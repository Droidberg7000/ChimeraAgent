"""Real RAPC build driver. No SDK, BIOS, ROM or fake COD is distributed."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile
import xml.etree.ElementTree as ET

def package(folder):
    jad,=folder.glob('*.jad');jar,=folder.glob('*.jar')
    for path in list(folder.glob('*.cod')):
        if zipfile.is_zipfile(path):
            with zipfile.ZipFile(path) as z:
                entries=z.infolist()
                if len(entries)>256 or sum(e.file_size for e in entries)>33554432: raise ValueError('COD archive limits')
                if any(not re.fullmatch(r'[\w-]+\.cod',e.filename) for e in entries): raise ValueError('Unsafe sibling path')
                children=[(e.filename,z.read(e)) for e in entries]
            path.unlink()
            for name,data in children: (folder/name).write_bytes(data)
    cods=sorted(folder.glob('*.cod'))
    if not cods or any(p.stat().st_size==0 for p in cods): raise ValueError('No native COD output')
    lines=[s for s in jad.read_text().splitlines() if not s.startswith(('MIDlet-Jar-Size:','RIM-COD-URL','RIM-COD-Size','RIM-COD-SHA1'))]
    lines.append('MIDlet-Jar-Size: '+str(jar.stat().st_size))
    for i,p in enumerate(cods):
        suffix='-'+str(i) if i else ''
        lines+=['RIM-COD-URL'+suffix+': '+p.name,'RIM-COD-Size'+suffix+': '+str(p.stat().st_size)]
    jad.write_text('\r\n'.join(lines)+'\r\n',encoding='utf-8')
    loader=ET.Element('loader',version='1.0');app=ET.SubElement(loader,'application',id=jad.stem)
    for k,v in [('name',jad.stem),('description','JavaBoy source build; see build mode'),('version','0.1.0'),('vendor','JavaBoyChimera')]: ET.SubElement(app,k).text=v
    group=ET.SubElement(app,'fileset');ET.SubElement(group,'directory').text='.';ET.SubElement(group,'files').text='\n'+'\n'.join(p.name for p in cods)+'\n'
    ET.ElementTree(loader).write(folder/(jad.stem+'.alx'),encoding='utf-8',xml_declaration=True)
    (folder/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in sorted(folder.iterdir()) if p.is_file() and p.name!='SHA256SUMS'))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--target',choices=['bold9900','pearl9105','pearl8100'],default='bold9900');p.add_argument('--factory');p.add_argument('--backend',type=Path);p.add_argument('--package-only',type=Path);a=p.parse_args()
    if a.package_only: package(a.package_only.resolve());return
    home=Path(__file__).resolve().parent;factory=a.factory or 'com.javaboy.chimera.Diagnostic';mode='emulator' if a.factory else 'diagnostic'
    if not re.fullmatch(r'[A-Za-z_]\w*(\.[A-Za-z_]\w*)+',factory): p.error('Invalid factory class')
    if a.factory and (not a.backend or not (a.backend/(factory.replace('.','/')+'.java')).is_file()): p.error('Missing actual backend source')
    tools=[Path(os.environ.get(n,'__MISSING__')).resolve() for n in ['RAPC','BB_API']]
    if not all(t.is_file() for t in tools): p.error('Set RAPC to genuine SDK launcher and BB_API to matching net_rim_api.jar')
    folder=home/'build'/(a.target+'-'+mode);folder.mkdir(parents=True,exist_ok=False)
    name='JavaBoy_'+a.target+'_'+mode;entry={'bold9900':'JavaBoyBold9900MIDlet','pearl9105':'JavaBoyPearl9105MIDlet','pearl8100':'JavaBoyPearl8100MIDlet'}[a.target]
    jad=folder/(name+'.jad')
    jad.write_text('\r\n'.join(['MIDlet-Name: '+name,'MIDlet-Version: 0.1.0','MIDlet-Vendor: JavaBoyChimera','MIDlet-1: '+name+',,com.javaboy.chimera.'+entry,'MIDlet-Jar-URL: '+name+'.jar','MicroEdition-Profile: MIDP-2.0','MicroEdition-Configuration: CLDC-1.1','JavaBoy-Core-Factory: '+factory,'JavaBoy-Mode: '+mode])+'\r\n',encoding='ascii')
    sources=list((home/'java').rglob('*.java'))
    if a.backend: sources+=list(a.backend.resolve().rglob('*.java'))
    subprocess.run([str(tools[0]),'import='+str(tools[1]),'codename='+name,'-midlet','jad='+jad.name]+[str(s) for s in sources],cwd=folder,check=True)
    package(folder);print(folder)
if __name__=='__main__': main()
