import { AUTH_REFUSAL, refusalOf } from '../../auth/authVocabulary';
import { messageOf } from '../../services/parseApiError';

/**
 * What the sign-in and change-password screens say, from the server's refusal codes (signin.md) -- one sentence a
 * SASBO administrator understands for each, and the server's own sentence for anything else.
 *
 * 🔴 NO SENTENCE TELLS AN UNKNOWN USERNAME FROM A WRONG PASSWORD. The server answers both `SIGN_IN_FAILED` on purpose
 * (no user enumeration), and the words here keep that promise: they never say which of the two was wrong, and never
 * repeat what was typed.
 */
const SIGN_IN_SENTENCE = {
  [AUTH_REFUSAL.SIGN_IN_FAILED]: 'The username or the password is not right. Check both and try again.',
  [AUTH_REFUSAL.ACCOUNT_LOCKED]:
    'This account is locked for 15 minutes, after five sign-ins that did not succeed. Try again after that, or ask your administrator to reset your password.',
  [AUTH_REFUSAL.ACCOUNT_INACTIVE]: 'This account is closed. Ask your administrator if you should still have access.',
};

/** Why a sign-in was refused, in words -- or, for a failure that is not a refusal, what happened to the request. */
export const signInFailure = (error) => SIGN_IN_SENTENCE[refusalOf(error)] ?? messageOf(error);

/** The password rules (signin.md, "Mechanism"), as the change-password screen lists them. */
export const PASSWORD_MIN = 10;
export const PASSWORD_RULES = Object.freeze([
  `At least ${PASSWORD_MIN} characters`,
  'Not your username',
  'Not a common password, such as a word followed by a number that everybody uses',
]);

/**
 * The rules the browser can check before it asks: the length and the username. The common-password list is the
 * server's, and its refusal (`PASSWORD_RULES`) is explained by `passwordFailure`.
 */
export const passwordProblem = (password, username) => {
  if (password.length < PASSWORD_MIN) return `Use at least ${PASSWORD_MIN} characters.`;
  if (username && password.trim().toLowerCase() === username.trim().toLowerCase()) {
    return 'Choose a password that is not your username.';
  }
  return null;
};

/** Why a password change was refused: the rules, or the current password; anything else in the server's words. */
export const passwordFailure = (error) => {
  const code = refusalOf(error);
  if (code === AUTH_REFUSAL.PASSWORD_RULES) {
    // The server's sentence names the rule it refused on (the common-password list is only on the server).
    const said = typeof error?.body?.message === 'string' ? error.body.message.trim() : '';
    return `That new password does not meet the rules below.${said ? ` ${said}` : ''}`;
  }
  if (code === AUTH_REFUSAL.SIGN_IN_FAILED) return 'The current password is not right. Check it and try again.';
  return messageOf(error);
};
