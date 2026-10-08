import { http, HttpResponse } from 'msw'

/**
 * THE MEMBER REGISTER'S THREE READS, ANSWERED FROM A TEST'S OWN MEMBERS (register row A10).
 *
 * The register screen read `GET /api/members` -- every member, every field -- and a test answered it with its list. It
 * reads three things now: a PAGE of slim rows (`POST /api/members/register/search`), its FIGURES and branches
 * (`GET /api/members/register/overview`), and ONE member's record when a row is opened (`GET /api/members/{id}`). One
 * list answers all three here, by the server's rules (`MemberRegisterRepository`), close enough for a screen's test:
 * a search finds the START of a surname, a first name, a membership or employee number, four digits the identity
 * numbers ending in them, a whole identity number its member; status, bank and branch narrow exactly; the orders are the
 * register's; a page past the last is answered as the last.
 *
 * The list is read on every request, so a test may change it between them -- a save, a change of status; pass a
 * FUNCTION answering the list where the test reassigns the variable that holds it. `requests`, where given, receives
 * every page's question as the screen sent it, for a test about what was asked.
 *
 * 🔑 THE KIT GOES LAST IN ITS `server.use` CALL. Within one call the first handler for a route answers, and a later call
 * goes in front of an earlier one: a test that answers a member's record itself -- counting the reads, or failing them
 * -- lists its own handler before `...registerHandlers(...)`, or uses it in a later call.
 *
 * Every member a test hands in is invented; the rules here are the server's, which are proved on the server.
 */

/** A row as the register's page answers it: the six columns and the id -- never an identity or account number. */
export const registerRow = (m) => ({
  id: m.id,
  membershipNumber: m.membershipNumber ?? null,
  employeeNumber: m.employeeNumber ?? null,
  title: m.title ?? null,
  initials: m.initials ?? null,
  surname: m.surname ?? null,
  firstNames: m.firstNames ?? null,
  bankName: m.bankName ?? null,
  branch: m.branch ?? null,
  status: m.status ?? null,
})

const lower = (value) => String(value ?? '').toLowerCase()
const starts = (value, term) => lower(value).startsWith(term)
const digits = (value) => /^\d+$/.test(value)

/** The search box, as the server reads it. */
const found = (m, q) => {
  const term = lower(q)
  if ([m.surname, m.firstNames, m.membershipNumber, m.employeeNumber].some((v) => starts(v, term))) return true
  if (digits(q) && starts(m.membershipNumber, `m-${term}`)) return true
  const identity = String(m.identityNumber ?? '').toUpperCase()
  if (digits(q) && q.length === 4) return identity.endsWith(q)
  return q.length >= 6 && /\d/.test(q) && identity === q.toUpperCase()
}

const ORDERS = {
  surname: (m) => [lower(m.surname), lower(m.firstNames)],
  membershipNumber: (m) => [String(m.membershipNumber ?? '')],
  bank: (m) => [String(m.bankName ?? ''), lower(m.surname), lower(m.firstNames)],
  branch: (m) => [String(m.branch ?? ''), lower(m.surname), lower(m.firstNames)],
  status: (m) => [String(m.status ?? ''), lower(m.surname), lower(m.firstNames)],
}

const compare = (keyOf) => (a, b) => {
  const [ka, kb] = [keyOf(a), keyOf(b)]
  for (let i = 0; i < ka.length; i += 1) {
    if (ka[i] !== kb[i]) return ka[i] < kb[i] ? -1 : 1
  }
  return (a.id ?? 0) - (b.id ?? 0)
}

/** The page the server would answer for `query` over `members`. */
export function registerPage(members, query = {}) {
  const [key, direction = 'asc'] = String(query.sort || 'surname,asc').split(',')
  const order = ORDERS[key] ?? ORDERS.surname
  const size = query.size ?? 50
  const matching = members
    .filter((m) => !query.q || found(m, query.q))
    .filter((m) => !query.status || m.status === query.status)
    .filter((m) => !query.bank || m.bankName === query.bank)
    .filter((m) => !query.branch || m.branch === query.branch)
    .filter((m) => !query.surname || starts(m.surname, lower(query.surname)))
    .filter((m) => !query.identityNumber || String(m.identityNumber ?? '').endsWith(query.identityNumber))
    .sort(compare(order))
  if (direction === 'desc') matching.reverse()
  const totalPages = Math.ceil(matching.length / size)
  const page = totalPages === 0 ? 0 : Math.min(query.page ?? 0, totalPages - 1)
  return {
    content: matching.slice(page * size, page * size + size).map(registerRow),
    page,
    size,
    totalElements: matching.length,
    totalPages,
    sort: `${ORDERS[key] ? key : 'surname'},${direction}`,
  }
}

/** The figures the server would answer for `members`: by status, and the branches they are at. */
export function registerOverview(members) {
  const by = (status) => members.filter((m) => m.status === status).length
  return {
    total: members.length,
    active: by('Active'),
    pending: by('Pending'),
    suspended: by('Suspended'),
    newThisMonth: 0,
    newDebitOrders: 0,
    updatedToday: 0,
    branches: [...new Set(members.map((m) => m.branch).filter(Boolean))].sort(),
    asOf: '2026-10-07',
  }
}

/**
 * The register's three reads, from `members`: the page, the figures (or `overview`, where a test names its own) and the
 * record of any member in the list. An id not in it is the server's 404.
 */
export function registerHandlers(members, { requests, overview } = {}) {
  const list = typeof members === 'function' ? members : () => members
  return [
    http.post('*/api/members/register/search', async ({ request }) => {
      const query = await request.json()
      requests?.push(query)
      return HttpResponse.json(registerPage(list(), query))
    }),
    http.get('*/api/members/register/overview', () => HttpResponse.json(overview ?? registerOverview(list()))),
    http.get('*/api/members/:id', ({ params }) => {
      // Only a member's number: `/api/members/summary` and its like are not records.
      if (!digits(String(params.id))) return undefined
      const one = list().find((m) => String(m.id) === String(params.id))
      return one
        ? HttpResponse.json(one)
        : HttpResponse.json({ status: 404, code: 'MEMBER_NOT_FOUND', message: 'That member is not on record.' }, { status: 404 })
    }),
  ]
}

/** A register that cannot be read: the page refused with `status`, or -- `'network'` -- never answered at all. */
export function registerFails(status = 500) {
  return http.post('*/api/members/register/search', () => (status === 'network'
    ? HttpResponse.error()
    : HttpResponse.json({ status, message: 'The server could not complete that request.' }, { status })))
}
