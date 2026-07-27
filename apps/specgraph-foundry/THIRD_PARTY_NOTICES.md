# Third-Party Notices

## Python runtime dependencies

### pypdf 4.3.1

- Upstream source: https://github.com/py-pdf/pypdf
- License: BSD-3-Clause
- Copyright:
  Copyright (c) 2006-2008, Mathieu Fenniak.
  Some contributions copyright (c) 2007, Ashish Kulkarni.
  Some contributions copyright (c) 2014, Steve Witham.
- Use in this repository: pure-Python PDF parsing for bounded, non-OCR text extraction in the Group 06 document adapter.

Relevant license notice excerpt:

> Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

## Web application direct dependencies

The Group 10 web foundation directly pins these npm packages in
`apps/web/package.json` and `apps/web/package-lock.json`:

- Next.js 16.2.10, MIT, https://github.com/vercel/next.js
- React 19.2.7 and React DOM 19.2.7, MIT, https://github.com/facebook/react
- @supabase/supabase-js 2.108.0 and @supabase/ssr 0.12.0, MIT, https://github.com/supabase/supabase-js and https://github.com/supabase/ssr
- @tanstack/react-query 5.101.2, MIT, https://github.com/TanStack/query
- react-hook-form 7.81.0, MIT, https://github.com/react-hook-form/react-hook-form
- zod 4.4.3, MIT, https://github.com/colinhacks/zod
- @hookform/resolvers 5.4.0, MIT, https://github.com/react-hook-form/resolvers
- Radix UI primitives: @radix-ui/react-slot 1.3.0, @radix-ui/react-dialog 1.1.19, @radix-ui/react-dropdown-menu 2.1.20, @radix-ui/react-label 2.1.11, @radix-ui/react-tooltip 1.2.12, MIT, https://github.com/radix-ui/primitives
- clsx 2.1.1, MIT, https://github.com/lukeed/clsx
- tailwind-merge 3.6.0, MIT, https://github.com/dcastil/tailwind-merge
- @xyflow/react 12.11.2, MIT, https://github.com/xyflow/xyflow
- elkjs 0.11.1, EPL-2.0, https://github.com/kieler/elkjs
- TypeScript 5.9.3, Apache-2.0, https://github.com/microsoft/TypeScript
- ESLint 9.39.5, MIT, https://github.com/eslint/eslint
- eslint-config-next 16.2.10, MIT, https://github.com/vercel/next.js
- Vitest 3.2.7, MIT, https://github.com/vitest-dev/vitest
- Vite 6.4.3 and @vitejs/plugin-react 4.7.0, MIT, https://github.com/vitejs/vite-plugin-react
- jsdom 26.1.0, MIT, https://github.com/jsdom/jsdom
- Testing Library packages, MIT, https://github.com/testing-library
- MSW 2.15.0, MIT, https://github.com/mswjs/msw
- openapi-typescript 7.13.0, MIT, https://github.com/openapi-ts/openapi-typescript
- Playwright test 1.61.1, Apache-2.0, https://github.com/microsoft/playwright
- axe-core 4.12.1 and @axe-core/playwright 4.12.1, MPL-2.0, https://github.com/dequelabs/axe-core-npm

EPL-2.0 and MPL-2.0 packages are admitted for this frontend foundation
because Group 10 explicitly requires ELK graph compatibility and axe
accessibility test infrastructure. They are used as package dependencies,
not copied into repository source.

The following technologies remain research candidates and are not bundled
unless listed above:

- Cytoscape.js
- Sigma.js
- Apache ECharts
- Mermaid
- Graphviz
- GitHub Models
- OpenCode
- Google Vertex AI
- Google Cloud Tasks
- Prefect
- Temporal
- Dagster

Before any dependency is added, its exact version, upstream source,
copyright holder, SPDX identifier, license text, attribution requirements,
modifications, and distribution obligations must be recorded here.
