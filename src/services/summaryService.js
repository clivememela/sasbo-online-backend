import { apiUrl } from './api';
import { requestJson } from './parseApiError';

/**
 * The figures -- month end 02.2, `GET /api/members/summary` -- the one read of the figures on Month end
 * (`MonthEndFigures.jsx`; on Home until the 5 Oct Show & Tell), asked once the page knows its month (MD69).
 *
 * `getSummary({ month, by, area, region })`: every part optional, and a part with no value is left out of the address
 * rather than sent empty. 🔑 THE MONTH IS ALWAYS THE SERVER'S (MD55): Month end names the month it chose from the
 * server's own months; with no month named -- when those months could not be read -- the server answers for the month
 * its own clock is in, in Johannesburg, and says so (`month`, `monthFromTheServer`). The browser's clock decides
 * nothing. No `by` is the employer group. `area` is an area's letter and `region` a region's code -- one or the other, never both.
 *
 * The answer is `{ month, monthFromTheServer, by, area, region, workplacesServed, groupedAsAt, totals, rows, unplaced,
 * seededMembers, membersFrom, leaversFrom }`; `monthEnd.js`'s `summaryOf` says whether it is one. A refusal rejects
 * with the server's code and its sentence (`SUMMARY_*`), and a figure that took longer than the server's four seconds
 * is `SUMMARY_UNAVAILABLE` (503) -- never a hung screen.
 */
const PARTS = ['month', 'by', 'area', 'region'];

export const summaryService = {
  getSummary: (ask = {}) => {
    const query = new URLSearchParams();
    for (const part of PARTS) if (ask[part]) query.set(part, ask[part]);
    const suffix = query.toString() ? `?${query}` : '';
    return requestJson((signal) => fetch(apiUrl('/api/members/summary') + suffix, { credentials: 'include', signal }));
  },
};
