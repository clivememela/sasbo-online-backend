/**
 * The words of the sign-in contract the screens branch on -- each one the server's (`api-vocabulary.json`, `auth`),
 * held to it by `authVocabulary.parity.test.js`. A code the server stops giving reddens that test instead of leaving a
 * screen waiting for a refusal that never comes.
 *
 * Every other refusal is shown in the server's own sentence, so a new code needs no change here.
 */
export const AUTH_REFUSAL = Object.freeze({
  SIGN_IN_FAILED: 'SIGN_IN_FAILED',
  ACCOUNT_LOCKED: 'ACCOUNT_LOCKED',
  ACCOUNT_INACTIVE: 'ACCOUNT_INACTIVE',
  SIGN_IN_REQUIRED: 'SIGN_IN_REQUIRED',
  SESSION_EXPIRED: 'SESSION_EXPIRED',
  PASSWORD_RULES: 'PASSWORD_RULES',
  PASSWORD_CHANGE_REQUIRED: 'PASSWORD_CHANGE_REQUIRED',
  PERMISSION_REQUIRED: 'PERMISSION_REQUIRED',
  USERNAME_TAKEN: 'USERNAME_TAKEN',
  SCOPE_INVALID: 'SCOPE_INVALID',
  STALE_VERSION: 'STALE_VERSION',
  LAST_ADMINISTRATOR: 'LAST_ADMINISTRATOR',
});

/**
 * `sasbo.signin.mode` (`open` until SASBO's users exist, then `required` -- the owner decides when), and the refusals
 * that end a session: defined in `session.js`, which is in the first load where this file is not.
 */
export { PASSWORD_CHANGE_REQUIRED, SESSION_ENDING, SIGN_IN_MODES } from './session';

/** How far a user sees (permissions.md, "Scope"): everything, some regions, or some areas. */
export const SCOPE_TYPES = Object.freeze(['ALL', 'REGION', 'AREA']);

/** The refusal code of a rejection from the service layer -- `parseApiError` keeps the server's envelope as `body`. */
export const refusalOf = (error) => error?.body?.code ?? null;
