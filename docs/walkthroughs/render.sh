#!/usr/bin/env bash
# Renders every Mermaid block of a walkthrough page to an SVG next to it:
# the first block to <page>.svg, the n-th to <page>-n.svg.
# Usage: docs/walkthroughs/render.sh docs/walkthroughs/01-secured-request-add-member.md
set -euo pipefail
page="$1"
base="${page%.md}"
tmpdir="$(mktemp -d -t walkthrough)"
awk -v dir="$tmpdir" '/^```mermaid/{n++; f=1; next} /^```/{f=0; next} f{print > (dir "/" n ".mmd")}' "$page"
for mmd in "$tmpdir"/*.mmd; do
  n="$(basename "$mmd" .mmd)"
  svg="$base.svg"; [ "$n" = 1 ] || svg="$base-$n.svg"
  npx -y @mermaid-js/mermaid-cli@11.4.2 -i "$mmd" -o "$svg" -b white
  # The tool writes width="100%", which keeps browser zoom from enlarging the picture:
  # give the root element the pixel size of its viewBox instead.
  read -r w h < <(grep -o 'viewBox="[^"]*"' "$svg" | head -1 | awk -F'[" ]' '{print $4, $5}')
  sed -i.bak -e "s/ width=\"100%\"/ width=\"$w\" height=\"$h\"/" -e 's/max-width: [0-9.]*px; //' "$svg"
  rm -f "$svg.bak"
  echo "rendered $svg (${w}x${h})"
done
rm -rf "$tmpdir"
