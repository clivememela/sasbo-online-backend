import { Link, useLocation, useNavigate } from 'react-router-dom';
import { KeyRound, LogIn, LogOut } from 'lucide-react';

import Button from '../form/Button';
import { cn } from '../../lib/utils';
import { useAuth } from '../../auth/useAuth';
import { loginPath } from '../../auth/next';

// The ended-session notice rides this chunk too (`lazyAccount.js`): one module, asked for once.
export { default as SessionNotice } from '../common/SessionNotice';

/**
 * WHO IS SIGNED IN, AND THE WAY IN OR OUT -- the header's account chip and the rail's foot (contracts/signin.md, "The
 * client"). One LAZY module for both (OB17a): the first load carries the shell and the session, and these arrive with
 * the page's own chunk, which is lazy too -- so they are on screen when the page is, and never in the first paint's bytes.
 *
 * Sign out revokes the session on the server and then opens the sign-in page, in either mode: staying on a screen as
 * somebody who has just left it is how a shared office computer hands the next person a session.
 */

/** Up to two letters from the name a person is shown by -- "Thandi Synthetic" is TS. */
export const initialsOf = (name) =>
  String(name ?? '').trim().split(/\s+/).slice(0, 2).map((word) => word[0] ?? '').join('').toUpperCase();

/** A link that looks like the topbar's ghost buttons: the same 38px row, ring and hover. */
const TOOL_LINK = cn(
  'touch-target inline-flex min-h-(--size-control) items-center gap-2 rounded-lg border border-transparent px-3 text-sm font-medium text-ink-muted',
  'hover-hover:hover:bg-surface hover-hover:hover:text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring',
);

// `touch-size`: the rail's foot takes the same 44px rows on a coarse pointer as the list (`07.2`).
const FOOT_ROW = cn(
  'touch-size flex h-9 w-full items-center gap-3 rounded-lg px-2.5 text-sm font-medium text-nav-ink',
  'transition-[background-color,color] duration-(--duration-instant)',
  'hover-hover:hover:bg-nav-hover hover-hover:hover:text-ink',
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand',
);

/** Sign out, then the sign-in page -- the header's and the rail's, one way. */
const useSignOut = (before) => {
  const { signOut } = useAuth();
  const navigate = useNavigate();
  return async () => {
    before?.();
    await signOut();
    navigate('/login', { replace: true });
  };
};

/** The header's account: the person's name and position, Change your password and Sign out; Sign in for nobody. */
export function HeaderAccount() {
  const { status, user } = useAuth();
  const location = useLocation();
  const signOut = useSignOut();

  if (status !== 'signed-in') {
    return (
      <Link to={loginPath(location)} className={TOOL_LINK}>
        <LogIn size={18} strokeWidth={1.75} aria-hidden="true" />
        Sign in
      </Link>
    );
  }

  const name = user.displayName || user.username;
  const position = user.position?.name;
  return (
    <>
      <p className="flex h-9 min-w-0 items-center gap-2.5 rounded-full py-0 pl-1 pr-2.5">
        <span aria-hidden="true" className="grid size-7 shrink-0 place-items-center rounded-full bg-brand-soft text-xs font-semibold text-brand-ink">
          {initialsOf(name)}
        </span>
        {/* The name and position across the whole desk, from `xl` as the placeholder's name was; below that, read but
            not shown -- ONE copy of the words, so a screen reader never hears them twice. */}
        <span className="sr-only min-w-0 leading-tight xl:not-sr-only xl:grid">
          <span className="truncate text-[13.5px] font-medium text-ink">{name}</span>
          {position && <span className="truncate text-xs text-ink-muted"><span className="sr-only">, </span>{position}</span>}
        </span>
      </p>
      <Link to="/change-password" className={TOOL_LINK} aria-label="Change your password" title="Change your password">
        <KeyRound size={18} strokeWidth={1.75} aria-hidden="true" />
      </Link>
      <Button variant="ghost" onClick={signOut}>
        <LogOut size={18} strokeWidth={1.75} aria-hidden="true" />
        <span className="max-sm:sr-only">Sign out</span>
      </Button>
    </>
  );
}

/**
 * The rail foot's way in or out. It was "Log out", a link to /login that signed nobody out because nobody was signed
 * in; with sign-in it is Sign out -- the session revoked, then the sign-in page -- or, for nobody, Sign in and back here.
 */
export function RailAccount({ onClick }) {
  const { status } = useAuth();
  const location = useLocation();
  const signOut = useSignOut(onClick);
  const icon = (Icon) => <Icon size={18} strokeWidth={1.75} aria-hidden="true" className="shrink-0 text-nav-icon" />;

  if (status !== 'signed-in') {
    return (
      <Link to={loginPath(location)} className={FOOT_ROW} onClick={onClick}>
        {icon(LogIn)}
        <span className="truncate">Sign in</span>
      </Link>
    );
  }
  return (
    <button type="button" className={FOOT_ROW} onClick={signOut}>
      {icon(LogOut)}
      <span className="truncate">Sign out</span>
    </button>
  );
}
