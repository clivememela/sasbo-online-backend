import { apiUrl } from './api';
import { requestJson } from './parseApiError';

/**
 * The record of who did what -- `GET /api/audit`, `AUDIT_VIEW` (contracts/signin.md). A page of
 * `{ at, username, displayName, action, entityType, entityId, summary }`, newest first, in the server's order.
 *
 * Filters: `username`, `entityType`, `entityId`, and `from`/`to` as calendar dates (`yyyy-mm-dd`, inclusive). An empty
 * filter is left out of the address rather than sent blank. The summary names fields and counts, never a member's
 * values (the contract), so nothing here needs masking.
 */
const FILTERS = ['username', 'entityType', 'entityId', 'from', 'to', 'page', 'size'];

export const getAudit = (filters = {}) => {
  const query = new URLSearchParams();
  for (const key of FILTERS) {
    const value = filters[key];
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
  }
  const suffix = query.toString() ? `?${query}` : '';
  return requestJson((signal) => fetch(apiUrl('/api/audit') + suffix, { credentials: 'include', signal }));
};

export const auditService = { getAudit };
