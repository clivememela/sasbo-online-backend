/**
 * Where a person goes after signing in -- `/login`'s and `/change-password`'s `next`.
 *
 * 🔴 `next` ARRIVES IN AN ADDRESS ANYBODY CAN WRITE. `/login?next=https://elsewhere.example` in an e-mail is how a
 * sign-in page becomes the first step of somebody else's phishing page: the person signs in on the real site and is
 * sent on to a copy of it. So only a path on THIS site is honoured, judged the way a browser will read it:
 *   - one leading `/`, and not `//` or `/\` -- both mean "another host" to a browser;
 *   - no backslash and no control character anywhere -- browsers turn `\` into `/` and drop tabs and newlines;
 *   - resolved against this origin, it must still be on it -- the last word, whatever the text looked like.
 * Anything else, and the sign-in pages themselves (a loop), go to the fallback.
 */
const PROBE = 'https://sasbo.invalid';

export const safeNext = (next, fallback = '/home') => {
  if (typeof next !== 'string' || !/^\/(?![/\\])/.test(next) || /[\\\p{Cc}]/u.test(next)) return fallback;
  try {
    const url = new URL(next, PROBE);
    if (url.origin !== PROBE || /^\/(login|change-password)(\/|$)/.test(url.pathname)) return fallback;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return fallback;
  }
};
