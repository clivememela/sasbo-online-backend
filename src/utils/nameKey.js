/**
 * Comparing a NAME the way the server compares it.
 *
 * <h2>Why this is one function and not three</h2>
 * Found by the arc's closing refactor. This exact line had been written three
 * times, in three phases, by the same hand:
 *
 *   02.3  `bankBranches.js`      — matching a bank + branch pair
 *   02.5  `CaptureMember.jsx`    — resolving a bank CODE for the job-grade fetch
 *   04.1  `bankSubscription.js`  — finding a bank's subscription row
 *
 * Every reference lookup on this screen resolves a name the browser holds against
 * a row the server stores, and Spring Data's `findByNameIgnoreCase` is
 * case-insensitive. Trimming is ours: the values arrive from JSON seed files and
 * from a combobox, and both carry stray whitespace.
 *
 * 🔑 IT NORMALISES; IT DOES NOT DECIDE THE MATCH. That distinction is load-bearing
 * and it is why this module is one line rather than a `matches()` helper. Two of
 * the three call sites compare for EXACT equality, mirroring the server; the third
 * deliberately uses `includes()`, because it is resolving a bank code from a
 * fuzzily-named bank and the server is not involved. `bankBranches.js` records
 * what happens if those two rules are confused — a fuzzy bank match offers a
 * branch belonging to a neighbouring bank, the server's exact match then fails,
 * and the defect looks exactly like the one that was just fixed.
 *
 * So the SHARED part is what "the same name" means. The rule about what counts as
 * a match stays at each call site, where its reason is written down.
 */
export const nameKey = (value) => String(value ?? '').trim().toLowerCase()
