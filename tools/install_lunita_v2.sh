#!/usr/bin/env bash
set -euo pipefail
volume=/var/lib/pterodactyl/volumes/1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd
container=1f23bff0-bc4c-4c00-bbcf-2fe81ff666dd
live="$volume/mods/lunaeternal-0.1.0.jar"
upload=/tmp/lunaeternal-lunita-v2.jar
expected_before="$1"
expected_after="$2"
backup="$volume/backups/lunita-v2/lunaeternal-before-${expected_before:0:12}.jar"
test "$(sha256sum "$live" | cut -d ' ' -f1)" = "$expected_before"
test "$(sha256sum "$upload" | cut -d ' ' -f1)" = "$expected_after"
mkdir -p "$volume/backups/lunita-v2"
test ! -e "$backup"
cp "$live" "$backup"
echo 'Verified uploaded package and preserved previous jar.'
/usr/local/sbin/pokereport-server-cmd 'save-all flush'
docker stop -t 60 "$container"
test "$(sha256sum "$live" | cut -d ' ' -f1)" = "$expected_before"
cp "$upload" "$live"
chown pterodactyl:pterodactyl "$live"
test "$(sha256sum "$live" | cut -d ' ' -f1)" = "$expected_after"
docker start "$container"
echo 'Started server with Lunita v2; verify fresh startup logs before publishing.'
