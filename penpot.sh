#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v node >/dev/null; then
    node_path=$(find "$HOME/.nvm/versions/node" -path '*/bin/node' -type f 2>/dev/null | sort -V | tail -n 1)
    [[ -n "$node_path" ]] || { echo 'Node.js 22 or newer is required.' >&2; exit 1; }
    export PATH="$(dirname "$node_path"):$PATH"
fi
bridge="$PWD/.tools/penpot-source/mcp"
[[ -d "$bridge" ]] || { echo 'Penpot source missing; see DESIGN_TOOLS.md for setup.' >&2; exit 1; }
export PATH="$PWD/.tools/penpot-source/bin:$PATH"
cd "$bridge"
if [[ -f packages/server/dist/index.js && -d packages/plugin/dist ]]; then
    exec pnpm run start
fi
CI=true pnpm install --frozen-lockfile
pnpm run build
exec pnpm run start
