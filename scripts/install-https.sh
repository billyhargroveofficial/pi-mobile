#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CADDY="$(command -v caddy)"
"$CADDY" validate --config "$ROOT/server/Caddyfile"
mkdir -p "$HOME/Library/LaunchAgents" "$HOME/Library/Logs/PiMobile" "$HOME/.local/share/pi-mobile-caddy"
chmod 700 "$HOME/.local/share/pi-mobile-caddy"
PLIST="$HOME/Library/LaunchAgents/ru.billyhargrove.pi-mobile-https.plist"
python3 - "$ROOT" "$CADDY" "$PLIST" <<'PY'
import sys,plistlib,os
root,caddy,out=sys.argv[1:]
c={'Label':'ru.billyhargrove.pi-mobile-https','ProgramArguments':[caddy,'run','--config',root+'/server/Caddyfile'],'RunAtLoad':True,'KeepAlive':True,'ThrottleInterval':15,'EnvironmentVariables':{'HOME':os.path.expanduser('~'),'XDG_DATA_HOME':os.path.expanduser('~/.local/share/pi-mobile-caddy')},'StandardOutPath':os.path.expanduser('~/Library/Logs/PiMobile/https.log'),'StandardErrorPath':os.path.expanduser('~/Library/Logs/PiMobile/https-error.log')}
with open(out,'wb') as f:plistlib.dump(c,f)
PY
launchctl bootout "gui/$(id -u)/ru.billyhargrove.pi-mobile-https" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo 'HTTPS service installed; confirm certificate and external 401 before use.'
