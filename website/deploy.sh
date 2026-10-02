#!/usr/bin/env bash
set -euo pipefail
# Run on the web host after uploading the verified package to /tmp/pokereport-portal.tar.gz.
root=/opt/pokereport/frontend
test "$(sha256sum /tmp/pokereport-portal.tar.gz | cut -d ' ' -f1)" = "$1"
stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup=/opt/pokereport/backups/portal-$stamp
mkdir -p "$backup" "$root/releases/$stamp"
cp -a "$root/dist" "$backup/dist"
cp "$root/nginx.conf" "$backup/nginx.conf"
cp /opt/pokereport/nginx-edge/edge.conf "$backup/edge.conf"
tar -xzf /tmp/pokereport-portal.tar.gz -C "$root/releases/$stamp"
stage="$root/releases/$stamp"
mkdir -p "$stage/tienda"
if test -f "$root/dist/tienda/index.html"; then
    cp "$root/dist/tienda/index.html" "$stage/tienda/index.html"
else
    cp "$root/dist/index.html" "$stage/tienda/index.html"
fi
# Keep the checkout JS and its assets intact. Only add a portal return link to the HTML.
python3 - "$stage/tienda/index.html" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1]); s=p.read_text()
link='<a href="/" style="position:fixed;bottom:18px;left:18px;z-index:40;background:#111827;color:#f5c779;padding:10px 16px;border:1px solid #f5c77966;border-radius:6px;font:600 13px system-ui;text-decoration:none" aria-label="Volver al portal PokeReport">← Portal PokeReport</a>'
if 'aria-label="Volver al portal PokeReport"' not in s:
    p.write_text(s.replace('</body>',link+'</body>'))
PY
cp -a "$stage/portal/." "$root/dist/"
cp -a "$stage/tienda" "$root/dist/"
mkdir -p "$root/dist/descargas"
cp "$stage/PokeReport-Launcher-0.2.3.exe" "$root/dist/descargas/"
chmod -R a+rX "$root/dist"
cp "$stage/nginx.conf" "$root/nginx.conf"
if ! docker exec pokereport-frontend nginx -t; then
    cp "$backup/nginx.conf" "$root/nginx.conf"
    cp "$backup/dist/index.html" "$root/dist/index.html"
    echo "Configuration rejected; original root restored. Backup: $backup" >&2
    exit 1
fi
docker exec pokereport-frontend nginx -s reload
cp "$stage/edge.conf" /opt/pokereport/nginx-edge/edge.conf
if ! docker exec pokereport-edge nginx -t; then
    cp "$backup/edge.conf" /opt/pokereport/nginx-edge/edge.conf
    echo "Edge configuration rejected; previous edge retained." >&2
    exit 1
fi
docker exec pokereport-edge nginx -s reload
echo "Portal installed. Original store asset files retained. Backup: $backup"
