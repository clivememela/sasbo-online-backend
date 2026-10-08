# AGENTS.md: SASBO Online Frontend

**Domain**: SASBO membership management system UI. React SPA serving member search, bank/branch lookup, administrative forms, reports, and archives.

## Architecture Overview

**Tech Stack**: React 19.2 | Vite | Tailwind CSS 4.1 | React Router 7 | JavaScript

> ⚠️ **Redux Toolkit is NOT in this project.** `@reduxjs/toolkit` and `react-redux` were declared in
> `package.json` and imported by nothing; both were removed on 2026-09-09. There is no store, no provider and
> no slice. Global state is React Context — see *State Management* below.

Single project repo serving as SPA on port 5173 (dev) or Render static site (prod).

## Project Structure

```
src/
├── pages/              # Route-level components mapped in App.jsx
├── components/         # Reusable UI components
│   ├── common/         # Shared (Logo, Breadcrumbs, PageTitle, ThemeToggle)
│   ├── form/           # Form controls (TextInput, SelectInput, Button, FilterForm)
│   ├── Footer.jsx
│   ├── Header.jsx
│   ├── Main.jsx
│   └── Sidebar.jsx
├── layouts/            # Page wrappers (AppLayout, AuthLayout)
├── services/           # API communication
│   └── api.js          # Base URL + request utility
├── hooks/              # Custom React hooks (shared logic)
├── context/            # React Context (ThemeContext for dark/light mode)
├── config/             # App configuration (routes, constants)
├── data/               # Static JSON fixtures (banks.json, members.json)
├── utils/              # Helper functions
└── assets/             # Images, styles, documents
    ├── styles/         # Global CSS
    ├── images/         # Logos, icons
    └── documents/      # PDFs
```

## Key Patterns & Conventions

### Routing & Layouts
- **App.jsx**: Defines all routes using React Router v7
- **Two layouts**:
  - **AuthLayout**: Wrapper for `/login`, `/signup` (no sidebar)
  - **AppLayout**: Wrapper for authenticated routes with sidebar + header + footer
- **Nested routes**: Parent route (`/`) renders `AppLayout`, child routes render page components

Example structure:
```jsx
<Route path="/" element={<AppLayout />}>
  <Route index element={<Navigate to="/login" />} />
  <Route path="home" element={<HomePage />} />
  <Route path="members" element={<MembersPage />} />
  <!-- nested routes inherit AppLayout -->
</Route>
```

### API Communication Pattern
- **File**: `src/services/api.js` exports `apiUrl(path)` utility
- **Environment-driven base URL**: 
  - Dev: `VITE_API_BASE_URL` from `.env` (defaults to empty, uses Vite proxy `/api` → `localhost:8080`)
  - Prod: `VITE_API_BASE_URL` set to Render backend URL
- **Usage**:
  ```javascript
  import { apiUrl } from './services/api.js';
  
  fetch(apiUrl('/api/members'))
    .then(res => res.json())
  ```

- **Domain service pattern**: small service modules live under `src/services/` (for example `bankService.js`, `memberService.js`, `representativeService.js`, `locationService.js`). They typically export concrete helper functions or small objects (e.g. `export const bankService = { getBanks: ..., createBank: ..., getPageSize: ... }`). Prefer using these service primitives in components instead of calling `fetch` directly — they return Promises that handle JSON parsing and basic error handling (see `src/services/bankService.js` usage in `src/pages/bank/BankBranch.jsx` where `bankService.getBanks()` and `bankService.getPageSize()` are used).

### Component Patterns

#### Form Components (`components/form/`)
- **TextInput, SelectInput**: Controlled inputs with onChange handlers
- **Button**: Reusable action button
- **FilterForm**: Compound component for search/filter UI
- **SearchableSelect**: Dropdown with search capability

- **Barrel export**: `src/components/form/index.js` re-exports form components. Import from the barrel when building pages: `import { TextInput, Button, SearchableSelect } from '../../components/form';` — this is used throughout the codebase (see `src/pages/bank/BankBranch.jsx`).

#### Common Components (`components/common/`)
- **Logo**: SASBO branding
- **Breadcrumbs**: Navigation trail
- **PageTitle**: Heading with optional subtitle
- **SidebarItem**: Navigation link item
- **ThemeToggle**: Dark/light mode switcher

#### Layout Components
- **Header**: Top navigation bar
- **Sidebar**: Left navigation panel
- **Footer**: Bottom footer
- **Main**: Central content area
- **AppLayout**: Combines Header + Sidebar + Main + Footer
- **AuthLayout**: Auth-specific layout (login page only)

### Styling Convention
- **Tailwind CSS 4.1**: Utility-first CSS framework
- **Files**:
  - `src/assets/styles/index.css`: Global resets + Tailwind directives
  - `src/assets/styles/App.css`: Page-specific overrides (minimal)
- **Usage**: Inline Tailwind classes in JSX (e.g., `<div className="flex gap-4 p-8">`)

Note: The project also uses the `@tailwindcss/vite` plugin (configured in `vite.config.js`) to integrate Tailwind with Vite. Agents should preserve the plugin when editing `vite.config.js`.

> ⚠️ **The `shadcn-ui` package was removed on 2026-09-09** — it was a legacy package name (the CLI is now
> `shadcn`), was imported by nothing, and provided no UI patterns to reference. shadcn/ui **is** the adopted
> component approach for this project *(decision D2)*, but it is installed per-component with
> `npx shadcn@latest add <component>`, which copies source into the repo rather than adding a dependency.
> It **was initialised** in unit `03.1` of the design arc (`components.json` exists), but **no shadcn component has
> been added**: the primitives under `src/components/` are this project's own, built on Radix and the token layer.
> ⚠️ `components.json` aliases `ui` to `@/components/ui`, which does not exist — and `npx shadcn add` would write
> raw colour utilities that `paletteLiterals.test.js` rejects. Read `COMPONENTS.md` before adding one.

- **Print / modal helpers**: Printable views use the `no-print` and `print-area` CSS classes (e.g. `src/pages/bank_branch/BankBranch.jsx`) and `globalThis.print()`. **Every dialog is the shared `Modal` / `ConfirmDialog`** in `src/components/overlay/Modal.jsx` (Radix): it owns the focus trap, Escape, returning focus, `busy` (no way out while a write runs) and `initialFocusRef`. Do not hand-roll a fixed overlay, and do not use `alert`/`confirm`/`prompt` — `npm run lint` fails on them. The rules a new screen inherits are in `SCREEN-CONTRACT.md`.

### State Management
- **React Context**: `ThemeContext.jsx` manages dark/light mode globally — this is the only global state
  mechanism in the project. Do not reach for Redux; it is not installed.
- **Custom Hooks**: `src/hooks/` folder for reusable stateful logic (e.g., `useTheme.js`)

### Icon Library

🔴 **One icon library: `lucide-react`. There is no choice to make.**

This section previously listed five and told agents to "choose per component; prefer Heroicons". That was
wrong in both halves: **`lucide-react` was the only one any file actually imported** (29 files), and the other
four — `@fortawesome/*`, `@heroicons/react`, `@tabler/icons-react`, `react-icons` — were declared and imported
by nothing. **All four were removed on 2026-09-09** *(decision D3)*. Importing from them now fails the build.

`lucide-react` is also shadcn/ui's default set, so D2 and D3 agree rather than compete.

## Build & Local Development

```bash
# Install dependencies
npm ci

# Start dev server (Vite, port 5173, HMR enabled)
npm run dev

# Production build (optimized, minified)
npm run build

# Local preview of production build
npm run preview

# Lint code (ESLint)
npm run lint
```

### Development Environment Setup
1. **Backend must run** on `localhost:8080` (required for API proxy)
2. **.env file** in project root:
   ```
   VITE_API_BASE_URL=http://localhost:8080
   ```
3. **Vite proxy** in `vite.config.js` forwards `/api/*` requests to backend

## Page Architecture

Each page component in `src/pages/`:
1. **Home**: Dashboard/landing page
2. **BankBranch** (`bank/BankBranch.jsx`): Search & manage bank branches with create/edit capability
   - Uses `bankService.getBanks()` to fetch branch data with enhanced fields (bankName, branch, area, randomAccessNumber)
   - Includes modal for creating/editing branches
   - Print and filter functionality
3. **BankListing** (`bank/BankListing.jsx`): Read-only bank listing with basic information
   - Uses `bankService.getBankListing()` for consolidated bank data (bank name, abbreviation, universal branch code)
   - Simpler UI focused on lookup and display
4. **Members** (`members/Members.jsx`): Search & manage members
   - **MemberHistory**: the record's History tab (month end `03.6`) -- one ruled row a change, newest first, from
     `GET /api/members/{id}/history`; a lazy chunk of its own, asked for when the tab is opened
   - **CaptureMember**: Form to add new member
   - **Representatives**: Search representatives
5. **Forms** (`forms/`):
   - **MembershipApplication**: PDF form for new membership
   - **Forms**: Generic forms page
6. **Reports** (`reports/`):
    - **ReportOptions**: Report menu
    - **OrganizerReport**: the **Location Visit** report — an organiser's visit to a branch or department (named "Location Visit" on screen since SASBO's 18 Sep 2026 ask; the route stays `/reports/organizer-report`)
    - **Locations**: Geographic reports
7. **Archives** (`archives/`):
   - **ArchiveSearch**: Historical data search
   - **AffiliationsOtherTradeUnions**: Archive section
   - **FinanceHouses**: Finance data archives
   - **ResearchInformation**: Research archives
8. **Admin** (`admin/`):
    - **Admin**: Dashboard
    - **DailyMenu**: Daily operations menu
    - **MonthlyMenu**: the **Month end** page, at `/admin/monthly-menu` (the address kept, MD38) — the month, its six
      statistics (`MonthEndStatistic`, `/admin/monthly-menu/:statistic`), the statistics pack, and the listings that
      wait for sign-in
    - **Maintenance**: System maintenance page
9. **Auth** (`auth/Auth.jsx`): Login/signup page

Notes on page patterns:
- **Modals & overlays**: Pages commonly open small modal components within the page (for example `src/pages/bank/BankBranchModal.jsx` used from `BankBranch.jsx`). Typical pattern: a boolean state (e.g. `showAddBank`) toggles rendering of the modal; the modal exposes callbacks such as `onCreated(newItem)` which the parent uses to merge data into local state (see how `BankBranch.jsx` appends a created bank: `setBanks(prev => [...prev, newBank])`).
- **Service-backed create flows**: Creation UI components call the domain service (e.g. `bankService.createBank(data)`) and rely on the service to handle POST and basic error handling. Follow that pattern when adding new create/update flows.

## Critical Configuration Files

| File               | Purpose                                        |
|--------------------|------------------------------------------------|
| `package.json`     | Dependencies & npm scripts                     |
| `vite.config.js`   | Vite build config + API proxy                  |
| `.env`             | Local development env vars (VITE_API_BASE_URL) |
| `vite.config.js`   | Frontend build & dev server config             |
| `eslint.config.js` | Linting rules                                  |

## Deployment

None for now. Since 2026-09-22 the project runs locally: it left the incumbent developer's Vercel and Render,
so there is no `vercel.json` or `render.yaml` here, and nothing deploys `main`.

- **Run**: `npm run dev` serves the client on `http://localhost:5173`, the one origin the backend's CORS
  policy allows.
- **API**: the tracked `.env` points `VITE_API_BASE_URL` at `http://localhost:8080`, the backend's default port.
- **Maps**: `VITE_GOOGLE_API_KEY` is not tracked; a developer's own key goes in `.env.local`. Without one,
  `AddressAutocomplete` offers no suggestions and still takes a typed address.

## Common Workflows

### Adding a New Page
1. Create component in `src/pages/section/PageName.jsx`
2. Add route in `App.jsx`:
   ```jsx
   <Route path="new-page" element={<NewPage />} />
   ```
3. Add navigation link in sidebar config or `Sidebar.jsx`
4. Use `useNavigate()` from React Router for programmatic navigation

### Adding a New Form Component
1. Create in `src/components/form/FormComponentName.jsx`
2. Accept props for `value`, `onChange`, `placeholder`, etc.
3. Export from `src/components/form/index.js` for convenience imports
4. Use Tailwind classes for styling

### Fetching Data from Backend
```javascript
import { apiUrl } from '../services/api';

// In useEffect:
useEffect(() => {
  fetch(apiUrl('/api/members'))
    .then(res => res.json())
    .then(data => setMembers(data))
    .catch(err => console.error(err));
}, []);
```

### Theme Toggle
- **File**: `src/context/ThemeContext.jsx`
 - **Hook**: `useTheme()` provides the theme state and mutators used in this project. Current implementation exposes `darkMode`, `toggleDarkMode`, and `setDarkMode` (see `src/context/ThemeContext.jsx`). Example usage:
  ```jsx
  const { darkMode, toggleDarkMode } = useTheme();
  // darkMode is boolean; toggleDarkMode() flips it; setDarkMode(value) is also available
  ```
 - **Tailwind dark mode**: Add `dark:` prefix for dark mode styles

  - **Persistence**: The `ThemeContext` persists the user's preference in `localStorage` (`localStorage.darkMode`) and toggles the `dark` class on `document.documentElement`. When manipulating theme in automated edits, replicate this behavior to avoid visual inconsistency.

## Known Patterns to Replicate

✅ **Nested Routes with Layouts**: Use React Router outlet for nested pages
✅ **API Base URL**: Always use `apiUrl()` helper for cross-environment compatibility
✅ **Environment Variables**: Prefix with `VITE_` for Vite to expose them
✅ **Tailwind for Styling**: Prefer utility classes; avoid inline CSS
✅ **Reusable Form Components**: Build generic TextInput, SelectInput, Button
✅ **React Context for Global State**: Theme, auth state — Context only; Redux is not installed
✅ **Page-level Components**: Colocate related components in `src/pages/section/`

## PDF Integration

- **inspect_pdf.js**: Utility script for PDF inspection (development tool)
- **pdf-lib**: Library for PDF manipulation
- **Document assets**: `src/assets/documents/` (e.g., Membership Application.pdf)

---
*Last updated: June 22, 2026*

