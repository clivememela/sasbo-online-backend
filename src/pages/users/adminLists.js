import { getPermissions, getPositions, getRoles } from '../../services/adminService';
import { byOrdinal } from './usersRules';

/**
 * The lists Maintenance's user screens choose from, each a loader for `useReferenceData` (stable, at module scope).
 * An answer that is not a list is a failure the control shows -- never an empty list (MV-36): the route smoke answers
 * every read with `[]`, and an older server with something else entirely.
 */
const unavailable = (what) => {
  throw new Error(`The ${what} are not available from the server.`);
};

export const positionsList = () => getPositions().then((rows) => (Array.isArray(rows) ? byOrdinal(rows) : unavailable('positions')));
export const rolesList = () => getRoles().then((rows) => (Array.isArray(rows) ? rows : unavailable('roles')));
export const permissionsList = () => getPermissions().then((rows) => (Array.isArray(rows) ? rows : unavailable('permissions')));
