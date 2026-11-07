import json
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from fastapi.testclient import TestClient
from core import Store
from server import build_app, Run

class ApiTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.client = TestClient(build_app(self.base/'workspace',self.base/'data'),base_url='http://127.0.0.1:8765')
        token = self.client.get('/').text.split("const TOKEN='")[1].split("'")[0]
        self.headers = {'X-Chimera-Token':token}
    def tearDown(self):
        self.client.close()
        self.tmp.cleanup()
    def test_guards(self):
        self.assertEqual(self.client.get('/api/sessions').status_code,403)
        self.assertEqual(self.client.get('/',headers={'host':'evil.test'}).status_code,403)
        self.assertEqual(self.client.get('/api/sessions',headers={**self.headers,'origin':'https://evil.test'}).status_code,403)
    def test_session(self):
        sid = self.client.post('/api/sessions',headers=self.headers,json={'title':'test'}).json()['id']
        self.assertEqual(self.client.get('/api/sessions/'+sid,headers=self.headers).json()['turns'],[])
        self.assertEqual(self.client.get('/api/file?path=../secret',headers=self.headers).status_code,400)
        self.assertEqual(self.client.post('/api/sessions/'+sid+'/commands',headers=self.headers,json={'argv':['echo','hi']}).status_code,403)
        self.assertEqual(self.client.post('/api/sessions/'+sid+'/runs',headers=self.headers,json={'text':'hi','model':'test:cloud'}).status_code,400)
    def test_stream_and_persistence(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def raise_for_status(self): pass
            def iter_lines(self):
                yield json.dumps(dict(message=dict(content='Hello '),done=False))
                yield json.dumps(dict(message=dict(content='world'),done=True))
        class FakeClient:
            def __init__(self,*args,**kwargs): pass
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def stream(self,*args,**kwargs): return Response()
        sid = self.client.post('/api/sessions',headers=self.headers,json={'title':'test'}).json()['id']
        with patch('server.httpx.Client',FakeClient):
            result = self.client.post('/api/sessions/'+sid+'/runs',headers=self.headers,json={'text':'hi','model':'qwen3:4b','mode':'chat'})
            self.assertEqual(result.status_code,200)
            for _ in range(100):
                data = self.client.get('/api/sessions/'+sid,headers=self.headers).json()
                if not data['active_run']: break
                time.sleep(0.02)
            self.assertEqual(data['turns'][0][-1]['content'],'Hello world')
            rid = result.json()['id']
            events = self.client.get('/api/runs/'+rid+'/events',headers=self.headers).text.splitlines()
            self.assertEqual(json.loads(events[-1])['type'],'done')
    def test_gate_denial(self):
        run = Run('r','s')
        store = Store(self.base/'gate.sqlite3')
        result = []
        thread = threading.Thread(target=lambda:result.append(run.approve('write_file',dict(path='a',content='exact'),store)))
        thread.start()
        for _ in range(100):
            with run.condition:
                if run.pending:
                    run.decision = False
                    run.condition.notify_all()
                    break
            time.sleep(0.01)
        thread.join(2)
        self.assertFalse(thread.is_alive())
        self.assertEqual(result,[False])

if __name__=='__main__':
    unittest.main()
