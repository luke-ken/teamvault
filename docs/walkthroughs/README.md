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
```

where `<diagram>.mmd` is the fenced `mermaid` block copied out of the page.
