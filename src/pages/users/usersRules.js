import { AUTH_REFUSAL, refusalOf } from '../../auth/authVocabulary';
import { instantParts } from '../../config/monthEnd';
import { messageOf, validationErrorsOf, writeFailure, writeMayHaveLanded } from '../../services/parseApiError';

/** An absent value, as every screen of this application marks one. */
export const NONE = '—';

const WHEN = new Intl.DateTimeFormat('en-ZA', {
  timeZone: 'Africa/Johannesburg', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
});

/** An instant in Johannesburg, as a table reads it -- `05 Oct 2026, 09:30` (en-ZA's own day) -- or the absence mark. */
export const whenLabel = (iso) => {
  const part = instantParts(iso, WHEN);
  return part ? `${part.day} ${part.month} ${part.year}, ${part.hour}:${part.minute}` : NONE;
};

/** Where a user stands: closed, locked (until when), waiting on a temporary password, or able to sign in. */
export const standingLabel = (user) => {
  if (user.active === false) return 'Closed';
  if (user.locked) return user.lockedUntil ? `Locked until ${whenLabel(user.lockedUntil)}` : 'Locked';
  if (user.mustChangePassword) return 'Temporary password';
  return 'Active';
};

/**
 * The rules of Maintenance's users, positions, roles and the record of who did what -- each a pure function, tested by
 * name in `usersRules.test.js`. The screens hold state; what they say and what they send is decided here.
 */

/** Longest a typed value may be -- the server's own limits (CreateUserRequest, PositionRequest, RoleRequest). */
export const LIMITS = Object.freeze({ username: 60, displayName: 80, email: 254, code: 40, name: 80, search: 100 });

/** A position or role code as the server takes it: 2 to 40 capitals, digits and underscores, starting with a letter. */
export const CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,39}$/;

/** A username as the server takes it: 3 to 60 letters, digits, dots, dashes, underscores or @, starting alphanumeric. */
export const USERNAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._@-]{2,59}$/;

/** The words for a scope (permissions.md, "Scope") -- "All of SASBO", "Regions: NORTH, SOUTH", "Area: A". */
export const scopeLabel = (scope) => {
  const codes = Array.isArray(scope?.codes) ? scope.codes : [];
  if (scope?.type === 'REGION') return `${codes.length === 1 ? 'Region' : 'Regions'}: ${codes.join(', ')}`;
  if (scope?.type === 'AREA') return `${codes.length === 1 ? 'Area' : 'Areas'}: ${codes.join(', ')}`;
  return 'All of SASBO';
};

/** An action as a person reads it: `SIGN_IN_FAILED` is "Sign in failed". A word the browser does not know still reads. */
export const actionLabel = (action) => {
  const words = String(action ?? '').trim().toLowerCase().replaceAll('_', ' ');
  return words ? words[0].toUpperCase() + words.slice(1) : '—';
};

/** Who did it, as the record has them: the name and the username, or whichever is there; `system` for the seed. */
export const whoLabel = ({ displayName, username } = {}) => {
  if (displayName && username) return `${displayName} (${username})`;
  return displayName || username || 'System';
};

/** An empty form for a new user: everything blank, the whole of SASBO, and the position's own role. */
export const NEW_USER = Object.freeze({
  displayName: '', username: '', email: '', positionCode: '', roleCode: '', scopeType: 'ALL', codes: [], active: true,
});

/** A user's record as the edit form holds it. */
export const formOf = (user) => ({
  displayName: user.displayName ?? '',
  username: user.username ?? '',
  email: user.email ?? '',
  positionCode: user.position?.code ?? '',
  roleCode: user.role?.code ?? '',
  scopeType: user.scope?.type ?? 'ALL',
  codes: user.scope?.type === 'ALL' ? [] : [...(user.scope?.codes ?? [])],
  active: user.active !== false,
});

const looksLikeEmail = (text) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text);

/**
 * Every reason the form refuses before it asks, by field. The scope's codes are the 5 Oct ask ("an administrator
 * creating users with a position and an area"): a regional or area user with nothing chosen would see nothing at all.
 */
export const userProblems = (form, { creating }) => {
  const problems = {};
  if (!form.displayName.trim()) problems.displayName = 'Enter the name this user is shown by.';
  if (creating && !form.username.trim()) problems.username = 'Enter a username.';
  else if (creating && !USERNAME_PATTERN.test(form.username.trim())) {
    problems.username = 'A username is 3 to 60 letters, digits, dots, dashes, underscores or @, with no spaces, starting with a letter or a digit.';
  }
  if (form.email.trim() && !looksLikeEmail(form.email.trim())) problems.email = 'Enter an e-mail address like name@sasbo.co.za, or leave it empty.';
  if (!form.positionCode) problems.positionCode = 'Choose the position this user holds.';
  if (form.scopeType === 'REGION' && form.codes.length === 0) problems.scope = 'Choose at least one region this user sees.';
  if (form.scopeType === 'AREA' && form.codes.length === 0) problems.scope = 'Choose at least one area this user sees.';
  return problems;
};

/** What a create or a change sends (contracts/signin.md). A blank role is the position's own; ALL carries no codes. */
export const userBody = (form, { creating, version }) => {
  const body = {
    displayName: form.displayName.trim(),
    email: form.email.trim() || null,
    positionCode: form.positionCode,
    roleCode: form.roleCode || null,
    scope: { type: form.scopeType, codes: form.scopeType === 'ALL' ? [] : form.codes },
  };
  return creating ? { username: form.username.trim(), ...body } : { ...body, active: form.active, version };
};

/** The server's field names, mapped to the form's: `scope`, `scope.type`, `scope.codes[2]` all belong to the scope. */
const fieldOf = (field) => (field.startsWith('scope') ? 'scope' : field);

/**
 * A refused create or change, as the dialog shows it: `fields` for what belongs on a field, `notice` for the rest, and
 * `stale` when someone else saved first. Every sentence names what to do next.
 */
export const userRefusal = (error) => {
  const code = refusalOf(error);
  const said = messageOf(error);
  if (code === AUTH_REFUSAL.USERNAME_TAKEN) return { fields: { username: 'That username is taken. Choose another.' } };
  if (code === AUTH_REFUSAL.SCOPE_INVALID) {
    return { fields: { scope: error.body.message?.trim() || 'Those regions or areas are not on SASBO’s lists. Choose them again.' } };
  }
  if (code === AUTH_REFUSAL.STALE_VERSION) {
    return { stale: true, notice: 'Someone else changed this user while you were editing. Reload the user to see their change, then make yours again.' };
  }
  if (code === AUTH_REFUSAL.LAST_ADMINISTRATOR) {
    return { notice: 'This would leave SASBO with no active user who can manage users. Give another user that permission first, then make this change.' };
  }
  const fields = Object.fromEntries(Object.entries(validationErrorsOf(error)).map(([field, text]) => [fieldOf(field), text]));
  return Object.keys(fields).length ? { fields, notice: said } : { notice: said };
};

/**
 * A failed save of a position or a role, as its dialog shows it. A refusal (4xx) is the server saying no, in its own
 * sentence and on the fields it names; anything else may have landed and is said as unconfirmed (SCREEN-CONTRACT §3).
 */
export const editorFailure = (error, what, where) => (writeMayHaveLanded(error)
  ? { fields: {}, notice: writeFailure(error, what, where) }
  : { fields: validationErrorsOf(error), notice: messageOf(error) });

/**
 * A position as its dialog holds it, and what a save sends (contracts/signin.md). A change carries the `version` it was
 * read at, so the server refuses one made over somebody else's (`STALE_VERSION`, said in its own words).
 */
export const positionForm = (position) => ({
  code: position?.code ?? '',
  name: position?.name ?? '',
  defaultRoleCode: position?.defaultRoleCode ?? '',
  ordinal: position?.ordinal == null ? '' : String(position.ordinal),
  active: position ? position.active !== false : true,
  version: position?.version ?? null,
});

export const positionProblems = (form, { creating }) => {
  const problems = {};
  if (!form.name.trim()) problems.name = 'Enter the position’s name as SASBO writes it.';
  if (creating && !CODE_PATTERN.test(form.code.trim())) {
    problems.code = 'A code is capital letters, digits and underscores, starting with a letter, for example REGIONAL_SECRETARY.';
  }
  if (!form.defaultRoleCode) problems.defaultRoleCode = 'Choose the role a user in this position gets.';
  if (form.ordinal.trim() && !/^\d{1,4}$/.test(form.ordinal.trim())) problems.ordinal = 'The order is a whole number, 0 to 9999.';
  return problems;
};

export const positionBody = (form) => ({
  code: form.code.trim(),
  name: form.name.trim(),
  defaultRoleCode: form.defaultRoleCode,
  active: form.active,
  ordinal: form.ordinal.trim() ? Number(form.ordinal.trim()) : 0,
  ...(form.version != null && { version: form.version }),
});

/**
 * A role as its dialog holds it, and what a save sends -- with its `version`, as a position's. A `fixed` role (the
 * System Administrator's) holds every permission and keeps them: only its name changes (the server's `ROLE_FIXED`).
 */
export const roleForm = (role) => ({
  code: role?.code ?? '',
  name: role?.name ?? '',
  permissions: [...(role?.permissions ?? [])],
  version: role?.version ?? null,
});

export const roleProblems = (form, { creating }) => {
  const problems = {};
  if (!form.name.trim()) problems.name = 'Enter the role’s name.';
  if (creating && !CODE_PATTERN.test(form.code.trim())) {
    problems.code = 'A code is capital letters, digits and underscores, starting with a letter, for example ORGANISER.';
  }
  return problems;
};

export const roleBody = (form) => ({
  code: form.code.trim(),
  name: form.name.trim(),
  permissions: form.permissions,
  ...(form.version != null && { version: form.version }),
});

/** Positions in the order SASBO lists them (`ordinal`), then by name. */
export const byOrdinal = (positions) =>
  [...positions].sort((a, b) => (a.ordinal ?? 0) - (b.ordinal ?? 0) || String(a.name).localeCompare(String(b.name)));
