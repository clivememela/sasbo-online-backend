/**
 * The Location Visit form as a VISIT DRAFT (October build, 5 Oct Show & Tell: "the visit forms saving and
 * submitting") -- what a draft sends from the form, and the form a saved draft fills back.
 *
 * A draft keeps what a visit keeps (A.1): the workplace, by its R/A number; the day; who visited; when they came and
 * went; and what the organiser found. Everything else on the form -- the branch's manager and representatives, their
 * telephones and e-mails, the signatures -- is a person's details or the paper's, printed with the form and never sent.
 *
 * The R/A number is needed to save; the day only to submit, so a draft can be started in the car park and finished at
 * the desk. Each field goes under the server's name (`DRAFT_FIELDS`), so a refusal that names one is said at the field.
 */

/**
 * A draft's status, as the server spells it (`WorkplaceVisitDraftRepository.DRAFT` / `SUBMITTED`): changed until it
 * is submitted, then SASBO's record and read-only. Named once here -- it is not a member's status, which shares the
 * field's name and nothing else.
 */
export const DRAFT_STATUS = Object.freeze({ DRAFT: 'DRAFT', SUBMITTED: 'SUBMITTED' });

/** Each field a draft keeps: the server's name, and the form's field it comes from. */
export const DRAFT_FIELDS = Object.freeze({
  raNumber: 'raNumber',
  visitedOn: 'dateOfVisit',
  timeIn: 'timeIn',
  timeOut: 'timeOut',
  organiser: 'organiserName',
  complementFound: 'complement',
  membershipFound: 'membership',
  nonMembers: 'noOfNonMembers',
  recruited: 'noOfNewMembersRecruited',
  nonMembersInterviewed: 'noOfNonMembersInterviewed',
});

/** The counts a draft carries -- whole numbers, or nothing. */
const COUNTS = new Set(['complementFound', 'membershipFound', 'nonMembers', 'recruited', 'nonMembersInterviewed']);

/**
 * The draft a filled form saves, and the fields it cannot be sent with -- said at each field before anything is sent.
 * What the server refuses besides (a day still to come, a time out before the time in, a count past its ceiling) it
 * says itself, at its field.
 *
 * @param {Record<string, string>} form the form's fields
 * @param {{ submitting?: boolean }} [options] submitting needs the day as well
 * @returns {{ draft: object, errors: Record<string, string> }}
 */
export function draftOf(form, { submitting = false } = {}) {
  const draft = {};
  const errors = {};
  const ra = String(form.raNumber ?? '').trim();
  if (!ra) errors.raNumber = 'The R/A number says which workplace was visited.';
  else if (!/^\d{1,9}$/.test(ra) || Number(ra) < 1) errors.raNumber = 'An R/A number is a number of up to nine digits.';
  else draft.raNumber = Number(ra);
  if (submitting && !String(form.dateOfVisit ?? '').trim()) errors.dateOfVisit = 'Choose the day of the visit.';
  for (const [key, field] of Object.entries(DRAFT_FIELDS)) {
    if (key === 'raNumber') continue;
    const value = String(form[field] ?? '').trim();
    if (!value) continue;
    if (!COUNTS.has(key)) draft[key] = value;
    else if (/^\d+$/.test(value)) draft[key] = Number(value);
    else errors[field] = 'A whole number, or leave it empty.';
  }
  return { draft, errors };
}

/**
 * The form's fields a saved draft fills -- every field a draft keeps, blank where it kept nothing, so a draft opened
 * over another leaves none of the other's values behind. The workplace's name goes in the branch's box.
 */
export function formFieldsOf(draft) {
  const fields = {};
  for (const [key, field] of Object.entries(DRAFT_FIELDS)) {
    fields[field] = draft?.[key] == null ? '' : String(draft[key]);
  }
  fields.branchDepartment = draft?.workplaceName ?? '';
  return fields;
}
