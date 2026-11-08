import io
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import patch
import gateway as g

class GatewayTest(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
 def tearDown(self): self.temp.cleanup()
 def test_ids_and_profiles(self):
  for value in ['../secret','/etc/passwd','','a/b','x'*65]:
   with self.assertRaises(ValueError):g.media_path(self.root,value,'mp4')
  with self.assertRaises(ValueError):g.media_path(self.root,'demo','avi')
 def test_ct1_utf8_and_exact_id(self):
  data=g.wire([dict(id='dQw4w9WgXcQ',title='café & 猫',author='A\tB\nC',seconds=42,profiles='mp4,3gp')],'catalog')
  values=[g.urllib.parse.unquote(v) for v in data.decode().splitlines()[1].split('\t')]
  self.assertEqual(values,['dQw4w9WgXcQ','café & 猫','A B C','42','mp4,3gp'])
 def test_feed_budget_truncates_whole_rows(self):
  rows=[dict(id='id'+str(i),title='猫'*160,author='猫'*80) for i in range(20)]
  data=g.wire(rows,'catalog');lines=data.decode().splitlines()
  self.assertLessEqual(len(data),32768);self.assertLess(len(lines),21)
  self.assertTrue(all(len(line.split('\t'))==5 for line in lines[1:]))
  self.assertTrue(all(len(line)<=4096 for line in lines))
 def test_duration_and_profile_validation(self):
  with self.assertRaises(ValueError):g.wire([dict(id='x',seconds=2147483648)],'catalog')
  with self.assertRaises(ValueError):g.wire([dict(id='x',profiles='rtsp')],'catalog')
 def test_json_upstream_not_substring_parsing(self):
  values=json.loads(json.dumps([dict(type='video',videoId='dQw4w9WgXcQ',title='Escaped " quote',lengthSeconds=42)]))
  with patch.object(g,'metadata',return_value=values):data=g.search(self.root,'x','invidious','https://configured.invalid')
  self.assertIn(b'dQw4w9WgXcQ',data);self.assertIn(b'%22',data)
  self.assertTrue(data.splitlines()[1].endswith(b'\t-'))
 def test_empty_and_unconfigured_sources(self):
  self.assertEqual(g.search(self.root,'','catalog'),b'CT1\tcatalog\n')
  with self.assertRaises(ValueError):g.search(self.root,'x','invidious')
 def test_missing_profiles_not_advertised(self):
  (self.root/'catalog.json').write_text(json.dumps([dict(id='demo',title='Demo',profiles='mp4,3gp')]))
  self.assertTrue(g.search(self.root,'','catalog').splitlines()[1].endswith(b'\t-'))
 def test_ranges(self):
  self.assertEqual(g.byte_range('bytes=2-4',10),(2,4,True))
  self.assertEqual(g.byte_range('bytes=-3',10),(7,9,True))
  self.assertEqual(g.byte_range('bytes=8-',10),(8,9,True))
  for value in ['bytes=11-','bytes=4-2','bytes=-0','bytes=0-1,4-5']:
   with self.assertRaises(ValueError):g.byte_range(value,10)
 def test_download_rejections_before_network(self):
  for url in ['http://evil.invalid/a','https://evil.invalid/a','https://user:pass@googlevideo.com/a']:
   with self.assertRaises(ValueError):g.download_public(url,self.root/'x','trusted.invalid')
 def test_remote_address_guard(self):
  with patch.object(g.socket,'getaddrinfo',return_value=[(2,1,6,'',('127.0.0.1',443))]):
   with self.assertRaises(ValueError):g.download_public('https://a.googlevideo.com/x',self.root/'x','trusted.invalid')
 def test_missing_source_has_no_fabricated_fallback(self):
  with patch.object(g,'metadata',return_value=dict(lengthSeconds=5,formatStreams=[])):
   with self.assertRaisesRegex(ValueError,'No progressive'):g.import_video(self.root,'https://configured.invalid','dQw4w9WgXcQ')
 def test_writer_exclusion_and_metadata_limits(self):
  with g.writer(self.root):
   with self.assertRaises(FileExistsError):
    with g.writer(self.root):pass
  with self.assertRaises(ValueError):g.limited_read(io.BytesIO(b'12345'),4,30)
 def test_real_http(self):
  path=g.media_path(self.root,'fixture','mp4');path.parent.mkdir();path.write_bytes(b'0123456789')
  server=g.Gateway(('127.0.0.1',0),self.root);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
  base='http://127.0.0.1:'+str(server.server_port)
  try:
   with urllib.request.urlopen(base+'/v1/search?q=') as r:self.assertEqual(r.read(),b'CT1\tcatalog\n')
   with urllib.request.urlopen(urllib.request.Request(base+'/v1/media/fixture/mp4',headers={'Range':'bytes=2-4'})) as r:
    self.assertEqual(r.status,206);self.assertEqual(r.read(),b'234');self.assertEqual(r.headers['Content-Range'],'bytes 2-4/10')
   with urllib.request.urlopen(urllib.request.Request(base+'/v1/media/fixture/mp4',method='HEAD')) as r:
    self.assertEqual(r.read(),b'');self.assertEqual(r.headers['Content-Length'],'10')
   for path,expected in [('/v1/media/fixture/3gp',404),('/v1/search?q=a&q=b',400)]:
    with self.assertRaises(urllib.error.HTTPError) as e:urllib.request.urlopen(base+path)
    self.assertEqual(e.exception.code,expected)
   with self.assertRaises(urllib.error.HTTPError) as e:
    urllib.request.urlopen(urllib.request.Request(base+'/v1/media/fixture/mp4',headers={'Range':'bytes=99-'}))
   self.assertEqual(e.exception.code,416);self.assertEqual(e.exception.headers['Content-Range'],'bytes */10')
  finally:server.shutdown();server.server_close();thread.join(3)
 @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'),'FFmpeg/FFprobe missing')
 def test_real_ffmpeg_preparation(self):
  source=self.root/'source.mp4'
  subprocess.run(['ffmpeg','-nostdin','-v','error','-y','-filter_threads','1','-f','lavfi','-i','testsrc=size=160x120:rate=15','-f','lavfi','-i','sine=frequency=440:sample_rate=22050','-t','1','-c:v','mpeg4','-threads','1','-pix_fmt','yuv420p','-c:a','aac',str(source)],check=True,timeout=30)
  media=self.root/'media';g.ingest(media,source,'demo','Generated demo')
  for profile,(w,h,codec,mime) in g.PROFILES.items():
   path=g.media_path(media,'demo',profile);info=g.probe(path);video=next(s for s in info['streams'] if s['codec_type']=='video')
   self.assertEqual((video['width'],video['height'],video['codec_name']),(w,h,codec))
   self.assertTrue(any(s.get('codec_name')=='aac' for s in info['streams']))
   data=path.read_bytes();self.assertLess(data.index(b'moov'),data.index(b'mdat'))
  self.assertIn(b'mp4,3gp',g.search(media,'Generated','catalog'))
  with self.assertRaisesRegex(ValueError,'already exists'):g.ingest(media,source,'demo','Again')
if __name__=='__main__':unittest.main()
