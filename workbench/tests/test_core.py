import os
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from core import Store, Workspace, context, run_command

class CoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.ws = Workspace(self.base/'workspace',self.base/'backups')
    def tearDown(self):
        self.tmp.cleanup()
    def test_boundaries(self):
        for path in ['../secret','/tmp/secret','x/../../secret','']:
            with self.assertRaises(ValueError):
                self.ws.path(path)
    @unittest.skipUnless(os.name=='posix','Symlink permissions vary')
    def test_symlink(self):
        (self.ws.root/'link').symlink_to(self.base)
        with self.assertRaises(ValueError):
            self.ws.path('link/secret')
    def test_write_backup(self):
        self.ws.commit(self.ws.prepare('nested/a.txt','old\r\n'))
        result = self.ws.commit(self.ws.prepare('nested/a.txt','new'))
        self.assertEqual(self.ws.read('nested/a.txt'),'new')
        self.assertEqual(Path(result['backup']).read_bytes(),b'old\r\n')
    def test_stale_preview(self):
        proposal = self.ws.prepare('a.txt','new')
        (self.ws.root/'a.txt').write_text('external')
        with self.assertRaises(ValueError):
            self.ws.commit(proposal)
    def test_size_limit(self):
        with self.assertRaises(ValueError):
            self.ws.prepare('a.txt','x'*128001)
    def test_search(self):
        self.ws.commit(self.ws.prepare('a.txt','first\nHELLO'))
        self.assertEqual(self.ws.search('hello')['matches'][0]['line'],2)
    def test_store(self):
        store = Store(self.base/'db.sqlite3')
        sid = store.create('test')
        turn = [dict(role='user',content='hello')]
        store.append(sid,turn)
        store.audit(sid,'test',dict(ok=True))
        self.assertEqual(store.turns(sid),[turn])
        self.assertTrue(store.events(sid)[0]['payload']['ok'])
    def test_context(self):
        turns = [[dict(role='user',content='x'*500)],[dict(role='user',content='recent')]]
        kept,dropped = context(turns,100)
        self.assertEqual(kept,turns[1])
        self.assertEqual(dropped,1)
    def test_command(self):
        result = run_command([sys.executable,'-c','print(123)'],self.ws.root,threading.Event())
        self.assertEqual(result['output'].strip(),'123')
        self.assertEqual(result['exit_code'],0)
    def test_timeout(self):
        result = run_command([sys.executable,'-c','import time; time.sleep(10)'],self.ws.root,threading.Event(),timeout=0.1)
        self.assertEqual(result['stop_reason'],'timeout')
    def test_cancel_before_launch(self):
        stop = threading.Event()
        stop.set()
        self.assertIn('error',run_command([sys.executable,'-c','print(123)'],self.ws.root,stop))

if __name__=='__main__':
    unittest.main()
