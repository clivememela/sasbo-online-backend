import { Link, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import { PERMISSION_LABELS } from './permissions';
import { loginPath } from './next';

/**
 * Shows its children to a user who may use them, and a short sentence to anyone else.
 *
 * A screen that needs a permission is still reachable -- its menu entry stays -- so a user who cannot use it is told
 * what is needed, in words, instead of meeting a blank page or a refusal from the server. Nothing inside is mounted
 * for a user who may not use it, so a refused screen asks the server for nothing.
 *
 * Every October screen wraps itself in this (permissions.md): `permission`, `children` and `fallback` are its API, and
 * they do not change.
 */
const RequirePermission = ({ permission, children, fallback }) => {
  const { status, can } = useAuth();
  const location = useLocation();

  if (can(permission)) return children;
  if (fallback !== undefined) return fallback;

  const what = PERMISSION_LABELS[permission] ?? 'use this';

  return (
    <div role="status" className="rounded-md border border-line bg-surface p-4 text-sm text-ink">
      {status === 'signed-in' ? (
        <p>You need permission to {what}. Ask your administrator if you should have it.</p>
      ) : (
        <p>
          Sign in to {what}.{' '}
          <Link className="font-medium underline" to={loginPath(location)}>Sign in</Link>
        </p>
      )}
    </div>
  );
};

export default RequirePermission;
