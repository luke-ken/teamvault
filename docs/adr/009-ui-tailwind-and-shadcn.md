# ADR-009: UI built on Tailwind CSS and shadcn/ui components

- **Date:** 2026-11-15
- **Status:** proposed

## Context
The frontend needs a small set of UI parts: two forms, a file list, a dialog, buttons, a
toast. They have to be accessible (focus handling, keyboard, labels) and look deliberate
without a design effort, because the project's subject is the backend and the integration,
not a visual identity. The author works with MUI every day. The question is whether the UI
layer repeats that experience or spends its small surface on a stack that is new.

## Options considered
1. **MUI**: complete components, accessibility solved, no learning time. Cons: nothing new is
   learned; a large runtime and a CSS-in-JS engine for eight components; changing how a
   component looks means overriding nested selectors of markup the library owns.
2. **Ant Design**: the same kind of library as MUI with another look. No advantage over
   option 1 here, and it would be new without being different.
3. **Plain CSS modules, no library**: no dependency, full control. Cons: dialog focus traps,
   keyboard handling and form states are written and tested by hand, which is the part a
   library gets right and a sprint gets wrong.
4. **Tailwind CSS plus shadcn/ui**: utility classes for styling; component source copied into
   the repository by a CLI, built on headless accessible primitives. Pros: the components are
   our files, changed by editing classes and not by overriding selectors; only what is added
   is shipped; accessibility comes from the primitives. Cons: long class lists in the markup;
   copied code is ours to maintain and does not update itself; two tools to learn inside a
   sprint whose goal is a deployed app.
5. **Tailwind on top of MUI**: possible, but two styling systems would compete for the same
   elements, and the work goes into deciding which one wins. Sensible for migrating an
   existing MUI application, not for a new one.

## Decision
Option 4. Tailwind CSS 4 through its Vite plugin, configured in CSS. shadcn/ui components are
added one at a time into `src/components/ui/`, each with the feature that first needs it, and
are treated as project code from then on: formatted, linted, reviewed. The default theme
stays; no custom design system. Class order is fixed by the Prettier plugin.

## Trade-off (why this, what we give up)
We give up the speed of a library the author already knows, and accept a few hours of
learning in a sprint that is about shipping, because the UI surface is small enough to carry
it and a second MUI project would teach nothing. We accept class lists in the markup in
exchange for styles that sit next to the element they affect and cannot leak. We accept
ownership of copied component code: a fix upstream reaches us only if we pull it in by hand,
which is tolerable for under ten components and would not be for fifty. Utility classes do
not exercise selectors and the cascade; that knowledge is practised elsewhere, not here.
Revisit if the UI grows past what copied components serve well (data grids, date pickers,
rich tables): then a full component library earns its weight.
