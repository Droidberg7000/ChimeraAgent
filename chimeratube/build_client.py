"""Real CLDC/MIDP compilation and preverification; missing tools fail, not fake artifacts."""
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
ROOT=Path(__file__).resolve().parent

def required(name):
    value=os.environ.get(name,"")
    if not value or not Path(value).is_file():raise SystemExit("Set "+name+" to a valid local tool/API file")
    return str(Path(value).resolve())

def main():
    java=shutil.which("java")
    if not java:raise SystemExit("Java runtime missing; no build performed")
    apis=list(dict.fromkeys(required(n) for n in ["CLDC_API","MIDP_API","MMAPI_API"]))
    compiler,preverify=required("ECJ_JAR"),required("PREVERIFY")
    build=ROOT/"build";dist=ROOT/"dist"
    if build.exists():shutil.rmtree(build)
    if dist.exists():shutil.rmtree(dist)
    classes=build/"classes";verified=build/"verified"
    classes.mkdir(parents=True);verified.mkdir()
    cp=os.pathsep.join(apis)
    subprocess.run([java,"-jar",compiler,"-source","1.3","-target","1.1","-encoding","UTF-8","-bootclasspath",cp,"-d",str(classes)]+[str(p) for p in sorted((ROOT/"client/src").rglob("*.java"))],check=True)
    subprocess.run([preverify,"-classpath",cp,"-d",str(verified),str(classes)],check=True)
    paths=list(verified.rglob("*.class"))
    if not paths:raise SystemExit("Preverify emitted no classes")
    attrs={"MIDlet-Name":"ChimeraTube","MIDlet-Version":"2.0.0","MIDlet-Vendor":"Chimera","MIDlet-1":"ChimeraTube,,com.chimeratube.ChimeraTubeMIDlet","MicroEdition-Configuration":"CLDC-1.1","MicroEdition-Profile":"MIDP-2.0","MIDlet-Permissions":"javax.microedition.io.Connector.http","MIDlet-Permissions-Opt":"javax.microedition.io.Connector.https"}
    def fold(k,v):
        b=(k+": "+v).encode("ascii");rows=[]
        while len(b)>70:rows.append(b[:70]);b=b" "+b[70:]
        return b"\r\n".join(rows+[b])+b"\r\n"
    dist.mkdir();jar=dist/"ChimeraTube.jar"
    with zipfile.ZipFile(jar,"w",zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF",b"Manifest-Version: 1.0\r\n"+b"".join(fold(k,v) for k,v in attrs.items())+b"\r\n")
        for p in paths:z.write(p,p.relative_to(verified).as_posix())
    attrs.update({"MIDlet-Jar-URL":jar.name,"MIDlet-Jar-Size":str(jar.stat().st_size)})
    (dist/"ChimeraTube.jad").write_text("".join(k+": "+v+"\n" for k,v in attrs.items()),encoding="ascii")
    print("Created preverified JAR/JAD:",jar)
if __name__=="__main__":main()
