import { lazy } from 'react';

/**
 * The shell's three lazy pieces of sign-in -- the header's account, the rail's foot, the ended-session notice -- from ONE
 * dynamic import (OB17a). Each `import()` site carries Vite's preload wrapper and dependency list into the first load,
 * so three sites for one small module paid for it three times; they are one module and one chunk, asked for once.
 */
const load = () => import('./AccountControls');

export const HeaderAccount = lazy(() => load().then((m) => ({ default: m.HeaderAccount })));
export const RailAccount = lazy(() => load().then((m) => ({ default: m.RailAccount })));
export const SessionNotice = lazy(() => load().then((m) => ({ default: m.SessionNotice })));
