/**
 * The R/A numbers of the organisers' batch print, read from what was typed or pasted all at once (October build, 5 Oct
 * Show & Tell: "if I'm going to have these 20 Rs for the month, let me put all 20 numbers in at once, click print").
 *
 * Separated by spaces, commas, semicolons or new lines -- the shapes a list copied from a spreadsheet, an e-mail or a
 * note arrives in. An R/A number is a number of up to nine digits, as the server's column is; leading noughts are
 * dropped, because `0123` and `123` are the same workplace.
 *
 * Answers what it read, so the screen can say all of it before anything is asked:
 *   `numbers`    -- each number once, in the order typed: what is printed;
 *   `repeated`   -- the numbers typed more than once (each printed once);
 *   `notNumbers` -- what was typed that is not an R/A number, each once;
 *   `tooMany`    -- more than {@link MAX_RA_NUMBERS} different numbers, which one print does not take.
 */
export const MAX_RA_NUMBERS = 50;

const SEPARATORS = /[\s,;]+/;
const RA_NUMBER = /^\d{1,9}$/;

export function parseRaNumbers(text) {
  // Sets, kept in the order first typed: a pasted column of thousands is read in one pass, never searched per token.
  const numbers = new Set();
  const repeated = new Set();
  const notNumbers = new Set();
  for (const token of String(text ?? '').split(SEPARATORS)) {
    if (!token) continue;
    if (!RA_NUMBER.test(token) || Number(token) < 1) {
      notNumbers.add(token);
      continue;
    }
    const number = String(Number(token));
    if (numbers.has(number)) repeated.add(number);
    else numbers.add(number);
  }
  return {
    numbers: [...numbers], repeated: [...repeated], notNumbers: [...notNumbers], tooMany: numbers.size > MAX_RA_NUMBERS,
  };
}

/**
 * The listings grouped by bank, for "separate them by bank ... if it's a new bank, start it on a new page" (5 Oct):
 * each bank once, in the order its first branch was typed, its branches in the order typed. A workplace's bank is the
 * employer its table code proves, or -- where the files prove none -- the employer as the branch list prints it, so a
 * branch is never left without a bank to be printed under. PROVISIONAL: "bank" is read as the EMPLOYER, not its group
 * (one "bank" a sheet) -- which one SASBO meant is their question, asked in the stream's report.
 *
 * @param {Array<{ workplace: object }>} listings in the order the numbers were typed
 * @returns {Array<{ key: string, bank: string, listings: Array<object> }>}
 */
export function groupByBank(listings) {
  const groups = new Map();
  for (const listing of listings ?? []) {
    const w = listing?.workplace ?? {};
    const key = w.employerId != null ? `employer:${w.employerId}` : `printed:${w.employerPrinted ?? ''}`;
    if (!groups.has(key)) {
      groups.set(key, { key, bank: w.employer ?? w.employerPrinted ?? 'Bank not on record', listings: [] });
    }
    groups.get(key).listings.push(listing);
  }
  return [...groups.values()];
}
