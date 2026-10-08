/**
 * `/login?next=<this screen>` -- for every place that asks a person to sign in and means to bring them back (the header,
 * the rail, the shell's required-mode redirect, `RequirePermission`). How `/login` judges the `next` it is handed is
 * `safeNext.js`, loaded with the sign-in pages rather than the shell.
 */
export const loginPath = ({ pathname = '/', search = '' } = {}) =>
  `/login?next=${encodeURIComponent(`${pathname}${search}`)}`;
