import { createContext, useCallback, useEffect, useMemo, useState } from 'react';

/*
 * 🗣️ LIGHT BY DEFAULT -- the 5 Oct Show & Tell (handover §A, "light mode by default"). A browser with no stored choice
 * starts light, whatever its operating system prefers: nothing here reads `prefers-color-scheme`, and index.html's
 * pre-paint script and its `color-scheme` meta give the same answer before React runs. The key stays `darkMode` --
 * the presentation decks set it before the page's scripts run.
 */
const STORAGE_KEY = 'darkMode';

/**
 * R-ROB-D. Both `localStorage` calls are wrapped, and the READ matters most.
 *
 * Safari in private mode, a browser configured to block site data, and some
 * embedded webviews all THROW on `localStorage` rather than returning null. The
 * read below runs inside `useState`'s initialiser, which runs during render --
 * above every error boundary in the tree. An uncaught throw there is not a
 * degraded theme, it is a WHITE PAGE, and no boundary in this app can catch it.
 *
 * The fallback is "light", the same answer a missing key gives, so a browser
 * that cannot remember the choice still renders correctly -- it just forgets.
 */
const readStoredTheme = () => {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
};

const storeTheme = (darkMode) => {
  try {
    localStorage.setItem(STORAGE_KEY, darkMode);
  } catch {
    // Cannot persist. The theme still applies for this page; only memory is lost.
  }
};

const ThemeContext = createContext();

export const ThemeProvider = ({ children }) => {
  const [darkMode, setDarkMode] = useState(readStoredTheme);

  useEffect(() => {
    storeTheme(darkMode);
    // The pre-paint script in index.html has already set this on first load;
    // re-applying is what keeps it correct on every subsequent toggle.
    document.documentElement.classList.toggle('dark', darkMode);
  }, [darkMode]);

  /**
   * R-PERF-F, half one. Already a FUNCTIONAL update before this wave -- it reads no
   * `darkMode` from the closure, so it needs no dependency and can be created once.
   * That is what makes the memo below worth having: a callback rebuilt every render
   * would change the context value every render regardless.
   */
  const toggleDarkMode = useCallback(() => setDarkMode((previous) => !previous), []);

  /**
   * R-PERF-F, half two. THE OBJECT LITERAL WAS THE PROBLEM, not the values in it.
   *
   * `value={{ ... }}` builds a NEW object on every render of this provider, and every
   * consumer of a context re-renders when the value's IDENTITY changes -- not when the
   * data does. This provider wraps the entire application, so one unrelated re-render
   * here re-rendered every screen, every table row and every open menu, and the theme
   * had not changed.
   *
   * `setDarkMode` is stable by `useState`'s own contract, `toggleDarkMode` is stable
   * above, so the identity now changes exactly when `darkMode` does -- which is the only
   * time a consumer has anything new to render. `ThemeContext.test.jsx` asserts it by
   * re-rendering without touching the theme and comparing identity, because "we added a
   * useMemo" is not evidence that the identity held.
   */
  const value = useMemo(
    () => ({ darkMode, toggleDarkMode, setDarkMode }),
    [darkMode, toggleDarkMode],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};

export { ThemeContext };
