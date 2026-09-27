# Conductor UI

## Project Overview

Conductor UI is an open-source React web application and npm library for [Netflix Conductor](https://github.com/conductor-oss/conductor). It provides core components, pages, and plugin infrastructure for the Conductor workflow orchestration engine.

- **Type:** Software Project (React Web Application & npm library)
- **Primary Languages:** TypeScript, React (TSX), CSS/SCSS
- **Build Tool:** Vite
- **Package Manager:** pnpm (v10.32.0+)
- **Testing:** Vitest, React Testing Library
- **UI Framework:** Material UI (@mui)

## Building and Running

### Prerequisites

- Node.js 22+
- pnpm 10.32.0 (`corepack use pnpm@10.32.0`)
- A running Conductor server (default: `http://localhost:8080`)

### Setup and Configuration

1. Install dependencies:
   ```bash
   pnpm install
   ```
2. Configure environment variables in `.env` (defaults to `VITE_WF_SERVER=http://localhost:8080`).
3. Set up runtime configuration:
   ```bash
   cp public/context.js.example public/context.js
   ```
   *Note: `public/context.js` is loaded at startup to set feature flags (`window.conductor`) and auth config (`window.authConfig`) without requiring a rebuild.*

### Available Scripts

- `pnpm dev` - Starts the development server with HMR on `http://localhost:1234`. API requests are proxied to the Conductor server.
- `pnpm build` - Builds the standalone application to `dist/`.
- `pnpm build:lib` - Builds the npm library version to `dist/`.
- `pnpm build:all` - Builds both the app and the library.
- `pnpm test` - Runs unit tests using Vitest.
- `pnpm test:watch` - Runs tests in watch mode.
- `pnpm test:coverage` - Runs tests and generates a coverage report.
- `pnpm lint` / `pnpm lint:fix` - Runs ESLint.
- `pnpm prettier:check` / `pnpm prettier:write` - Checks and fixes code formatting.
- `pnpm typecheck` - Type-checks the TypeScript code without emitting files.

## Architecture and Development Conventions

- **Extensibility:** The application is built to be extensible via a plugin system (`pluginRegistry`). Plugins can register custom routes, sidebar items, task forms, task menu items, authentication providers, and search providers.
- **Directory Structure:**
  - `src/components/`: Reusable, shared UI components.
  - `src/pages/`: Route-level page components.
  - `src/plugins/`: Plugin registry and utilities for extending the application.
  - `src/shared/`: Shared state (auth state machine) and context.
  - `src/theme/`: Material UI (MUI) theme provider configurations.
  - `src/types/`: Shared TypeScript type definitions.
  - `src/utils/`: Feature flags, constants, and helper functions.
  - `public/`: Static assets, including the non-bundled `context.js` runtime configuration.
- **Library Usage:** The project exports its components and global styles for use in other applications via `import "conductor-ui/styles.css"` and `import "conductor-ui/global.css"`.
- **Styling:** Primarily uses Material UI components, `@emotion/styled` for custom styled components, and some SCSS/CSS.
- **State Management:** Uses React Query (`react-query`) for data fetching and caching, Jotai (`jotai`) for atomic state management, and XState (`xstate`) for complex state machines (like auth).