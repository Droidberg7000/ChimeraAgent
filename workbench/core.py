"""Local state, bounded workspace tools, and optional subprocess execution."""
from contextlib import contextmanager
import difflib
import hashlib
import json
import os
from pathlib import Path
import signal
import sqlite3
import subprocess
import tempfile
import threading
import time
import uuid

MAX_FILE = 128000
MAX_OUTPUT = 64000

class Store:
    def __init__(self, path):
        self.path = str(path)
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript('PRAGMA journal_mode=WAL; CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY,title TEXT,created REAL); CREATE TABLE IF NOT EXISTS turns(id INTEGER PRIMARY KEY,session TEXT,messages TEXT); CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY,session TEXT,event TEXT,payload TEXT,created REAL);')

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        try:
            with db:
                yield db
        finally:
            db.close()

    def create(self, title):
        sid = uuid.uuid4().hex
        with self.connect() as db:
            db.execute('INSERT INTO sessions VALUES (?,?,?)', (sid,title[:100],time.time()))
        return sid

    def sessions(self):
        with self.connect() as db:
            return [dict(id=r[0],title=r[1],created=r[2]) for r in db.execute('SELECT id,title,created FROM sessions ORDER BY created DESC LIMIT 500')]

    def exists(self, sid):
        with self.connect() as db:
            return db.execute('SELECT 1 FROM sessions WHERE id=?',(sid,)).fetchone() is not None

    def turns(self, sid):
        with self.connect() as db:
            return [json.loads(r[0]) for r in db.execute('SELECT messages FROM turns WHERE session=? ORDER BY id',(sid,))]

    def append(self, sid, messages):
        with self.connect() as db:
            db.execute('INSERT INTO turns(session,messages) VALUES (?,?)',(sid,json.dumps(messages,ensure_ascii=False)))

    def audit(self, sid, event, payload):
        with self.connect() as db:
            db.execute('INSERT INTO audit(session,event,payload,created) VALUES (?,?,?,?)',(sid,event,json.dumps(payload,ensure_ascii=False),time.time()))

    def events(self, sid):
        with self.connect() as db:
            return [dict(id=r[0],event=r[1],payload=json.loads(r[2]),created=r[3]) for r in db.execute('SELECT id,event,payload,created FROM audit WHERE session=? ORDER BY id DESC LIMIT 500',(sid,))]

def context(turns, budget=24000):
    kept, size = [], 0
    for turn in reversed(turns):
        length = len(json.dumps(turn,ensure_ascii=False))
        if size + length > budget:
            break
        kept.insert(0,turn)
        size += length
    return [m for turn in kept for m in turn], len(turns)-len(kept)

class Workspace:
    def __init__(self, root, backups):
        self.root, self.backups = Path(root).resolve(), Path(backups).resolve()
        if self.backups.is_relative_to(self.root):
            raise ValueError('Backups must be outside workspace')
        self.root.mkdir(parents=True,exist_ok=True)
        self.backups.mkdir(parents=True,exist_ok=True)
        self.lock = threading.Lock()

    def path(self, name):
        if not isinstance(name,str) or not name or '\x00' in name:
            raise ValueError('Invalid path')
        rel = Path(name)
        if rel.is_absolute() or '..' in rel.parts:
            raise ValueError('Absolute paths and traversal forbidden')
        p = self.root
        for part in rel.parts:
            p = p / part
            if p.is_symlink():
                raise ValueError('Symlinks forbidden')
        p = p.resolve()
        if not p.is_relative_to(self.root):
            raise ValueError('Outside workspace')
        return p

    def read(self, name):
        p = self.path(name)
        if not p.is_file():
            raise ValueError('Not a regular file')
        with p.open('rb') as f:
            data = f.read(MAX_FILE+1)
        if len(data)>MAX_FILE:
            raise ValueError('File exceeds 128 KB')
        return data.decode('utf-8')

    def listing(self, name='.'):
        return [dict(name=p.name,directory=p.is_dir(),symlink=p.is_symlink()) for p in sorted(self.path(name).iterdir(),key=lambda x:x.name)[:500]]

    def search(self, query):
        if not isinstance(query,str) or not 1<=len(query)<=200:
            raise ValueError('Query must contain 1-200 characters')
        matches, scanned = [], 0
        for base, dirs, names in os.walk(self.root,followlinks=False):
            dirs[:] = sorted(d for d in dirs if d not in {'.git','.venv','node_modules'} and not (Path(base)/d).is_symlink())
            for name in sorted(names):
                scanned += 1
                if scanned>2000 or len(matches)>=100:
                    return dict(matches=matches,limited=True)
                relative = str((Path(base)/name).relative_to(self.root))
                try:
                    for number,line in enumerate(self.read(relative).splitlines(),1):
                        if query.casefold() in line.casefold():
                            matches.append(dict(path=relative,line=number,text=line[:400]))
                            if len(matches)>=100:
                                break
                except (OSError,ValueError,UnicodeError):
                    continue
        return dict(matches=matches,limited=False)

    def prepare(self, name, content):
        if not isinstance(content,str) or len(content.encode())>MAX_FILE:
            raise ValueError('Write limit is 128 KB UTF-8')
        p = self.path(name)
        old = self.read(name) if p.exists() else None
        return dict(path=name,resolved_path=str(p),content=content,previous_sha256=hashlib.sha256(old.encode()).hexdigest() if old is not None else None,diff=''.join(difflib.unified_diff((old or '').splitlines(True),content.splitlines(True),fromfile=name,tofile=name)))

    def commit(self, proposal):
        with self.lock:
            p = self.path(proposal['path'])
            old = self.read(proposal['path']) if p.exists() else None
            digest = hashlib.sha256(old.encode()).hexdigest() if old is not None else None
            if digest!=proposal['previous_sha256'] or str(p)!=proposal['resolved_path']:
                raise ValueError('File changed after preview; fresh approval required')
            backup = None
            if old is not None:
                backup = self.backups/(uuid.uuid4().hex+'.txt')
                backup.write_text(old,encoding='utf-8',newline='')
            p.parent.mkdir(parents=True,exist_ok=True)
            fd,temp = tempfile.mkstemp(prefix='.chimera-',dir=p.parent)
            try:
                with os.fdopen(fd,'w',encoding='utf-8',newline='') as f:
                    f.write(proposal['content'])
                    f.flush()
                    os.fsync(f.fileno())
                os.replace(temp,p)
            finally:
                if os.path.exists(temp):
                    os.unlink(temp)
            return dict(written=proposal['path'],backup=str(backup) if backup else None)

def run_command(argv, cwd, cancel, timeout=60):
    if not isinstance(argv,list) or not 1<=len(argv)<=100 or any(not isinstance(a,str) or not a or chr(0) in a or len(a)>8000 for a in argv):
        raise ValueError('Invalid argv')
    if cancel.is_set():
        return dict(error='Cancelled before launch')
    with tempfile.TemporaryFile() as output:
        p = subprocess.Popen(argv,cwd=cwd,stdin=subprocess.DEVNULL,stdout=output,stderr=subprocess.STDOUT,shell=False,start_new_session=(os.name=='posix'))
        reason, start = None, time.monotonic()
        try:
            while p.poll() is None:
                if cancel.is_set():
                    reason = 'cancelled'
                elif time.monotonic()-start>timeout:
                    reason = 'timeout'
                elif os.fstat(output.fileno()).st_size>MAX_OUTPUT:
                    reason = 'output_limit'
                if reason:
                    break
                time.sleep(0.05)
        finally:
            if os.name=='posix':
                try:
                    os.killpg(p.pid,signal.SIGKILL)
                except ProcessLookupError:
                    pass
            elif p.poll() is None:
                p.kill()
            p.wait()
        output.seek(0)
        data = output.read(MAX_OUTPUT+1)
        return dict(exit_code=p.returncode,stop_reason=reason,output=data[:MAX_OUTPUT].decode('utf-8',errors='replace'),truncated=len(data)>MAX_OUTPUT)
