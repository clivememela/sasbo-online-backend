import { nameKey } from './nameKey.js';

/**
 * The branch list, derived from the bank listing the screen already fetched.
 *
 * WHY THIS EXISTS (E3, and E6 behind it)
 * --------------------------------------
 * `BRANCH_OPTIONS` was a hand-assembled constant of fourteen strings. Classified
 * against the real data it was a union of two DIFFERENT columns -- 4 values that
 * are a `branchName`, 7 that are an `area`, and 3 (`Cape Town`, `Rivonia`,
 * `Soweto`) that match neither -- while 18 real areas were never offered at all.
 *
 * The server resolves the bank address with
 *
 *     findByBank_NameIgnoreCaseAndBranchNameIgnoreCase(bankName, branch)
 *
 * which matches on `branchName` ONLY. So ten of the fourteen options could never
 * resolve, and that is what silently killed `autoPopulateBankAddress`: the save
 * succeeded, the address came back empty, and nothing anywhere reported a fault.
 *
 * THE MATCHING RULE IS THE CONTRACT
 * ---------------------------------
 * This module deliberately mirrors the server's lookup: an exact, case-insensitive,
 * trimmed comparison on the bank name, and the branch's `branchName` used verbatim
 * as the option value. It is NOT the fuzzy `includes()` match used to resolve a
 * bank CODE for the job-grade fetch, and that difference is load-bearing -- a fuzzy
 * bank match would offer a branch belonging to a neighbouring bank, the server's
 * exact match would then fail, and we would have rebuilt the very defect this
 * replaces while looking like we had fixed it.
 */

/** The branch name a record carries, under either the entity or the DTO spelling. */
export const branchNameOf = (record) =>
  String(record?.branchName ?? record?.branch ?? '').trim();

/** The bank name a record carries, under either spelling. */
export const bankNameOf = (record) =>
  String(record?.bankName ?? record?.name ?? '').trim();

/**
 * Every branch belonging to `bankName`, de-duplicated and sorted.
 *
 * Returns `[]` for a blank bank name rather than every branch of every bank: a
 * branch is only meaningful once the bank is known, and offering the full union
 * is how the old constant came to exist.
 */
export const branchNamesForBank = (bankListing, bankName) => {
  const wanted = nameKey(bankName);
  if (!wanted) return [];

  const names = new Set();
  (Array.isArray(bankListing) ? bankListing : []).forEach((record) => {
    if (nameKey(bankNameOf(record)) !== wanted) return;
    const branch = branchNameOf(record);
    if (branch) names.add(branch);
  });

  return [...names].sort((a, b) => a.localeCompare(b));
};

/**
 * A NEW BANK, AND WHAT GOES WITH THE OLD ONE -- one rule for capture and the record, whether a person chose the bank
 * or accepted the Workplace control's offer to fill it (month end 01.3; main's intent, e56dbe4). A grade belongs to a
 * bank, so a DIFFERENT bank clears it; a branch the new bank does not offer goes with the old bank -- judged against
 * the bank actually chosen, so the moment a searchable control passes through an empty bank keeps the branch. A
 * branch chosen in the same step stays. The same bank is `next` itself.
 *
 * `branchesFor(bankName)` is the screen's own list of what a bank offers.
 */
export const settleBankChange = (prev, next, branchesFor) => {
  if (next.bankName === prev.bankName) return next;
  const settled = { ...next, jobGrade: '' };
  if (next.bankName && prev.branch && next.branch === prev.branch && !branchesFor(next.bankName).includes(prev.branch)) {
    settled.branch = '';
  }
  return settled;
};

/**
 * The listing record for one bank+branch pair, under the same rule the server uses.
 * This is the source for every branch-derived field in `04.2`.
 */
export const findBranchRecord = (bankListing, bankName, branch) => {
  const wantedBank = nameKey(bankName);
  const wantedBranch = nameKey(branch);
  if (!wantedBank || !wantedBranch) return null;

  return (Array.isArray(bankListing) ? bankListing : []).find(
    (record) =>
      nameKey(bankNameOf(record)) === wantedBank
      && nameKey(branchNameOf(record)) === wantedBranch,
  ) ?? null;
};

/**
 * The branch's code — the source for "IBT No." (E4, 04.2).
 *
 * 🔑 The spec asks for the IBT number to be "auto-generated according to the
 * selected branch", and it is not a generation at all: the branch record already
 * carries the number, on `branchCode`. 66 distinct values across the seeded
 * branches. It was a free-text box that an administrator typed by hand, from a
 * value the screen was already holding in state.
 *
 * `universalBranchCode` is accepted as a second spelling because the bank LISTING
 * uses it where the branch entity uses `branchCode`, and this screen fetches both.
 */
export const branchCodeOf = (record) =>
  String(record?.branchCode ?? record?.universalBranchCode ?? record?.universal_branch_code ?? '').trim();

/** A location that may be a nested `{name}` object, a flat `…Name` string, or absent. */
const placeName = (nested, flat) =>
  String(nested?.name ?? (typeof nested === 'string' ? nested : '') ?? '').trim()
  || String(flat ?? '').trim();

/**
 * A branch's physical address as one line — the SAME formatting the server uses.
 *
 * 🔑 IT MUST MATCH `MemberServiceImpl.formatPhysicalAddress` PART FOR PART, in the
 * same order and with the same separator:
 *
 *     streetNumber, streetName, suburb, city, province, postalCode
 *
 * The server writes this field on save whether or not the browser filled it in.
 * If the two formatters disagree, the address the user approved on screen is
 * silently replaced by a differently-punctuated one on save — a diff nobody reads
 * and everybody distrusts once they notice. Blank parts are dropped rather than
 * leaving ", ," gaps, which is also what the server does.
 *
 * ⚠️ THREE SHAPES REACH THIS FUNCTION, and that is not defensive programming — it
 * is measured. `GET /api/banks/branches` returns the ENTITY, not `BankBranchDto`,
 * so `physicalAddress` arrives as an OBJECT whose suburb, city and province are
 * themselves `{id, name}` objects. `BankBranchDto` collapses the same field to a
 * STRING through its own `FlexibleStringDeserializer`. And the client's
 * `src/data/banks.json` holds it as a plain string again — a third shape, in this
 * repository, differing from the server's copy of the same filename.
 *
 * So the reader accepts all three rather than picking one and being right about a
 * third of the traffic. 📌 The underlying problem — one endpoint serving entities
 * while its sibling serves a DTO with a different contract for the same field — is
 * a finding for `RF`, not something a formatter should be papering over forever.
 *
 * 04.1 surfaces this. It is the "visible" half of a derivation that has always
 * existed and that the person capturing the member has never been shown.
 */
export const formatBranchAddress = (record) => {
  const address = record?.physicalAddress;
  if (!address) return '';

  // The DTO shape, and the client fixture's: already one line, already formatted.
  if (typeof address === 'string') return address.trim();

  return [
    address.streetNumber,
    address.streetName,
    placeName(address.suburb, address.suburbName),
    placeName(address.city, address.cityName),
    placeName(address.province, address.provinceName),
    address.postalCode,
  ]
    .map((part) => String(part ?? '').trim())
    .filter(Boolean)
    .join(', ');
};
