"""Generate your own one-second video/tone, then prepare both real client profiles."""
import argparse
from pathlib import Path
import subprocess
import tempfile
from gateway import ingest

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("--root",default="media");a=p.parse_args()
    with tempfile.TemporaryDirectory() as temp:
        source=Path(temp)/"demo.mp4"
        subprocess.run(["ffmpeg","-nostdin","-v","error","-y","-filter_threads","1","-f","lavfi","-i","testsrc=size=160x120:rate=15","-f","lavfi","-i","sine=frequency=440:sample_rate=22050","-t","1","-c:v","mpeg4","-threads","1","-pix_fmt","yuv420p","-c:a","aac",str(source)],check=True,timeout=30)
        print(ingest(a.root,source,"generated-demo","Generated test pattern","FFmpeg synthetic fixture"))
if __name__=="__main__":main()
