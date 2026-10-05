#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NODE="$(command -v node)"
mkdir -p "$HOME/Library/LaunchAgents" "$HOME/Library/Logs/PiMobile"
PLIST="$HOME/Library/LaunchAgents/ru.billyhargrove.pi-mobile.plist"
python3 - "$ROOT" "$NODE" "$PLIST" <<'PY'
import sys,plistlib,os
root,node,out=sys.argv[1:]
config={'Label':'ru.billyhargrove.pi-mobile','ProgramArguments':[node,root+'/server/index.mjs'],'WorkingDirectory':root,'RunAtLoad':True,'KeepAlive':True,'ThrottleInterval':10,'EnvironmentVariables':{'PATH':'/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin'},'StandardOutPath':os.path.expanduser('~/Library/Logs/PiMobile/gateway.log'),'StandardErrorPath':os.path.expanduser('~/Library/Logs/PiMobile/gateway-error.log')}
with open(out,'wb') as f:plistlib.dump(config,f)
PY
launchctl bootout "gui/$(id -u)/ru.billyhargrove.pi-mobile" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo 'Gateway installed (loopback only). Device token stays in ~/.pi/agent/pi-mobile/device-token.'
