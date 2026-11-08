"""CT1 development gateway. Python 3.10+; FFmpeg/FFprobe only needed for preparation."""
import argparse
from contextlib import contextmanager
import http.server
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import urllib.parse
import urllib.request

ID=re.compile(r"[A-Za-z0-9_-]{1,64}\Z")
PROFILES={"mp4":(320,240,"mpeg4","video/mp4"),"3gp":(176,144,"h263","video/3gpp")}
MAX_FILE=200*1024*1024
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None
OPENER=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())

def key(value):
    if not isinstance(value,str) or not ID.fullmatch(value): raise ValueError("Invalid media ID")
    return value

def media_path(root,identity,profile):
    key(identity)
    if profile not in PROFILES: raise ValueError("Unsupported profile")
    root=Path(root).resolve();path=(root/identity/(profile+"."+profile)).resolve()
    if not path.is_relative_to(root): raise ValueError("Path escaped media root")
    return path

def catalog(root):
    p=Path(root)/"catalog.json"
    if not p.exists(): return []
    if p.stat().st_size>1024*1024: raise ValueError("Catalog exceeds limit")
    value=json.loads(p.read_text(encoding="utf-8"))
    if not isinstance(value,list) or len(value)>1000: raise ValueError("Invalid or oversized catalog")
    return value

def label(value,maximum):
    return "".join(" " if ord(c)<32 or ord(c)==127 else c for c in str(value or ""))[:maximum]

def wire(items,source):
    if source not in {"catalog","invidious"}: raise ValueError("Invalid source")
    output=bytearray(("CT1\t"+source+"\n").encode("ascii"))
    for item in items[:20]:
        seconds=int(item.get("seconds",0))
        if not 0<=seconds<=2147483647: raise ValueError("Duration outside client integer range")
        profiles=item.get("profiles","-")
        if profiles not in {"-","mp4","3gp","mp4,3gp","3gp,mp4"}: raise ValueError("Invalid profiles")
        fields=[key(item["id"]),label(item.get("title"),160),label(item.get("author"),80),str(seconds),profiles]
        row=("\t".join(urllib.parse.quote(v,safe="-._~,") for v in fields)+"\n").encode("ascii")
        if len(row)-1>4096: raise ValueError("Row exceeds client limit")
        if len(output)+len(row)>32768: break # Whole rows only; never overflow Feed.LIMIT.
        output.extend(row)
    return bytes(output)

def origin_url(origin):
    u=urllib.parse.urlsplit(origin)
    if u.scheme not in {"http","https"} or not u.hostname or u.username or u.password or u.query or u.fragment or u.path not in {"","/"}:
        raise ValueError("Configure a trusted Invidious base origin")
    return u

def limited_read(response,maximum,seconds):
    deadline=time.monotonic()+seconds;data=bytearray()
    while True:
        block=response.read(min(8192,maximum+1-len(data)))
        if not block: return bytes(data)
        data.extend(block)
        if len(data)>maximum: raise ValueError("Upstream response exceeds limit")
        if time.monotonic()>deadline: raise ValueError("Upstream response deadline exceeded")

def metadata(origin,route,query=None):
    origin_url(origin)
    url=origin.rstrip("/")+route+("?"+urllib.parse.urlencode(query) if query else "")
    req=urllib.request.Request(url,headers={"Accept":"application/json","Accept-Encoding":"identity","User-Agent":"ChimeraTube/2.0"})
    with OPENER.open(req,timeout=15) as r: data=limited_read(r,1024*1024,30)
    return json.loads(data.decode("utf-8"))

def prepared(root,identity):
    return ",".join(p for p in PROFILES if media_path(root,identity,p).is_file()) or "-"

def search(root,query,source,origin=""):
    if len(query)>200: raise ValueError("Query exceeds 200 characters")
    if source=="catalog":
        rows=[]
        for item in catalog(root):
            if query.casefold() in (str(item.get("title",""))+" "+str(item.get("author",""))).casefold():
                row=dict(item);row["profiles"]=prepared(root,row["id"]);rows.append(row)
        return wire(rows,"catalog")
    if source!="invidious" or not origin: raise ValueError("Invidious search is not configured")
    values=metadata(origin,"/api/v1/search",dict(q=query,type="video"))
    if not isinstance(values,list): raise ValueError("Invalid upstream search response")
    rows=[]
    for item in values[:100]:
        if not isinstance(item,dict) or item.get("type")!="video": continue
        identity=item.get("videoId","")
        if not isinstance(identity,str) or not re.fullmatch(r"[A-Za-z0-9_-]{11}",identity): continue
        rows.append(dict(id=identity,title=item.get("title"),author=item.get("author"),seconds=item.get("lengthSeconds",0),profiles=prepared(root,identity)))
    return wire(rows,"invidious")

def byte_range(header,size):
    if size<=0: raise ValueError("Empty media")
    if not header: return 0,size-1,False
    match=re.fullmatch(r"bytes=(\d*)-(\d*)",header)
    if not match or not any(match.groups()): raise ValueError("Invalid byte range")
    a,b=match.groups()
    if not a:
        if int(b)<=0: raise ValueError("Invalid suffix")
        start,end=max(0,size-int(b)),size-1
    else: start,end=int(a),min(int(b),size-1) if b else size-1
    if start>=size or end<start: raise ValueError("Unsatisfiable range")
    return start,end,True

class Gateway(http.server.ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self,address,root,origin=""):
        self.root,self.origin=Path(root).resolve(),origin
        if origin: origin_url(origin)
        self.slots=threading.BoundedSemaphore(12)
        super().__init__(address,Handler)
    def process_request(self,request,address):
        if not self.slots.acquire(False):
            try: request.sendall(b"HTTP/1.0 503 Busy\r\nContent-Length: 0\r\n\r\n")
            finally: self.shutdown_request(request)
            return
        try: super().process_request(request,address)
        except BaseException:
            self.slots.release();raise
    def process_request_thread(self,request,address):
        try: super().process_request_thread(request,address)
        finally: self.slots.release()

class Handler(http.server.BaseHTTPRequestHandler):
    server_version="ChimeraTube/2.0"
    def setup(self):
        super().setup();self.connection.settimeout(20)
    def log_message(self,*args): pass
    def end_headers(self):
        super().end_headers();self.responded=True
    def send_bytes(self,status,data,head):
        self.send_response(status)
        self.send_header("Content-Type","text/plain; charset=utf-8")
        self.send_header("Content-Length",str(len(data)))
        self.send_header("Cache-Control","no-store")
        self.send_header("X-Content-Type-Options","nosniff")
        self.end_headers()
        if not head: self.wfile.write(data)
    def do_HEAD(self): self.get(True)
    def do_GET(self): self.get(False)
    def get(self,head):
        self.responded=False
        try:
            if len(self.path)>2048: return self.send_bytes(414,b"Request too long",head)
            u=urllib.parse.urlsplit(self.path)
            if u.scheme or u.netloc: raise ValueError("Use origin-form request targets")
            if u.path=="/health": return self.send_bytes(200,b"ready\n",head)
            if u.path=="/v1/search":
                args=urllib.parse.parse_qs(u.query,keep_blank_values=True,max_num_fields=5)
                if set(args)-{"q","source"} or any(len(v)!=1 for v in args.values()): raise ValueError("Invalid search arguments")
                return self.send_bytes(200,search(self.server.root,args.get("q",[""])[0],args.get("source",["catalog"])[0],self.server.origin),head)
            match=re.fullmatch(r"/v1/media/([A-Za-z0-9_-]{1,64})/(mp4|3gp)",u.path)
            if not match or u.query: return self.send_bytes(404,b"Not found",head)
            identity,profile=match.groups();path=media_path(self.server.root,identity,profile)
            try: media=path.open("rb")
            except FileNotFoundError: return self.send_bytes(404,b"Profile not prepared",head)
            with media:
                size=os.fstat(media.fileno()).st_size
                # If-Range cannot be validated without validators: send the full representation.
                header=self.headers.get("Range") if not self.headers.get("If-Range") else None
                try: start,end,partial=byte_range(header,size)
                except ValueError:
                    self.send_response(416);self.send_header("Content-Range","bytes */"+str(size));self.send_header("Content-Length","0");self.end_headers();return
                self.send_response(206 if partial else 200)
                self.send_header("Content-Type",PROFILES[profile][3]);self.send_header("Accept-Ranges","bytes")
                self.send_header("Content-Length",str(end-start+1));self.send_header("Cache-Control","no-store")
                if partial: self.send_header("Content-Range","bytes %d-%d/%d"%(start,end,size))
                self.end_headers()
                if not head:
                    media.seek(start);remaining=end-start+1
                    while remaining:
                        block=media.read(min(65536,remaining))
                        if not block: break
                        self.wfile.write(block);remaining-=len(block)
        except (BrokenPipeError,ConnectionResetError,socket.timeout): self.close_connection=True
        except ValueError as e:
            if self.responded: self.close_connection=True
            else: self.send_bytes(400,str(e).encode(),head)
        except Exception:
            if self.responded: self.close_connection=True
            else: self.send_bytes(502,b"Gateway/upstream failed; no fallback fabricated",head)

def probe(path):
    r=subprocess.run(["ffprobe","-v","error","-protocol_whitelist","file,pipe","-show_streams","-show_format","-of","json",str(path)],capture_output=True,timeout=30,check=True)
    return json.loads(r.stdout)

@contextmanager
def writer(root):
    root=Path(root);root.mkdir(parents=True,exist_ok=True)
    lock=root/".writer.lock";fd=os.open(str(lock),os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
    try: yield
    finally: os.close(fd);lock.unlink()

def ingest(root,source,identity,title,author=""):
    root,source=Path(root).resolve(),Path(source).resolve();key(identity)
    if not source.is_file() or source.stat().st_size>MAX_FILE: raise ValueError("Input missing or over 200 MiB")
    info=probe(source);duration=float(info["format"].get("duration",0))
    if not 0<duration<=900 or not any(s.get("codec_type")=="video" for s in info["streams"]): raise ValueError("Requires finite video up to 900 seconds")
    with writer(root):
        if (root/identity).exists(): raise ValueError("ID already exists; overwrites are disabled")
        with tempfile.TemporaryDirectory(prefix=".prepare-",dir=root) as temp:
            for profile,(width,height,codec,mime) in PROFILES.items():
                output=Path(temp)/(profile+"."+profile)
                scale="scale=%d:%d:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=%d:%d:(ow-iw)/2:(oh-ih)/2"%(width,height,width,height)
                cmd=["ffmpeg","-nostdin","-threads","1","-filter_threads","1","-v","error","-y","-protocol_whitelist","file,pipe","-i",str(source),"-map","0:v:0","-map","0:a:0?","-vf",scale,"-r","15","-c:v",codec,"-b:v","240k" if profile=="mp4" else "96k","-bf","0","-pix_fmt","yuv420p","-c:a","aac","-profile:a","aac_low","-ar","22050","-ac","1","-b:a","32k","-t","900","-movflags","+faststart","-f",profile,str(output)]
                with tempfile.TemporaryFile() as errors:
                    r=subprocess.run(cmd,stdout=subprocess.DEVNULL,stderr=errors,timeout=600)
                    if r.returncode:
                        errors.seek(0);raise ValueError("FFmpeg failed: "+errors.read(2000).decode(errors="replace"))
                video=next(s for s in probe(output)["streams"] if s.get("codec_type")=="video")
                if (video["width"],video["height"],video["codec_name"])!=(width,height,codec): raise ValueError("Encoded profile mismatch")
            entries=catalog(root)
            if len(entries)>=1000: raise ValueError("Catalog full")
            item=dict(id=identity,title=label(title,160),author=label(author,80),seconds=int(duration),profiles="mp4,3gp")
            entries.append(item);raw=json.dumps(entries,ensure_ascii=False)
            if len(raw.encode())>1024*1024: raise ValueError("Catalog exceeds size limit")
            scratch=root/".catalog.tmp";scratch.write_text(raw,encoding="utf-8")
            os.rename(temp,root/identity)
            try: os.replace(scratch,root/"catalog.json")
            except BaseException:
                shutil.rmtree(root/identity);raise
    return item

def download_public(url,destination,trusted_host):
    u=urllib.parse.urlsplit(url);host=u.hostname or ""
    if u.scheme!="https" or u.username or u.password or u.port not in {None,443}: raise ValueError("Media requires HTTPS without credentials")
    if not (host==trusted_host or host=="googlevideo.com" or host.endswith(".googlevideo.com")): raise ValueError("Media host not allowlisted")
    if host!=trusted_host:
        addresses=socket.getaddrinfo(host,443,type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(a[4][0]).is_global for a in addresses): raise ValueError("Non-public media address")
    with OPENER.open(url,timeout=20) as response,Path(destination).open("wb") as output:
        total=0;deadline=time.monotonic()+120
        while True:
            block=response.read(65536)
            if not block: break
            total+=len(block)
            if total>MAX_FILE or time.monotonic()>deadline: raise ValueError("Download size/deadline exceeded")
            output.write(block)

def import_video(root,origin,identity):
    if not re.fullmatch(r"[A-Za-z0-9_-]{11}",identity): raise ValueError("Expected an 11-character video ID")
    item=metadata(origin,"/api/v1/videos/"+identity)
    if any(item.get(n) for n in ["liveNow","isUpcoming","paid","premium"]): raise ValueError("Live/upcoming/paid/premium sources unsupported")
    if not 0<int(item.get("lengthSeconds",0))<=900: raise ValueError("Unsupported duration")
    selected=next((s for s in item.get("formatStreams",[]) if str(s.get("itag"))=="18" and s.get("url")),None)
    if not selected: raise ValueError("No progressive itag 18 source; no synthetic URL or bypass attempted")
    with tempfile.TemporaryDirectory() as temp:
        path=Path(temp)/"source.mp4";download_public(selected["url"],path,origin_url(origin).hostname)
        return ingest(root,path,identity,item.get("title",identity),item.get("author",""))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--root",default="media")
    commands=p.add_subparsers(dest="command",required=True)
    serve=commands.add_parser("serve");serve.add_argument("--bind",default="127.0.0.1");serve.add_argument("--port",type=int,default=9090);serve.add_argument("--invidious",default=os.environ.get("INVIDIOUS_ORIGIN",""))
    add=commands.add_parser("ingest");add.add_argument("input");add.add_argument("--id",required=True);add.add_argument("--title",required=True);add.add_argument("--author",default="")
    remote=commands.add_parser("import-invidious");remote.add_argument("--origin",required=True);remote.add_argument("--id",required=True)
    a=p.parse_args()
    if a.command=="serve":
        server=Gateway((a.bind,a.port),a.root,a.invidious)
        try: server.serve_forever()
        except KeyboardInterrupt: pass
        finally: server.server_close()
    elif a.command=="ingest": print(json.dumps(ingest(a.root,a.input,a.id,a.title,a.author)))
    else: print(json.dumps(import_video(a.root,a.origin,a.id)))
if __name__=="__main__": main()
