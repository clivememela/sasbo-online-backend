/**
 * A member's name, joined from whichever parts are present, in the two shapes the application uses. Each returns `''`
 * when no part is present, so every caller keeps its own fallback ("That member", "—", "this record"), and each accepts
 * a missing member.
 *
 * Extracted in P6's refactor pass from the sites P6 wrote. Older sites with the same shape are listed in findings.md
 * rather than edited here.
 */

/** Title, initials and surname -- how a notice names a member ("Mr A Nkosi was added to …"). */
export function formalName(member) {
  return [member?.title, member?.initials, member?.surname].filter(Boolean).join(' ');
}

/** First names and surname -- how the record and its card name a member. */
export function personName(member) {
  return [member?.firstNames, member?.surname].filter(Boolean).join(' ');
}
