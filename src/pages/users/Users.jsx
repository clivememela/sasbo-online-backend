import { useCallback, useEffect, useMemo, useState } from 'react';
import { UserPlus } from 'lucide-react';

import Criteria, { CRITERION_WIDE } from '../../components/common/Criteria';
import { PageNotice } from '../../components/common/DialogNotice';
import Pager from '../../components/common/Pager';
import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet';
import Button from '../../components/form/Button';
import TextInput from '../../components/form/TextInput';
import Table from '../../components/table/Table';
import RequirePermission from '../../auth/RequirePermission';
import { PERMISSIONS } from '../../auth/permissions';
import { useDebounce } from '../../hooks/useDebounce';
import useFocusOn from '../../hooks/useFocusOn';
import reportError from '../../lib/reportError';
import { getUsers } from '../../services/adminService';
import UserDialog from './UserDialog';
import { LIMITS, NONE, scopeLabel, standingLabel, whenLabel } from './usersRules';

/**
 * *Maintenance → Users* -- the 5 Oct Show & Tell's "an administrator creating users with a position and an area".
 * A page of users at a time, in the server's order, found by name or username; each opens onto its own dialog
 * (`UserDialog`): change the position, the role, what they see, whether they may sign in, or reset the password.
 *
 * Everything below the heading is `USERS_ADMIN`'s (contracts/signin.md): for anyone else the screen says what is needed
 * and asks the server for nothing.
 */

export const PAGE_SIZE = 50;
const NOT_AVAILABLE = 'The users are not available from this server.';

const COLUMNS = (open) => [
  {
    key: 'displayName',
    header: 'Name',
    cell: (u) => (
      <button
        type="button"
        data-row-action=""
        aria-label={`Change ${u.displayName} (${u.username})`}
        onClick={() => open(u)}
        className="touch-target grid rounded-sm text-left focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring"
      >
        <span className="font-medium text-brand-ink">{u.displayName}</span>
        <span className="text-xs text-ink-muted">{u.username}</span>
      </button>
    ),
  },
  { key: 'position', header: 'Position', cell: (u) => u.position?.name ?? NONE },
  { key: 'role', header: 'Role', priority: 'secondary', cell: (u) => u.role?.name ?? NONE },
  { key: 'scope', header: 'Sees', cell: (u) => scopeLabel(u.scope) },
  { key: 'lastSignInAt', header: 'Last signed in', priority: 'secondary', cell: (u) => whenLabel(u.lastSignInAt) },
  { key: 'standing', header: 'Standing', end: true, cell: (u) => standingLabel(u) },
];

function UsersSheet({ onOpen, reloads }) {
  const [text, setText] = useState('');
  const [paging, setPaging] = useState({ page: 0 });
  const [attempt, setAttempt] = useState(0);
  const [answer, setAnswer] = useState({});

  // From the second character, once typing pauses, as the workplaces' search asks.
  const typed = text.trim();
  const settled = useDebounce(typed, 400);
  const q = typed && settled.length > 1 ? settled : '';
  const page = paging.q === q ? paging.page : 0;
  const key = `${q}|${page}|${attempt}|${reloads}`;

  useEffect(() => {
    let current = true;
    getUsers({ page, size: PAGE_SIZE, q })
      .then((data) => {
        if (!current) return;
        if (!Array.isArray(data?.content)) throw new Error(NOT_AVAILABLE);
        setAnswer({ key, data });
      })
      .catch((error) => {
        if (!current) return;
        reportError('Users.read', error);
        setAnswer({ key, error });
      });
    return () => {
      current = false;
    };
  }, [key, page, q]);

  const loading = answer.key !== key;
  const failed = loading ? null : answer.error;
  const data = loading ? null : answer.data;
  const rows = data?.content ?? [];
  const pageNumber = Number(data?.page ?? data?.number ?? page) || 0;
  const columns = useMemo(() => COLUMNS(onOpen), [onOpen]);

  return (
    <>
      <Criteria className="mt-5">
        <TextInput
          className={`${CRITERION_WIDE} max-md:basis-full`}
          label="Name or username"
          kind="search"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="Two characters or more"
          maxLength={LIMITS.search}
        />
      </Criteria>
      <Sheet className="register mt-4">
        <Table
          caption="The users of SASBO Online"
          columns={columns}
          rows={rows}
          rowKey="id"
          onRowSelect={onOpen}
          loading={loading}
          error={failed}
          errorMessage="The users did not load, so this is not an empty list."
          onRetry={() => setAttempt((n) => n + 1)}
          emptyTitle={q ? 'No user matches that name.' : 'No user has been created yet.'}
          emptyMessage={q ? 'Check the spelling, or clear the search.' : 'Create the first one with Create a user.'}
        />
        <Pager
          page={pageNumber + 1}
          totalPages={Math.max(Number(data?.totalPages) || 0, 1)}
          onPageChange={(next) => setPaging({ q, page: next - 1 })}
          count={Number(data?.totalElements) || 0}
          start={pageNumber * PAGE_SIZE + 1}
          end={pageNumber * PAGE_SIZE + rows.length}
          noun="users"
          loading={loading}
          error={Boolean(failed)}
        />
      </Sheet>
    </>
  );
}

export default function Users() {
  const [dialog, setDialog] = useState(null);
  const [reloads, setReloads] = useState(0);
  const [notice, setNotice] = useState(null);
  const noticeRef = useFocusOn(notice);

  const open = useCallback((user) => {
    setNotice(null);
    setDialog({ user });
  }, []);

  const saved = (user, { created }) => {
    setReloads((n) => n + 1);
    // A new user's dialog stays open on the temporary password; a change closes it and says so here.
    if (!created) {
      setDialog(null);
      setNotice(user ? `The changes to ${user.displayName} are saved.` : 'The changes are saved.');
    }
  };

  return (
    <Page>
      <PageTitle title="Users" subtitle="Who signs in to SASBO Online, the position each holds, and the part of SASBO each one sees." />
      <RequirePermission permission={PERMISSIONS.USERS_ADMIN}>
        <div className="mt-5 flex flex-wrap items-center gap-3">
          <Button variant="primary" onClick={() => { setNotice(null); setDialog({ user: null }); }}>
            <UserPlus size={16} strokeWidth={1.75} aria-hidden="true" /> Create a user
          </Button>
        </div>
        {notice && (
          <PageNotice ref={noticeRef} id="users-notice" tone="ok" onDismiss={() => setNotice(null)}>{notice}</PageNotice>
        )}
        <UsersSheet onOpen={open} reloads={reloads} />
        {dialog && <UserDialog user={dialog.user} onClose={() => setDialog(null)} onSaved={saved} />}
      </RequirePermission>
    </Page>
  );
}
