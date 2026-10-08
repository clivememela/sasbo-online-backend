import { PASSWORD_CHANGE_REQUIRED, SESSION_ENDING, endSession, readSession, requirePasswordChange } from '../auth/session';

const RAW_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

// Validate the base URL is an http/https origin — prevents SSRF from a misconfigured env var
const API_BASE_URL = (() => {
  if (!RAW_BASE) return '';
  try {
    const { protocol, origin } = new URL(RAW_BASE);
    if (protocol !== 'http:' && protocol !== 'https:') return '';
    return origin;
  } catch {
    return '';
  }
})();

export function apiUrl(path) {
  // Ensure path starts with / and contains no protocol — prevents open redirect / SSRF
  const safePath = '/' + path.replace(/^\/+/, '').replace(/^[a-z][a-z0-9+\-.]*:\/\//i, '');
  if (!API_BASE_URL) return safePath;
  return `${API_BASE_URL}${safePath}`;
}

/**
 * Is this request one to THIS application's API? Exactly what `apiUrl` builds: the configured API origin followed by
 * `/api/` -- or, with no origin configured (Vite proxies `/api`), a path starting `/api/`. The origin ends where `/api/`
 * begins, so a lookalike host (`api.example.evil`) or another origin's `/api/` cannot pass, and a protocol-relative
 * `//host/api/` does not start with `/api/`.
 */
const API_PREFIX = `${API_BASE_URL}/api/`;

export const isApiRequest = (input) =>
  (typeof input === 'string' ? input : input?.url ?? String(input)).startsWith(API_PREFIX);

/** The two refusals that mean "this session is over" (signin.md). `SIGN_IN_FAILED` is a wrong password, not an ending. */
const SESSION_OVER = new Set(SESSION_ENDING);

/** A 401's refusal code -- read from a copy, so the caller still gets the body to read. Not JSON is no code. */
const refusalCode = async (response) => {
  try {
    return (await response.clone().json())?.code ?? null;
  } catch {
    return null;
  }
};

const INSTALLED = Symbol.for('sasbo.sessionFetch');

/**
 * THE BEARER TOKEN, ON EVERY API REQUEST -- the sign-in contract's "`api.js` adds `Authorization: Bearer <token>` when a
 * session exists".
 *
 * 🔑 WHY A WRAPPER AROUND `fetch` AND NOT A HEADER AT EACH CALL SITE. Every service writes its own
 * `fetch(apiUrl('/api/…'), { credentials: 'include', signal })`, deliberately -- the API contract scan reads the path at
 * each call site (`requestJson`'s comment) -- so there is no shared request function to add a header to. Every existing
 * call site, and every service the October streams are adding, carries the token with no edit, and a service written
 * tomorrow cannot forget it.
 *
 * 🔴 IT IS NARROW ON PURPOSE. Only a request to this application's own API is touched (`isApiRequest`): a bearer token
 * sent to any other origin -- Google's map scripts, a typo'd absolute URL -- is a leaked session. A caller that sets its
 * own `Authorization` keeps it. Everything else passes through untouched, with no session or without.
 *
 * It also reads each API `401`: `SESSION_EXPIRED` or `SIGN_IN_REQUIRED` ends the session (`endSession`), before the caller
 * sees the response, so the screen that asked and the shell agree about who is signed in. A `403
 * PASSWORD_CHANGE_REQUIRED` marks the session's user as on a temporary password, which sends them to /change-password.
 *
 * Installed once, from `main.jsx` -- and from `src/test/setup.js` after MSW has patched `fetch`, so this is the outer
 * layer there too and the suite's handlers see the header. Returns the uninstall.
 */
export function installSessionFetch() {
  const inner = globalThis.fetch;
  if (typeof inner !== 'function' || inner[INSTALLED]) return () => {};

  const sessionFetch = async (input, init) => {
    if (!isApiRequest(input)) return inner(input, init);
    const token = readSession()?.token;
    let sent = init;
    if (token) {
      const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
      if (!headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`);
      sent = { ...init, headers };
    }
    const response = await inner(input, sent);
    if (response.status === 401) {
      const code = await refusalCode(response);
      if (SESSION_OVER.has(code)) endSession(code, token);
    } else if (response.status === 403 && token && await refusalCode(response) === PASSWORD_CHANGE_REQUIRED) {
      requirePasswordChange(token);
    }
    return response;
  };
  sessionFetch[INSTALLED] = true;
  globalThis.fetch = sessionFetch;

  return () => {
    if (globalThis.fetch === sessionFetch) globalThis.fetch = inner;
  };
}
