import { render } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ThemeProvider } from '../context/ThemeContext'

/**
 * The one way a component under test gets mounted.
 *
 * Written before the second test needed it. The alternative -- each file
 * arranging its own wrapper -- ends as a couple of dozen bespoke ones that
 * drift, and then a provider added to `main.jsx` has to be added in a couple of
 * dozen places.
 *
 * What it wraps, and why only these two:
 *
 *   MemoryRouter   almost every page uses a `Link`, `useNavigate` or
 *                  `useParams`, and all three throw outside a router.
 *   ThemeProvider  `context/ThemeContext.jsx` is the ONLY `createContext` in
 *                  this codebase. There is no Redux store to wrap: the
 *                  `@reduxjs/toolkit` and `react-redux` packages are declared
 *                  in `package.json` and imported nowhere (register F1).
 *
 * `APIProvider` is not here -- it is module-mocked in `src/test/setup.js`,
 * because it reads a secret and loads a remote script.
 *
 * Everything else is opt-in, on the argument that a helper that silently
 * supplies context is a helper that hides a missing provider.
 */
export function renderWithProviders(ui, options = {}) {
  const {
    initialEntries = ['/'],
    // Mount `ui` under a path pattern, for a component that reads route params.
    path,
    ...renderOptions
  } = options

  const Wrapper = ({ children }) => (
    <MemoryRouter initialEntries={initialEntries}>
      <ThemeProvider>
        {path ? <Routes><Route path={path} element={children} /></Routes> : children}
      </ThemeProvider>
    </MemoryRouter>
  )

  return render(ui, { wrapper: Wrapper, ...renderOptions })
}

export { renderWithProviders as default }
