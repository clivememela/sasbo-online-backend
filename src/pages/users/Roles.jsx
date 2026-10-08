import { useCallback, useId, useMemo, useState } from 'react';
import { Plus } from 'lucide-react';

import DialogNotice, { PageNotice } from '../../components/common/DialogNotice';
import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet';
import Button from '../../components/form/Button';
import CheckboxGroup from '../../components/form/CheckboxGroup';
import LoadFailureFrame from '../../components/form/LoadFailureFrame';
import TextInput from '../../components/form/TextInput';
import Modal, { ModalClose, closeOn } from '../../components/overlay/Modal';
import Table from '../../components/table/Table';
import RequirePermission from '../../auth/RequirePermission';
import { PERMISSIONS } from '../../auth/permissions';
import useFocusOn from '../../hooks/useFocusOn';
import useReferenceData from '../../hooks/useReferenceData';
import reportError from '../../lib/reportError';
import { createRole, updateRole } from '../../services/adminService';
import { permissionsList, rolesList } from './adminLists';
import { LIMITS, editorFailure, roleBody, roleForm, roleProblems } from './usersRules';

/**
 * *Maintenance → Roles* -- what each role may see and do, as a checklist of the permission catalogue
 * (`GET /api/admin/permissions`, each with the server's sentence). Roles are data a System Administrator edits
 * (permissions.md); a position gives its role to a new user, and a user may be given another.
 *
 * Organisers never see unpaids or payroll discrepancies (Stephne, 6 Oct): that is a fact about the roles SASBO's
 * organisers hold, kept by whoever edits them here -- the catalogue's sentences say which permissions those are.
 */

const LISTS = { roles: rolesList, permissions: permissionsList };

function RoleDialog({ role, catalogue, onClose, onSaved }) {
  const creating = !role;
  const formId = useId();
  const [form, setForm] = useState(() => roleForm(role));
  const [fields, setFields] = useState({});
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const described = useMemo(() => new Map(catalogue.options.map((p) => [p.name, p.description])), [catalogue.options]);

  const save = async (event) => {
    event.preventDefault();
    const problems = roleProblems(form, { creating });
    setFields(problems);
    setNotice(null);
    if (Object.keys(problems).length) return;
    setBusy(true);
    try {
      if (creating) await createRole(roleBody(form));
      else await updateRole(role.code, roleBody({ ...form, code: role.code }));
      onSaved(form.name.trim());
    } catch (error) {
      reportError('Roles.save', error);
      const failure = editorFailure(error, `The role ${form.name.trim() || ''}`.trim(), 'the roles');
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
      title={creating ? 'Add a role' : `Change ${role.name}`}
      description={creating ? undefined : `Code ${role.code}`}
      size="lg"
      busy={busy}
      footer={(
        <>
          <ModalClose variant="ghost">Cancel</ModalClose>
          <Button type="submit" form={formId} variant="primary" loading={busy}>{creating ? 'Add role' : 'Save changes'}</Button>
        </>
      )}
    >
      <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />
      <form id={formId} onSubmit={save} noValidate className="grid gap-4">
        <TextInput label="Name" value={form.name} onChange={(event) => setForm((all) => ({ ...all, name: event.target.value }))}
          error={fields.name} required maxLength={LIMITS.name} />
        {creating && (
          <TextInput label="Code" value={form.code} onChange={(event) => setForm((all) => ({ ...all, code: event.target.value.toUpperCase() }))}
            error={fields.code} required maxLength={LIMITS.code} autoComplete="off" spellCheck={false}
            hint="Capital letters, digits and underscores. It cannot be changed later." />
        )}
        {/* The System Administrator's role (`fixed`) holds every permission and keeps them -- the server refuses any
            other set (ROLE_FIXED) -- so it is said, not offered as a checklist that could only be refused. */}
        {role?.fixed ? (
          <p className="rounded-lg border border-line bg-surface-2 p-3 text-sm text-ink">
            This role holds every permission, and they are not changed here. Its name can be.
          </p>
        ) : (
        <LoadFailureFrame failed={Boolean(catalogue.loadError)} label="The permissions" loadError={catalogue.loadError} onRetryLoad={catalogue.onRetryLoad}>
          {catalogue.loading ? (
            <p role="status" className="text-sm text-ink-muted">Loading the permissions…</p>
          ) : (
            <CheckboxGroup
              legend="May"
              options={catalogue.options.map((p) => p.name)}
              selected={form.permissions}
              onToggle={(permissions) => setForm((all) => ({ ...all, permissions }))}
              labelFor={(name) => (
                <>
                  <span className="block">{described.get(name) || name}</span>
                  <span className="block text-xs text-ink-muted">{name}</span>
                </>
              )}
              error={fields.permissions}
            />
          )}
        </LoadFailureFrame>
        )}
      </form>
    </Modal>
  );
}

function RolesSheet() {
  const lists = useReferenceData(LISTS);
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState(null);
  const noticeRef = useFocusOn(notice);
  const open = useCallback((role) => {
    setNotice(null);
    setDialog({ role });
  }, []);
  const roles = lists.field('roles');
  const total = lists.data.permissions.length;

  const columns = useMemo(() => [
    {
      key: 'name',
      header: 'Role',
      cell: (r) => (
        <button type="button" data-row-action="" aria-label={`Change ${r.name}`} onClick={() => open(r)}
          className="touch-target rounded-sm text-left font-medium text-brand-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring">
          {r.name}
        </button>
      ),
    },
    { key: 'code', header: 'Code', priority: 'secondary' },
    {
      key: 'permissions',
      header: 'May',
      end: true,
      cell: (r) => {
        const held = r.permissions?.length ?? 0;
        return total ? `${held} of ${total} permissions` : `${held} permission${held === 1 ? '' : 's'}`;
      },
    },
  ], [open, total]);

  return (
    <>
      <div className="mt-5 flex flex-wrap items-center gap-3">
        <Button variant="primary" onClick={() => open(null)}>
          <Plus size={16} strokeWidth={1.75} aria-hidden="true" /> Add a role
        </Button>
      </div>
      {notice && <PageNotice ref={noticeRef} id="roles-notice" tone="ok" onDismiss={() => setNotice(null)}>{notice}</PageNotice>}
      <Sheet className="register mt-4">
        <Table
          caption="The roles, and how many permissions each holds"
          columns={columns}
          rows={roles.options}
          rowKey="code"
          onRowSelect={open}
          loading={roles.loading}
          error={roles.loadError}
          errorMessage="The roles did not load, so this is not an empty list."
          onRetry={lists.retry}
          emptyTitle="No role is on record yet."
          emptyMessage="Add one with Add a role."
        />
      </Sheet>
      {dialog && (
        <RoleDialog
          role={dialog.role}
          catalogue={lists.field('permissions')}
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

export default function Roles() {
  return (
    <Page>
      <PageTitle title="Roles" subtitle="What each role may see and do. A position gives its role to a new user." />
      <RequirePermission permission={PERMISSIONS.USERS_ADMIN}>
        <RolesSheet />
      </RequirePermission>
    </Page>
  );
}
