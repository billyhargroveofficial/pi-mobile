#!/usr/bin/env bash
# Install only the loopback relay. Does not change Tailscale, start Pi or reload agents.
set -euo pipefail
[[ "$(uname -s)" == Linux ]] || { echo 'This installer requires Linux.' >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NODE="$(command -v node)"
command -v pi >/dev/null || { echo 'Add the installed Pi binary directory to PATH first.' >&2; exit 1; }
ORCA="${ORCA_CLI_COMMAND:-$(command -v orca-ide || true)}"
[[ -n "$ORCA" && -x "$ORCA" ]] || { echo 'Set ORCA_CLI_COMMAND to the Orca IDE CLI (not the GNOME screen reader).' >&2; exit 1; }
command -v python3 >/dev/null
systemctl --user show-environment >/dev/null
UNIT="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user/pi-mobile.service"
RUNTIME="${PI_MOBILE_ORCA_RUNTIME_FILE:-${XDG_CONFIG_HOME:-$HOME/.config}/orca/orca-runtime.json}"
mkdir -p "$(dirname "$UNIT")"
python3 - "$ROOT" "$NODE" "$ORCA" "$UNIT" "$PATH" "$RUNTIME" <<'PY'
import sys, pathlib, json, shutil, datetime
root,node,orca,unit,env_path,runtime=sys.argv[1:]
def quote(value):
    if any(c in value for c in '\n\r\x00'): raise SystemExit('Unsupported control character in service path')
    return json.dumps(value.replace('%','%%'))
p=pathlib.Path(unit)
if p.exists():
    backup=p.with_name(p.name+'.backup-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    shutil.copy2(p,backup)
    print('Previous unit backed up:',backup)
p.write_text('\n'.join([
    '[Unit]','Description=Pi Mobile authenticated relay for Orca','After=network-online.target','',
    '[Service]','Type=simple','WorkingDirectory='+quote(root),
    'ExecStart='+quote(node)+' '+quote(root+'/server/index.mjs'),
    'Environment='+quote('PATH='+env_path),
    'Environment='+quote('ORCA_CLI_COMMAND='+orca),
    'Environment='+quote('PI_MOBILE_ORCA_RUNTIME_FILE='+runtime),
    'UMask=0077','Restart=on-failure','RestartSec=3','',
    '[Install]','WantedBy=default.target',''
]))
PY
systemctl --user daemon-reload
systemctl --user enable pi-mobile.service
systemctl --user restart pi-mobile.service
systemctl --user is-active pi-mobile.service
printf '%s\n' 'Relay installed on loopback :8788. Configure HTTPS separately; see docs/setup.md.'
