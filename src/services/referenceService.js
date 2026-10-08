import { apiUrl } from './api';
import { requestJson } from './parseApiError';

/**
 * SASBO's lists -- month end 01.1. One typed route a list (MD41): no name of a list travels in an address, so each
 * read is a method of its own with its path written at its call site, where the API-contract scan can see it.
 *
 * Every answer is `{ list, printedOn, confirmedOn, reason, rows }`: the rows in the server's order, the day the
 * report they were read from was printed, the day SASBO confirmed them (empty until she has, MD28), and -- where no
 * row is -- why, in one of the four words `LIST_EMPTY_REASON` gives a sentence to.
 *
 * Every method goes through `requestJson`, the one error path (conventions §16). A list is asked for when the control
 * that shows it is opened, and kept for the visit -- never at first paint (MD69).
 *
 * 🔑 EACH READ IS ALSO ITS OWN NAMED EXPORT, so a screen that needs two lists ships two reads and not ten: an object
 * literal's unused methods are never shaken out of a bundle, and the bundle's ceiling is not raised to fit a change.
 * The month's figures (`02.3`, on Month end since 5 Oct) read only the areas, the regions and the lists' status; the member's Workplace control
 * (`01.3`) only the employers and the status; 03.3's Change status dialog only the deletion and the suspension codes;
 * the record's Structures tab only the committees -- by name since 2 Oct, where it had imported the whole object.
 * `referenceService` is the same reads, gathered, for everything that iterates the service layer -- the object holds
 * these very functions, never copies of them.
 */
export const getRegions = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/regions'), { credentials: 'include', signal }));

export const getAreas = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/areas'), { credentials: 'include', signal }));

export const getEmployerGroups = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/employer-groups'), { credentials: 'include', signal }));

/** SASBO's employers (01.1): `{ list, printedOn, confirmedOn, reason, rows: [{ id, name, group, core, tableCodes }] }`. */
export const getEmployers = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/employers'), { credentials: 'include', signal }));

export const getTableCodes = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/table-codes'), { credentials: 'include', signal }));

export const getCommitteeProvinces = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/committee-provinces'), { credentials: 'include', signal }));

export const getCommittees = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/committees'), { credentials: 'include', signal }));

/**
 * SASBO's deletion codes BY THEIR NUMBERS (03.3): `{ …, rows: [{ code, name, under }] }` -- each numbered code in order,
 * then the reasons SASBO keeps under it (`code: null`, `under: 2`). A change sends the number, never a row's id (RC7).
 */
export const getDeletionCodes = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/deletion-codes'), { credentials: 'include', signal }));

/** SASBO's suspension codes (01.1): `{ …, rows: [{ code, name }] }`. */
export const getSuspensionCodes = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/suspension-codes'), { credentials: 'include', signal }));

/** How many rows each list holds here, and why none where none is (ME-18, ME-19) -- the workplaces' among them. */
export const getStatus = () =>
  requestJson((signal) => fetch(apiUrl('/api/reference/status'), { credentials: 'include', signal }));

export const referenceService = {
  getRegions,
  getAreas,
  getEmployerGroups,
  getEmployers,
  getTableCodes,
  getCommitteeProvinces,
  getCommittees,
  getDeletionCodes,
  getSuspensionCodes,
  getStatus,
};
