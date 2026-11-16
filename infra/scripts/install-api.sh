#!/bin/bash
set -euo pipefail
: "${RB1_ASSET_URI:?}" "${RB1_TABLE:?}" "${RB1_BUCKET:?}" "${RB1_DOMAIN:?}" "${AWS_REGION:?}"
[[ "$RB1_DOMAIN" =~ ^[a-z0-9]+([.-][a-z0-9]+)*\.[a-z]{2,}$ ]]
test "$(id -u)" = 0
test "$(node -p 'process.versions.node.split(".")[0]')" -ge 24
test -x /usr/bin/node
exec 9>/run/lock/shared-caddy-deploy.lock
flock -w 300 9
grep -Fqx 'import /etc/caddy/conf.d/*.caddy' /etc/caddy/Caddyfile
systemctl is-active --quiet caddy
if test -n "$(ss -H -ltn '( sport = :3081 )')" && ! systemctl is-active --quiet rb1-dashcam; then
  echo 'Loopback port 3081 is occupied by another service; refusing deployment.' >&2
  exit 1
fi

# Fail closed if any of the four existing sites is unhealthy before touching RB1.
check_sites() {
  for site in piggyledger.net midlifemotor.club kaedamacycles.com thecrankygoose.net; do
    test "$(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' "https://${site}/")" = 200
    curl -sSI --max-time 20 "https://www.${site}/" | tr -d '\r' | grep -Fqi "location: https://${site}/"
  done
  test "$(curl -sS --max-time 20 -o /dev/null -w '%{http_code}' https://kaedamacycles.com/api/quotes)" = 405
  curl -fsS --max-time 20 https://piggyledger.net/api/health >/dev/null
}
check_sites
work=$(mktemp -d /tmp/rb1-deploy.XXXXXXXX)
install -d -m 0755 /opt/rb1/releases /etc/rb1 /etc/caddy/conf.d
stage=$(mktemp -d /opt/rb1/releases/release.XXXXXXXX)
route=/etc/caddy/conf.d/rb1-dashcam.caddy
unit=/etc/systemd/system/rb1-dashcam.service
previous=$(readlink /opt/rb1/current || true)
for file in "$route" "$unit" /etc/rb1/api.env; do
  if test -f "$file"; then cp "$file" "$work/$(basename "$file")"; fi
done
# Verify every pre-existing site snippet and the root Caddy file remain byte-for-byte intact.
find /etc/caddy -maxdepth 2 -type f ! -name rb1-dashcam.caddy -print0 | sort -z | xargs -0 sha256sum > "$work/shared.sha256"
find /etc/systemd/system -maxdepth 1 -type f ! -name rb1-dashcam.service -print0 | sort -z | xargs -0 sha256sum >> "$work/shared.sha256"
switched=false
rollback() {
  status=$?
  if test "$status" -ne 0 && test "$switched" = true; then
    for file in "$route" "$unit" /etc/rb1/api.env; do
      if test -f "$work/$(basename "$file")"; then cp "$work/$(basename "$file")" "$file";
      else rm -f "$file"; fi
    done
    if test -n "$previous"; then
      ln -s "$previous" "$work/previous-link"; mv -Tf "$work/previous-link" /opt/rb1/current
      systemctl daemon-reload; systemctl restart rb1-dashcam || true
    else systemctl stop rb1-dashcam || true; systemctl disable rb1-dashcam || true; rm -f /opt/rb1/current; systemctl daemon-reload; fi
    caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile && systemctl reload caddy || true
  fi
  rm -rf "$work"
  exit "$status"
}
trap rollback EXIT
aws s3 cp "$RB1_ASSET_URI" "$work/api.zip"
unzip -oq "$work/api.zip" -d "$stage"
test -s "$stage/main.cjs"
(cd "$stage" && npm ci --omit=dev --ignore-scripts)
id rb1-api >/dev/null 2>&1 || useradd --system --no-create-home --shell /sbin/nologin rb1-api
chmod -R a+rX "$stage"
cat > "$work/api.env" <<EOF
RB1_TABLE=${RB1_TABLE}
RB1_BUCKET=${RB1_BUCKET}
AWS_REGION=${AWS_REGION}
EOF
cat > "$work/rb1-dashcam.service" <<'EOF'
[Unit]
Description=RB1 dashcam upload authorization API
After=network-online.target
[Service]
User=rb1-api
Group=rb1-api
WorkingDirectory=/opt/rb1/current
EnvironmentFile=/etc/rb1/api.env
ExecStart=/usr/bin/node main.cjs
Restart=on-failure
RestartSec=5
MemoryMax=192M
CPUQuota=25%
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictSUIDSGID=true
UMask=0077
[Install]
WantedBy=multi-user.target
EOF
cat > "$work/rb1-dashcam.caddy" <<EOF
${RB1_DOMAIN} {
    request_body {
        max_size 32KB
    }
    reverse_proxy 127.0.0.1:3081
    header Strict-Transport-Security "max-age=31536000"
    header X-Content-Type-Options "nosniff"
}
EOF
switched=true
install -m 0600 "$work/api.env" /etc/rb1/api.env
install -m 0644 "$work/rb1-dashcam.service" "$unit"
ln -s "$stage" "$work/next-link"; mv -Tf "$work/next-link" /opt/rb1/current
systemctl daemon-reload
systemctl enable --now rb1-dashcam
systemctl restart rb1-dashcam
for attempt in {1..15}; do
  if curl -fsS --max-time 2 http://127.0.0.1:3081/health >/dev/null; then break; fi
  sleep 1
done
systemctl is-active --quiet rb1-dashcam
curl -fsS --max-time 2 http://127.0.0.1:3081/health | node -e 'let b=""; process.stdin.on("data",c=>b+=c); process.stdin.on("end",()=>{if(JSON.parse(b).service!=="rb1-dashcam")process.exit(1)})'
install -m 0644 "$work/rb1-dashcam.caddy" "$route"
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sha256sum --check "$work/shared.sha256"
systemctl reload caddy
systemctl is-active --quiet caddy
check_sites
healthy=false
for attempt in {1..20}; do
  if curl -fsS --connect-timeout 5 --max-time 10 "https://${RB1_DOMAIN}/health" > "$work/health.json" &&
    node -e 'if(require(process.argv[1]).service!=="rb1-dashcam")process.exit(1)' "$work/health.json"; then
    healthy=true; break
  fi
  sleep 3
done
test "$healthy" = true
echo 'RB1 API healthy; all four existing sites preserved and checked.'
