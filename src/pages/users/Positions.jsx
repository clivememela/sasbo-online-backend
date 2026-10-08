import { useCallback, useId, useMemo, useState } from 'react';
import { Plus } from 'lucide-react';

import DialogNotice, { PageNotice } from '../../components/common/DialogNotice';
import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet';
import Button from '../../components/form/Button';
import SelectInput from '../../components/form/SelectInput';
import TextInput from '../../components/form/TextInput';
import Modal, { ModalClose, closeOn } from '../../components/overlay/Modal';
import Table from '../../components/table/Table';
import RequirePermission from '../../auth/RequirePermission';
import { PERMISSIONS } from '../../auth/permissions';
import useFocusOn from '../../hooks/useFocusOn';
import useReferenceData from '../../hooks/useReferenceData';
import reportError from '../../lib/reportError';
import { createPosition, updatePosition } from '../../services/adminService';
import { positionsList, rolesList } from './adminLists';
import { LIMITS, NONE, editorFailure, positionBody, positionForm, positionProblems } from './usersRules';

/**
 * *Maintenance → Positions* -- SASBO's positions and the role each one gives a new user. Provisional until Stephne's
 * list arrives ("the list will be shared", 5 Oct); her positions replace these as data, with no code change
 * (permissions.md). A position no longer offered stays on the users who hold it, and is not offered for new ones.
 */

const LISTS = { positions: positionsList, roles: rolesList };

function PositionDialog({ position, roles, onClose, onSaved }) {
  const creating = !position;
  const formId = useId();
  const [form, setForm] = useState(() => positionForm(position));
  const [fields, setFields] = useState({});
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const set = (field) => (value) => setForm((all) => ({ ...all, [field]: value }));
  const text = (field) => (event) => set(field)(event.target.value);

  const save = async (event) => {
    event.preventDefault();
    const problems = positionProblems(form, { creating });
    setFields(problems);
    setNotice(null);
    if (Object.keys(problems).length) return;
    setBusy(true);
    try {
      if (creating) await createPosition(positionBody(form));
      else await updatePosition(position.code, positionBody({ ...form, code: position.code }));
      onSaved(form.name.trim());
    } catch (error) {
      reportError('Positions.save', error);
      const failure = editorFailure(error, `The position ${form.name.trim() || ''}`.trim(), 'the positions');
      setFields(failure.fields);
      setNotice({ message: failure.notice });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open
      onOpenChange={closeOn(onClose)}
      title={creating ? 'Add a position' : `Change ${position.name}`}
      description={creating ? undefined : `Code ${position.code}`}
      busy={busy}
      footer={(
        <>
          <ModalClose variant="ghost">Cancel</ModalClose>
          <Button type="submit" form={formId} variant="primary" loading={busy}>{creating ? 'Add position' : 'Save changes'}</Button>
        </>
      )}
    >
      <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />
      <form id={formId} onSubmit={save} noValidate className="grid gap-4">
        <TextInput label="Name" value={form.name} onChange={text('name')} error={fields.name} required maxLength={LIMITS.name} />
        {creating && (
          <TextInput label="Code" value={form.code} onChange={(event) => set('code')(event.target.value.toUpperCase())}
            error={fields.code} required maxLength={LIMITS.code} autoComplete="off" spellCheck={false}
            hint="Capital letters, digits and underscores. It cannot be changed later." />
        )}
        <SelectInput label="Default role" required placeholder="Choose a role" {...roles} optionValue="code" optionLabel="name"
          value={form.defaultRoleCode} onChange={set('defaultRoleCode')} error={fields.defaultRoleCode}
          hint="What a new user in this position may do, unless their own role says otherwise." />
        <TextInput label="Order" kind="number" value={form.ordinal} onChange={text('ordinal')} error={fields.ordinal} maxLength={4}
          hint="Where it stands in the list of positions: lower comes first." />
        <label className="touch-target flex cursor-pointer items-start gap-3 rounded p-1.5 text-sm text-ink">
          <input type="checkbox" checked={form.active} onChange={(event) => set('active')(event.target.checked)} className="mt-0.5 size-4 shrink-0 accent-brand" />
          <span>
            Offered when creating a user
            <span className="block text-xs text-ink-muted">Untick to stop offering it. Users who hold it keep it.</span>
          </span>
        </label>
      </form>
    </Modal>
  );
}

function PositionsSheet() {
  const lists = useReferenceData(LISTS);
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  const noticeRef = useFocusOn(notice);
  const roleName = useMemo(() => new Map(lists.data.roles.map((role) => [role.code, role.name])), [lists.data.roles]);
  const open = useCallback((position) => {
    setNotice(null);
    setDialog({ position });
  }, []);
  const positions = lists.field('positions');

  const columns = useMemo(() => [
    {
      key: 'name',
      header: 'Position',
      cell: (p) => (
        <button type="button" data-row-action="" aria-label={`Change ${p.name}`} onClick={() => open(p)}
          className="touch-target rounded-sm text-left font-medium text-brand-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring">
          {p.name}
        </button>
      ),
    },
    { key: 'code', header: 'Code', priority: 'secondary' },
    { key: 'defaultRoleCode', header: 'Default role', cell: (p) => roleName.get(p.defaultRoleCode) ?? p.defaultRoleCode ?? NONE },
    { key: 'ordinal', header: 'Order', priority: 'secondary', align: 'right', className: 'tabular-nums' },
    { key: 'active', header: 'Offered', end: true, cell: (p) => (p.active === false ? 'No' : 'Yes') },
  ], [open, roleName]);

  return (
    <>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <Button variant="primary" onClick={() => open(null)}>
          <Plus size={16} strokeWidth={1.75} aria-hidden="true" /> Add a position
        </Button>
      </div>
      {notice && <PageNotice ref={noticeRef} id="positions-notice" tone="ok" onDismiss={() => setNotice(null)}>{notice}</PageNotice>}
      <Sheet className="register mt-4">
        <Table
          caption="SASBO’s positions"
          columns={columns}
          rows={positions.options}
          rowKey="code"
          onRowSelect={open}
          loading={positions.loading}
          error={positions.loadError}
          errorMessage="The positions did not load, so this is not an empty list."
          onRetry={lists.retry}
          emptyTitle="No position is on record yet."
          emptyMessage="Add SASBO’s positions with Add a position."
        />
      </Sheet>
      {dialog && (
        <PositionDialog
          position={dialog.position}
          roles={lists.field('roles')}
          onClose={() => setDialog(null)}
          onSaved={(name) => {
            setDialog(null);
            setNotice(`${name} is saved.`);
            lists.retry();
          }}
        />
      )}
    </>
  );
}

export default function Positions() {
  return (
    <Page>
      <PageTitle title="Positions" subtitle="SASBO’s positions, and the role each one gives a user who holds it." />
      <RequirePermission permission={PERMISSIONS.USERS_ADMIN}>
        <PositionsSheet />
      </RequirePermission>
    </Page>
  );
}
