import { branchNameOf, formatBranchAddress } from '../../utils/bankBranches.js';

/**
 * 🔴 A29 -- THE RECORD IS NOT THE FORM, AND THE BRANCH DIALOG USED ONE AS THE OTHER.
 *
 * `GET /api/banks/branches` answers the ENTITY: the branch's name as `branchName`, the physical address as an OBJECT
 * (street, suburb `{id, name}`, city, province, postal code), the counts as numbers. The dialog seeded its form with
 * that record as it came. So *Edit* handed the address object to a text field -- `AddressAutocomplete` called `.trim()`
 * on it and the screen fell to "This page could not be displayed." -- and the Branch field, reading `branch`, was
 * empty for every branch (the read-only view printed "N/A" for it), so a save that got that far was refused for a
 * missing branch.
 *
 * So the record is converted once at each edge: IN, every field becomes the string its control holds; OUT, the form
 * becomes the keys `BankBranchDto` declares. The address is one line, formatted by `formatBranchAddress` -- the
 * server's own order and separator, so the line shown is the line the member's bank address is built from.
 */

/** The fields the dialog edits, which are also the keys `BankBranchDto` reads. */
export const BRANCH_FORM_KEYS = Object.freeze([
  'bankName', 'bankAbbreviation', 'branch', 'branchCode', 'area', 'randomAccessNumber',
  'phoneNumber', 'fax', 'email', 'managerName', 'physicalAddress', 'postalAddress',
  'provinceCommitteeCode', 'provinceCommitteeName', 'sasboAreaCode', 'bankDepartmentCode',
  'tableCode', 'subsValueBranch', 'branchStaffComplement', 'branchMemberComplement',
]);

/** The four the DTO holds as numbers; the rest are text. */
const NUMBER_KEYS = new Set(['tableCode', 'subsValueBranch', 'branchStaffComplement', 'branchMemberComplement']);

export const EMPTY_BRANCH_FORM = Object.freeze(Object.fromEntries(BRANCH_FORM_KEYS.map((key) => [key, ''])));

const text = (value) => (value === null || value === undefined || typeof value === 'object' ? '' : String(value));

/** A record from the API (or nothing, for a new branch) as the dialog's form: every field a string. */
export const toBranchForm = (record) => {
  if (!record) return { ...EMPTY_BRANCH_FORM };
  const form = Object.fromEntries(BRANCH_FORM_KEYS.map((key) => [key, text(record[key])]));
  form.branch = branchNameOf(record);
  form.physicalAddress = formatBranchAddress(record);
  return form;
};

/**
 * The form as `BankBranchDto` reads it: text trimmed, a blank as `null` (the update writes every field it is given,
 * so a blank clears the field either way), numbers as numbers.
 *
 * 🔑 THE PHYSICAL ADDRESS GOES ONLY WHEN IT WAS CHANGED. The server keeps the stored address for a request that names
 * none, and replaces it with a single street line for one that names any -- its suburb, city and province, which a
 * line of text cannot carry back, would go with every save of an unrelated field. `opened` is the form as the record
 * opened it; without one (a new branch) a blank address is simply not sent.
 */
export const toBranchPayload = (form, opened) => {
  const payload = {};
  for (const key of BRANCH_FORM_KEYS) {
    const value = String(form?.[key] ?? '').trim();
    if (NUMBER_KEYS.has(key)) payload[key] = value === '' ? null : Number(value);
    else payload[key] = value === '' ? null : value;
  }
  const address = String(form?.physicalAddress ?? '').trim();
  if (opened ? address === String(opened.physicalAddress ?? '').trim() : address === '') {
    delete payload.physicalAddress;
  } else {
    payload.physicalAddress = address;
  }
  return payload;
};

/**
 * The record a saved edit leaves behind, in the API's shape, for the list behind the dialog. The server answers the
 * saved entity, and that is used as it came; a deployment that answers an empty 200 (A26) leaves the service's
 * optimistic `{...payload, id}`, which is folded onto the record that was opened instead.
 */
export const toBranchRecord = (answer, opened, payload) => {
  if (answer && typeof answer === 'object' && 'branchName' in answer) return answer;
  return {
    ...opened,
    ...payload,
    id: opened?.id,
    branchName: payload.branch,
    physicalAddress: payload.physicalAddress ?? opened?.physicalAddress,
  };
};
