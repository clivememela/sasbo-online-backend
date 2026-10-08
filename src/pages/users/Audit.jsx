import { useEffect, useState } from 'react';

import Criteria, { CRITERION, CRITERION_WIDE } from '../../components/common/Criteria';
import Pager from '../../components/common/Pager';
import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet';
import Button from '../../components/form/Button';
import DateField from '../../components/form/DateField';
import TextInput from '../../components/form/TextInput';
import Table from '../../components/table/Table';
import RequirePermission from '../../auth/RequirePermission';
import { PERMISSIONS } from '../../auth/permissions';
import { useDebounce } from '../../hooks/useDebounce';
import reportError from '../../lib/reportError';
import { getAudit } from '../../services/auditService';
import { LIMITS, NONE, actionLabel, whenLabel, whoLabel } from './usersRules';

/**
 * *Maintenance → Who did what* -- "a record of who did what" (5 Oct Show & Tell): every sign-in, failed sign-in and
 * lock-out, every change to users, positions and roles, and every held action, newest first, a page at a time, from
 * `GET /api/audit` (`AUDIT_VIEW`). Narrowed by who, by what was changed, and by the days between two dates.
 *
 * The record's summary names fields and counts, never a member's values (contracts/signin.md), so it is shown as the
 * server wrote it.
 */

export const PAGE_SIZE = 50;
const NOT_AVAILABLE = 'The record is not available from this server.';
const NO_FILTERS = { username: '', entityType: '', entityId: '', from: '', to: '' };

const COLUMNS = [
  { key: 'at', header: 'When', className: 'tabular-nums whitespace-nowrap', cell: (e) => whenLabel(e.at) },
  { key: 'who', header: 'Who', cell: (e) => whoLabel(e) },
  { key: 'action', header: 'Action', cell: (e) => actionLabel(e.action) },
  {
    key: 'entity',
    header: 'What',
    priority: 'secondary',
    cell: (e) => (e.entityType ? [e.entityType, e.entityId].filter(Boolean).join(' ') : NONE),
  },
  { key: 'summary', header: 'Summary', truncate: true, className: 'max-w-[24rem]', cell: (e) => e.summary || NONE },
];

function AuditSheet() {
  const [filters, setFilters] = useState(NO_FILTERS);
  const [paging, setPaging] = useState({ page: 0 });
  const [attempt, setAttempt] = useState(0);
  const [answer, setAnswer] = useState({});

  // Typing settles before it asks; the dates ask at once.
  const typed = useDebounce(`${filters.username.trim()}\n${filters.entityType.trim()}\n${filters.entityId.trim()}`, 400);
  const [username, entityType, entityId] = typed.split('\n');
  const backwards = Boolean(filters.from && filters.to && filters.to < filters.from);
  const question = JSON.stringify([username, entityType, entityId, filters.from, filters.to]);
  const page = paging.question === question ? paging.page : 0;
  const key = `${question}|${page}|${attempt}`;

  useEffect(() => {
    if (backwards) return undefined;
    let current = true;
    getAudit({ username, entityType, entityId, from: filters.from, to: filters.to, page, size: PAGE_SIZE })
      .then((data) => {
        if (!current) return;
        if (!Array.isArray(data?.content)) throw new Error(NOT_AVAILABLE);
        setAnswer({ key, data });
      })
      .catch((error) => {
        if (!current) return;
        reportError('Audit.read', error);
        setAnswer({ key, error });
      });
    return () => {
      current = false;
    };
  }, [key, backwards, username, entityType, entityId, filters.from, filters.to, page]);

  const loading = !backwards && answer.key !== key;
  const failed = loading || backwards ? null : answer.error;
  const data = loading || backwards ? null : answer.data;
  const rows = data?.content ?? [];
  const pageNumber = Number(data?.page ?? data?.number ?? page) || 0;
  const filtered = Object.values(filters).some((value) => value.trim());
  const set = (field) => (event) => setFilters((all) => ({ ...all, [field]: event.target.value }));

  return (
    <>
      <Criteria className="mt-5">
        <TextInput className={CRITERION_WIDE} label="Who" value={filters.username} onChange={set('username')}
          placeholder="A username" maxLength={LIMITS.username} autoComplete="off" spellCheck={false} />
        <TextInput className={CRITERION} label="What" value={filters.entityType} onChange={set('entityType')}
          placeholder="USER, ROLE, MEMBER…" maxLength={LIMITS.code} autoComplete="off" spellCheck={false} />
        <TextInput className={CRITERION} label="Which" value={filters.entityId} onChange={set('entityId')}
          placeholder="Its number" maxLength={LIMITS.code} autoComplete="off" spellCheck={false} />
        <DateField className={CRITERION} label="From" value={filters.from} onChange={set('from')} />
        <DateField className={CRITERION} label="To" value={filters.to} onChange={set('to')}
          error={backwards ? 'The end date is before the start date.' : undefined} />
        <Button variant="ghost" onClick={() => setFilters(NO_FILTERS)}>Clear</Button>
      </Criteria>
      <Sheet className="register mt-4">
        <Table
          error={failed}
          caption="The record of who did what, newest first"
          columns={COLUMNS}
          rows={rows}
          rowKey={(entry, index) => entry.id ?? `${entry.at}-${index}`}
          loading={loading}
          errorMessage="The record did not load, so this is not an empty record."
          onRetry={() => setAttempt((n) => n + 1)}
          emptyTitle={backwards ? 'Those dates are the wrong way round.' : filtered ? 'Nothing matches these filters.' : 'Nothing is recorded yet.'}
          emptyMessage={backwards ? 'Choose a To date on or after the From date.' : filtered ? 'Clear the filters to see the whole record.' : undefined}
        />
        <Pager
          page={pageNumber + 1}
          totalPages={Math.max(Number(data?.totalPages) || 0, 1)}
          onPageChange={(next) => setPaging({ question, page: next - 1 })}
          count={Number(data?.totalElements) || 0}
          start={pageNumber * PAGE_SIZE + 1}
          end={pageNumber * PAGE_SIZE + rows.length}
          noun="entries"
          loading={loading}
          error={Boolean(failed)}
        />
      </Sheet>
    </>
  );
}

export default function Audit() {
  return (
    <Page>
      <PageTitle title="Who did what" subtitle="The record of every sign-in and every change to users, positions and roles, newest first." />
      <RequirePermission permission={PERMISSIONS.AUDIT_VIEW}>
        <AuditSheet />
      </RequirePermission>
    </Page>
  );
}
