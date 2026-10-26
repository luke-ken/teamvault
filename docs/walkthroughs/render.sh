#!/usr/bin/env bash
# Renders the Mermaid block of a walkthrough page to the SVG next to it.
# Usage: docs/walkthroughs/render.sh docs/walkthroughs/01-secured-request-add-member.md
set -euo pipefail
page="$1"
svg="${page%.md}.svg"
tmp="$(mktemp -t walkthrough).mmd"
awk '/^```mermaid/{f=1;next}/^```/{if(f){exit}}f' "$page" > "$tmp"
npx -y @mermaid-js/mermaid-cli@11.4.2 -i "$tmp" -o "$svg" -b white
# The tool writes width="100%", which keeps browser zoom from enlarging the picture:
# give the root element the pixel size of its viewBox instead.
read -r w h < <(grep -o 'viewBox="[^"]*"' "$svg" | head -1 | awk -F'[" ]' '{print $4, $5}')
sed -i.bak -e "s/ width=\"100%\"/ width=\"$w\" height=\"$h\"/" -e 's/max-width: [0-9.]*px; //' "$svg"
rm -f "$svg.bak" "$tmp"
echo "rendered $svg (${w}x${h})"
