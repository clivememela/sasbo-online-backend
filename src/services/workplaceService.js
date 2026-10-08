import { apiUrl } from './api';
import { requestJson } from './parseApiError';

/**
 * SASBO's workplaces -- month end 01.2 -- a page at a time, PAGED BY THE SERVER in its own order: the workplace's
 * name, then its R/A number. No request names a column (MR-SEC-22).
 *
 * `search` finds a workplace by its name or its R/A number. The caller waits for two characters and a debounce, and
 * drops an answer that arrives after a newer question (MR-PERF-12): the server refuses a term over a hundred
 * characters `REQUEST_MALFORMED`, and a page past the last `WORKPLACE_PAGE_INVALID`.
 *
 * The answer is `{ content, page, size, totalElements, totalPages, reason }`. Where no workplace is on record here
 * -- the deployment's switch is off until SASBO agrees (MD45), or they could not be loaded -- `content` is empty and
 * `reason` says why. At `names` a workplace has no `staffComplement` (MD62).
 */
const FILTERS = ['page', 'size', 'employer', 'tableCode', 'area', 'committee', 'search'];

export const workplaceService = {
  getWorkplaces: (filters = {}) => {
    const query = new URLSearchParams();
    for (const key of FILTERS) {
      const value = filters[key];
      if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
    }
    const suffix = query.toString() ? `?${query}` : '';
    return requestJson((signal) => fetch(apiUrl('/api/workplaces') + suffix, { credentials: 'include', signal }));
  },
};
