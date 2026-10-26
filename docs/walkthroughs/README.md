# Walkthroughs

One page per path through the system: a sequence diagram with three lanes (green for code in
this repository, blue for Spring and its libraries, grey for infrastructure), a numbered
narrative, a table of who wrote what, where to look, what breaks if a piece is removed, and
the questions the page answers.

1. [One secured request, end to end](01-secured-request-add-member.md): an admin adds a member.

Each page shows the diagram as an SVG with its own white background, so it reads the same
in light and dark mode and in any viewer, with a "full size" link to the raw file for
browser zoom. The Mermaid source sits folded under the picture as the editable mirror and
the SVG is re-rendered from it after a change:

```bash
npx -y @mermaid-js/mermaid-cli@11.4.2 -i <diagram>.mmd -o <page>.svg -b white
# the tool writes width="100%", which stops browser zoom from enlarging the picture;
# give the root element the pixel size of its viewBox instead (the sed is for macOS):
w=$(grep -o 'viewBox="[^"]*"' <page>.svg | head -1 | awk -F'[" ]' '{print $4}')
h=$(grep -o 'viewBox="[^"]*"' <page>.svg | head -1 | awk -F'[" ]' '{print $5}')
sed -i '' -e "s/ width=\"100%\"/ width=\"$w\" height=\"$h\"/" -e 's/max-width: [0-9.]*px; //' <page>.svg
```

where `<diagram>.mmd` is the fenced `mermaid` block copied out of the page.
