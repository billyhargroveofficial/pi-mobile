#!/usr/bin/env python3
"""ADB-only UI helper. Does not require accessibility apps or agent plugins.
Usage: android-ui.py dump | tap <resource ID suffix or exact text> | type <ascii> | screenshot
"""
import subprocess,sys,xml.etree.ElementTree as ET,re,pathlib
ROOT=pathlib.Path(__file__).resolve().parent.parent
ADB=['adb','-s','emulator-5554']
def adb(*args):return subprocess.check_output(ADB+list(args))
def nodes():
 # Never interpret a stale hierarchy after UIAutomator times out during animation.
 for attempt in range(3):
  adb('shell','rm','-f','/sdcard/pi-mobile-ui.xml')
  adb('shell','uiautomator','dump','/sdcard/pi-mobile-ui.xml')
  try:
   return ET.fromstring(adb('exec-out','cat','/sdcard/pi-mobile-ui.xml'))
  except (subprocess.CalledProcessError, ET.ParseError):
   if attempt == 2: raise RuntimeError('No fresh UI hierarchy; inspect screenshot instead')
def tap(selector):
 matches=[n for n in nodes().iter('node') if n.get('resource-id','').endswith(':id/'+selector) or n.get('text')==selector or n.get('content-desc')==selector]
 if len(matches)!=1:raise RuntimeError(f'Expected 1 node for {selector!r}, got {len(matches)}')
 x1,y1,x2,y2=map(int,re.findall(r'\d+',matches[0].get('bounds','')))
 if x2<=x1 or y2<=y1:raise RuntimeError('Node not visible')
 adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
if __name__=='__main__':
 cmd=sys.argv[1]
 if cmd=='dump':
  for n in nodes().iter('node'):
   if n.get('password')=='true':continue
   if n.get('text') or n.get('resource-id'):print(n.get('resource-id'),n.get('text'),n.get('bounds'))
 elif cmd=='tap':tap(sys.argv[2])
 elif cmd=='type':adb('shell','input','text',sys.argv[2].replace(' ','%s'))
 elif cmd=='screenshot':
  p=ROOT/'artifacts/android.png';p.parent.mkdir(exist_ok=True);p.write_bytes(adb('exec-out','screencap','-p'));print(p)
 else:raise SystemExit('Unknown command')
