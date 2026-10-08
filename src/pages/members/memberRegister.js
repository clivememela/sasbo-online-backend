/**
 * THE MEMBER REGISTER, A PAGE AT A TIME (register row A10) -- the rules the screen reads its page by, written once.
 *
 * The register asks the server for one page (`POST /api/members/register/search`) instead of loading every member. What
 * it asks is kept in the ADDRESS BAR -- `?q=&page=&size=&sort=&status=&bank=&branch=` -- so a refresh, a link or the
 * Back button returns to the same place. The criteria behind "More criteria" (an identity, account or cellphone number,
 * an e-mail address...) are a member's details and stay out of it.
 *
 * 🔒 A WHOLE IDENTITY NUMBER NEVER GOES IN THE ADDRESS BAR, which is kept in the browser's history and sent to the host
 * on a refresh. A search for one is kept in the history entry's own state instead: Back and a refresh still find it.
 *
 * The page sizes and the orders are the server's (`api-vocabulary.json` -> `monthEnd.registerPageMaxSize`,
 * `registerSorts`), held to it by `memberRegister.test.js`.
 */

/** The sizes a page can be. SASBO has not said how many a page should show (REG-25); the screen offers three. */
export const REGISTER_PAGE_SIZES = Object.freeze([25, 50, 100]);
export const REGISTER_DEFAULT_PAGE_SIZE = 50;

/** The orders the register is read in -- each a column of the register, by its name on the wire. */
export const REGISTER_SORTS = Object.freeze(['surname', 'membershipNumber', 'bank', 'branch', 'status']);

/** The register's own order: by surname. SASBO's choice is REG-26, unanswered. */
export const REGISTER_DEFAULT_SORT = Object.freeze({ key: 'surname', direction: 'asc' });

/** How long typing must pause before the search is asked -- `useDebounce`'s own. */
export const SEARCH_PAUSE_MS = 400;

/** The search asks from the second character, as the workplaces' does: one letter matches too much to be a question. */
export const SEARCH_MIN = 2;

/** The longest text a criterion takes; the server refuses a longer one (`REQUEST_MALFORMED`). */
export const TERM_MAX = 100;

/** The address bar's names for the view. */
const PARAM = Object.freeze({ q: 'q', page: 'page', size: 'size', sort: 'sort', status: 'status', bank: 'bank', branch: 'branch' });

/** The history entry's own state key for a search that may not go in the address bar. */
export const PRIVATE_QUERY = 'registerQuery';

/** What the search box asks for: its text trimmed, nothing below two characters, nothing past the ceiling. */
export const searchTerm = (text) => {
  const trimmed = String(text ?? '').trim();
  return trimmed.length >= SEARCH_MIN ? trimmed.slice(0, TERM_MAX) : '';
};

/** A South African identity number, whole: thirteen digits. */
export const isWholeIdentityNumber = (term) => /^\d{13}$/.test(String(term ?? ''));

const text = (value) => {
  const trimmed = String(value ?? '').trim();
  return trimmed && trimmed.length <= TERM_MAX ? trimmed : '';
};

/** `surname` or `surname,desc` -- anything else is the register's own order. */
export const readSort = (value) => {
  const [key, direction = 'asc'] = String(value ?? '').split(',');
  return REGISTER_SORTS.includes(key) && (direction === 'asc' || direction === 'desc')
    ? { key, direction }
    : REGISTER_DEFAULT_SORT;
};

export const sortParam = (sort) => `${sort.key},${sort.direction}`;

/** The order a heading's press asks for: its column either way round, starting from A to Z. */
export const nextSort = (current, key) =>
  current.key === key ? { key, direction: current.direction === 'asc' ? 'desc' : 'asc' } : { key, direction: 'asc' };

/**
 * The register's view, read from the address bar and the history entry's state. Anything unreadable -- a page that is a
 * word, a size the screen does not offer, an order the register is not read in -- is the register's default, never a
 * refusal: an address somebody typed or kept from last month still opens the register.
 *
 * `page` is counted from 0, as the server counts it; the address bar counts from 1, as a person does.
 *
 * @param {URLSearchParams} params
 * @param {object} [state] the history entry's state: a private query, and the filters another screen arrived with
 * @param {string[]} [statuses] the statuses a status may be
 */
export function readView(params, state = null, statuses = null) {
  const page = Number.parseInt(params.get(PARAM.page) ?? '', 10);
  const size = Number.parseInt(params.get(PARAM.size) ?? '', 10);
  const status = text(params.get(PARAM.status));
  return {
    q: searchTerm(params.get(PARAM.q)) || searchTerm(state?.[PRIVATE_QUERY]),
    page: Number.isSafeInteger(page) && page >= 1 ? page - 1 : 0,
    size: REGISTER_PAGE_SIZES.includes(size) ? size : REGISTER_DEFAULT_PAGE_SIZE,
    sort: readSort(params.get(PARAM.sort)),
    status: statuses && !statuses.includes(status) ? '' : status,
    bank: text(params.get(PARAM.bank)) || text(state?.filters?.bankName),
    branch: text(params.get(PARAM.branch)) || text(state?.filters?.branch),
  };
}

/**
 * The address bar for a view: the defaults left out, so the register's own address is just `/members`. A whole
 * identity number is left out too -- it goes in the history entry's state ({@link historyState}).
 */
export function writeView(view) {
  const params = new URLSearchParams();
  if (view.q && !isWholeIdentityNumber(view.q)) params.set(PARAM.q, view.q);
  if (view.status) params.set(PARAM.status, view.status);
  if (view.bank) params.set(PARAM.bank, view.bank);
  if (view.branch) params.set(PARAM.branch, view.branch);
  if (view.sort && sortParam(view.sort) !== sortParam(REGISTER_DEFAULT_SORT)) params.set(PARAM.sort, sortParam(view.sort));
  if (view.size && view.size !== REGISTER_DEFAULT_PAGE_SIZE) params.set(PARAM.size, String(view.size));
  if (view.page > 0) params.set(PARAM.page, String(view.page + 1));
  return params;
}

/** The history entry's state for a view: the private query, where the search is a whole identity number. */
export const historyState = (view) => (isWholeIdentityNumber(view.q) ? { [PRIVATE_QUERY]: view.q } : null);

/** What the server is asked for a view and the criteria behind "More criteria". */
export const registerQuery = (view, criteria) => ({
  ...criteria,
  q: view.q || undefined,
  status: view.status || undefined,
  bank: view.bank || undefined,
  branch: view.branch || undefined,
  page: view.page,
  size: view.size,
  sort: sortParam(view.sort),
});

/** An answer that is a page: rows and a total, never a bare list or a proxy's page of HTML (MV-36). */
export const isRegisterPage = (answer) =>
  Boolean(answer) && Array.isArray(answer.content) && Number.isInteger(answer.page)
  && Number.isInteger(answer.totalPages) && Number.isFinite(answer.totalElements);

/** The figure strip's answer, or `null` where it is not one. */
export const overviewFigures = (answer) => (
  answer && typeof answer === 'object' && !Array.isArray(answer) && Number.isFinite(answer.total)
    ? {
      total: answer.total,
      active: answer.active,
      pending: answer.pending,
      suspended: answer.suspended,
      newThisMonth: answer.newThisMonth,
      newDebitOrders: answer.newDebitOrders,
      updatedToday: answer.updatedToday,
    }
    : null
);
