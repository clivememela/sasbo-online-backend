/**
 * Where the suite points, and the only place that decides it.
 *
 * THE INVARIANT THIS FILE EXISTS TO HOLD
 * The suite never contains a relative path reaching up out of this repository
 * into the client's or the server's directory -- not in a config file, not in a
 * comment, not in a compose build context. Both targets come from the
 * environment, with localhost defaults.
 *
 * (The forbidden strings are deliberately not spelled out here: an executable
 * guard greps every file in this repository for them, and a comment quoting one
 * would fail that guard. See tests/harness/invariants.spec.js.)
 *
 * WHY THAT IS LOAD-BEARING AND NOT A STYLE CHOICE
 * A suite that reaches up into a sibling checkout runs only where all three
 * repositories happen to sit side by side on one disk. Resolving targets from the
 * environment is what lets the same suite -- unchanged, no code edit -- run
 * against a local dev stack, against the container stack this repo defines, or
 * against a deployed preview environment. That is the difference between a
 * suite that can be wired into a pipeline later and one that has to be
 * rewritten first.
 *
 * The defaults are the local dev stack because that is the common case when a
 * developer runs the suite by hand. The container stack overrides both, because
 * it deliberately publishes non-default host ports -- see docker-compose.yml.
 */

/** The application's own dev server, when a developer is running one. */
const DEFAULT_CLIENT_URL = 'http://localhost:5173'

/** The API's default port, when a developer is running it directly. */
const DEFAULT_API_URL = 'http://localhost:8080'

/**
 * The mail catcher's API -- Mailpit's own default port, for a developer running
 * one by hand. The container stack overrides it like the other two. It is a
 * TARGET rather than a detail because since server 99936a5 a capture sends the
 * welcome letter (register row A64), and the letter is only assertable here.
 */
const DEFAULT_MAIL_URL = 'http://localhost:8025'

/**
 * Read one target from the environment.
 *
 * A malformed URL fails HERE, at load time, with the variable name in the
 * message -- rather than 40 scenarios later as an unexplained navigation error.
 * A typo in an environment variable is the single most likely way to point a
 * green suite at the wrong thing.
 */
function readTarget(name, fallback) {
  const raw = (process.env[name] ?? '').trim()
  const value = raw === '' ? fallback : raw

  let parsed
  try {
    parsed = new URL(value)
  } catch {
    throw new Error(
      `${name} is not a valid URL: ${JSON.stringify(value)}. ` +
        `Expected something like ${fallback}.`,
    )
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error(`${name} must be http or https, got ${JSON.stringify(value)}.`)
  }

  // Trailing slashes are stripped so callers can join paths without doubling
  // them. `${API_URL}/api/banks` must not become `http://host//api/banks`:
  // some servers treat that as a different route, and the resulting 404 looks
  // like a missing endpoint rather than a formatting bug.
  return value.replace(/\/+$/, '')
}

export const CLIENT_URL = readTarget('SASBO_CLIENT_URL', DEFAULT_CLIENT_URL)
export const API_URL = readTarget('SASBO_API_URL', DEFAULT_API_URL)
export const MAIL_URL = readTarget('SASBO_MAIL_URL', DEFAULT_MAIL_URL)

/** Exported for the harness self-tests, which exercise the parsing rules. */
export const _internals = { readTarget, DEFAULT_CLIENT_URL, DEFAULT_API_URL, DEFAULT_MAIL_URL }
