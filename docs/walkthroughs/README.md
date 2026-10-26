# Walkthroughs

One page per path through the system: a sequence diagram with three lanes (green for code in
this repository, blue for Spring and its libraries, grey for infrastructure), a numbered
narrative, a table of who wrote what, where to look, what breaks if a piece is removed, and
the questions the page answers.

1. [One secured request, end to end](01-secured-request-add-member.md): an admin adds a member.

The diagrams are Mermaid source inside the page. The SVG next to each page is rendered from
that source for a full-size view and must be re-rendered after a change:

```bash
npx -y @mermaid-js/mermaid-cli@11.4.2 -i <diagram>.mmd -o <page>.svg -b white
```

where `<diagram>.mmd` is the fenced `mermaid` block copied out of the page.
