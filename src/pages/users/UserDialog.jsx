import { useId, useState } from 'react';
import { Copy } from 'lucide-react';

import Button from '../../components/form/Button';
import CheckboxGroup from '../../components/form/CheckboxGroup';
import LoadFailureFrame from '../../components/form/LoadFailureFrame';
import SelectInput from '../../components/form/SelectInput';
import TextInput from '../../components/form/TextInput';
import DialogNotice from '../../components/common/DialogNotice';
import Modal, { ModalClose, closeOn } from '../../components/overlay/Modal';
import useReferenceData from '../../hooks/useReferenceData';
import reportError from '../../lib/reportError';
import { listRows } from '../../config/monthEnd';
import { getAreas, getRegions } from '../../services/referenceService';
import { createUser, getUsers, resetPassword, updateUser } from '../../services/adminService';
import { writeFailure, writeMayHaveLanded } from '../../services/parseApiError';
import { positionsList, rolesList } from './adminLists';
import { LIMITS, NEW_USER, formOf, userBody, userProblems, userRefusal } from './usersRules';

/**
 * Create a user, or change one -- the 5 Oct ask: "an administrator creating users with a position and an area".
 *
 * Three steps in one dialog, so nothing opens on top of anything else:
 *   `form`      the user: name, username (when creating), e-mail, position, role, what they see, and whether they may
 *               sign in (when changing). A change carries the `version` it was read at; `STALE_VERSION` says someone
 *               saved first and offers to read the user again.
 *   `confirm`   resetting the password, asked first: it signs the user out everywhere.
 *   `password`  the temporary password, SHOWN ONCE. It lives in this dialog's state and nowhere else -- closing the
 *               dialog is the end of it -- and the dialog does not close on Escape or a stray click while it shows.
 *
 * The lists the form chooses from (positions, roles, SASBO's regions and areas) are read when the dialog opens, through
 * `useReferenceData`: one that fails says so on its own control, with its retry, and the others still work.
 */

// An answer that is not the list's shape is a failure the control shows, never an empty list (MV-36).
const notAList = (what) => {
  throw new Error(`SASBO’s ${what} are not available from the server.`);
};
const LISTS = {
  positions: positionsList,
  roles: rolesList,
  regions: () => getRegions().then((answer) => listRows(answer) ?? notAList('regions')),
  areas: () => getAreas().then((answer) => listRows(answer) ?? notAList('areas')),
};

/** A region by its name, an area by its letter and region -- the words beside each box of the scope's list. */
const scopeChoices = (list, rows) => new Map(rows.map((row) => (list === 'regions'
  ? [row.code, row.name || row.code]
  : [row.letter, row.region ? `Area ${row.letter} · ${row.region}` : `Area ${row.letter}`])));

const SCOPES = [
  ['ALL', 'All of SASBO'],
  ['REGION', 'Only some regions'],
  ['AREA', 'Only some areas'],
];

/** The temporary password, and how to pass it on. Copying is offered; where the browser refuses, it says so. */
function TemporaryPassword({ name, password }) {
  const [copied, setCopied] = useState(null);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(password);
      setCopied('Copied. Pass it to them yourself, and do not keep a copy.');
    } catch {
      setCopied('This browser would not copy it. Select the password and copy it yourself.');
    }
  };
  return (
    <div className="grid gap-3 text-sm text-ink">
      <p>Give this temporary password to {name}. They choose their own password the first time they sign in.</p>
      <p className="rounded-lg border border-status-suspended-dot bg-status-suspended-bg p-3 text-status-suspended">
        It is shown only this once. After you close this, nobody can see it again; if it is lost, reset the password again.
      </p>
      <div className="flex flex-wrap items-end gap-2">
        <div className="grid min-w-0 flex-1 gap-1">
          <p className="text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Temporary password</p>
          <p className="select-all break-all rounded-lg border border-line-strong bg-surface-2 px-3 py-2 font-mono text-lg text-ink">{password}</p>
        </div>
        <Button onClick={copy}>
          <Copy size={16} strokeWidth={1.75} aria-hidden="true" /> Copy
        </Button>
      </div>
      {copied && <p role="status" className="text-ink-muted">{copied}</p>}
    </div>
  );
}

export default function UserDialog({ user, onClose, onSaved }) {
  const creating = !user;
  const lists = useReferenceData(LISTS);
  const formId = useId();
  const scopeName = useId();

  const [form, setForm] = useState(() => (creating ? NEW_USER : formOf(user)));
  const [version, setVersion] = useState(user?.version);
  const [step, setStep] = useState('form');
  const [fields, setFields] = useState({});
  const [notice, setNotice] = useState(null);
  const [stale, setStale] = useState(false);
  const [busy, setBusy] = useState(false);
  const [password, setPassword] = useState('');

  const name = form.displayName.trim() || user?.displayName || 'this user';
  const set = (field) => (value) => setForm((all) => ({ ...all, [field]: value }));
  const text = (field) => (event) => set(field)(event.target.value);

  const refused = (error, what) => {
    reportError(`UserDialog.${what}`, error);
    // A refusal is the server saying no; anything else may have landed and is said as unconfirmed (SCREEN-CONTRACT §3).
    if (!writeMayHaveLanded(error)) {
      const refusal = userRefusal(error);
      setFields(refusal.fields ?? {});
      setStale(Boolean(refusal.stale));
      setNotice(refusal.notice ? { message: refusal.notice } : null);
    } else {
      setNotice({ message: writeFailure(error, creating ? 'The new user' : `The change to ${name}`, 'the list of users') });
    }
  };

  const save = async (event) => {
    event.preventDefault();
    const problems = userProblems(form, { creating });
    setFields(problems);
    setNotice(null);
    setStale(false);
    if (Object.keys(problems).length) return;
    setBusy(true);
    try {
      const answer = creating
        ? await createUser(userBody(form, { creating }))
        : await updateUser(user.id, userBody(form, { creating, version }));
      if (creating) {
        setPassword(answer?.temporaryPassword ?? '');
        setStep('password');
        onSaved(answer?.user, { created: true });
      } else {
        onSaved(answer?.user ?? null, { created: false });
      }
    } catch (error) {
      refused(error, creating ? 'create' : 'update');
    } finally {
      setBusy(false);
    }
  };

  // STALE_VERSION: the user as they are now, found by their username -- the contract reads users only by page.
  const reload = async () => {
    setBusy(true);
    try {
      const page = await getUsers({ q: user.username, page: 0, size: 20 });
      const fresh = page?.content?.find((row) => row.id === user.id);
      if (!fresh) {
        setNotice({ message: `${name} could not be found again. Close this and look for them in the list.` });
        return;
      }
      setForm(formOf(fresh));
      setVersion(fresh.version);
      setStale(false);
      setFields({});
      setNotice({ tone: 'ok', message: `This is ${fresh.displayName} as saved now. Make your change again.` });
    } catch (error) {
      reportError('UserDialog.reload', error);
      setNotice({ message: `${name} could not be read again. Close this and reload the list of users.` });
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    setBusy(true);
    setNotice(null);
    try {
      const answer = await resetPassword(user.id);
      setPassword(answer?.temporaryPassword ?? '');
      setStep('password');
    } catch (error) {
      reportError('UserDialog.reset', error);
      setNotice({ message: writeFailure(error, `The new password for ${name}`, 'the list of users') });
    } finally {
      setBusy(false);
    }
  };

  const scopeList = form.scopeType === 'REGION' ? 'regions' : form.scopeType === 'AREA' ? 'areas' : null;
  const scopeField = scopeList && lists.field(scopeList);
  const choices = scopeField ? scopeChoices(scopeList, scopeField.options) : null;
  const positions = lists.data.positions.filter((p) => p.active !== false || p.code === form.positionCode);

  const title = {
    form: creating ? 'Create a user' : `Change ${user?.displayName ?? 'this user'}`,
    confirm: `Reset the password of ${name}?`,
    password: `Temporary password for ${name}`,
  }[step];

  const footer = {
    form: (
      <>
        {!creating && (
          <Button variant="ghost" className="mr-auto" disabled={busy} onClick={() => { setNotice(null); setStep('confirm'); }}>
            Reset password
          </Button>
        )}
        <ModalClose variant="ghost">Cancel</ModalClose>
        <Button type="submit" form={formId} variant="primary" loading={busy}>
          {creating ? 'Create user' : 'Save changes'}
        </Button>
      </>
    ),
    confirm: (
      <>
        <Button variant="ghost" disabled={busy} onClick={() => setStep('form')}>Back</Button>
        <Button variant="danger" loading={busy} onClick={reset}>Reset password</Button>
      </>
    ),
    password: <Button variant="primary" onClick={onClose}>Done</Button>,
  }[step];

  return (
    <Modal
      open
      onOpenChange={closeOn(onClose)}
      title={title}
      description={creating || step !== 'form' ? undefined : `Username ${user.username}`}
      busy={busy}
      dismissible={step !== 'password'}
      footer={footer}
    >
      <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />
      {stale && (
        <Button className="mb-4" disabled={busy} onClick={reload}>Reload the user</Button>
      )}

      {step === 'form' && (
        <form id={formId} onSubmit={save} noValidate className="grid gap-4">
          <TextInput label="Name" kind="name" value={form.displayName} onChange={text('displayName')}
            error={fields.displayName} required maxLength={LIMITS.displayName} hint="As it shows in the header and beside every change they make." />
          {creating && (
            <TextInput label="Username" value={form.username} onChange={text('username')} error={fields.username} required
              maxLength={LIMITS.username} autoComplete="off" autoCapitalize="none" spellCheck={false} hint="What they type to sign in. It cannot be changed later." />
          )}
          <TextInput label="E-mail" kind="email" value={form.email} onChange={text('email')} error={fields.email}
            maxLength={LIMITS.email} autoComplete="off" />
          <SelectInput label="Position" required placeholder="Choose a position" {...lists.field('positions')} options={positions}
            optionValue="code" optionLabel="name" value={form.positionCode} onChange={set('positionCode')} error={fields.positionCode} />
          <SelectInput label="Role" placeholder="The position’s own role" {...lists.field('roles')} optionValue="code"
            optionLabel="name" value={form.roleCode} onChange={set('roleCode')} error={fields.roleCode}
            hint="What they may do. Leave it to the position unless this person needs something different." />

          <fieldset className="rounded-lg border border-line bg-surface-2 p-4">
            <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-ink-muted">Sees</legend>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {SCOPES.map(([type, label]) => (
                <label key={type} className="touch-target flex cursor-pointer items-center gap-3 rounded p-1.5 text-sm text-ink">
                  <input type="radio" name={scopeName} value={type} checked={form.scopeType === type} className="size-4 shrink-0 accent-brand"
                    onChange={() => setForm((all) => ({ ...all, scopeType: type, codes: [] }))} />
                  <span>{label}</span>
                </label>
              ))}
            </div>
          </fieldset>
          {scopeField && (
            <LoadFailureFrame failed={Boolean(scopeField.loadError)} label={scopeList === 'regions' ? 'The regions' : 'The areas'}
              loadError={scopeField.loadError} onRetryLoad={scopeField.onRetryLoad}>
              {scopeField.loading ? (
                <p role="status" className="text-sm text-ink-muted">Loading SASBO’s {scopeList}…</p>
              ) : (
                <CheckboxGroup
                  legend={scopeList === 'regions' ? 'Regions' : 'Areas'}
                  required
                  options={[...choices.keys()]}
                  selected={form.codes}
                  onToggle={set('codes')}
                  labelFor={(code) => choices.get(code) ?? code}
                  error={fields.scope}
                />
              )}
            </LoadFailureFrame>
          )}
          {!scopeField && fields.scope && <p role="alert" className="text-xs text-status-suspended">{fields.scope}</p>}

          {!creating && (
            <label className="touch-target flex cursor-pointer items-start gap-3 rounded p-1.5 text-sm text-ink">
              <input type="checkbox" checked={form.active} onChange={(event) => set('active')(event.target.checked)} className="mt-0.5 size-4 shrink-0 accent-brand" />
              <span>
                This user may sign in
                <span className="block text-xs text-ink-muted">
                  Untick to close the account: they are signed out at once, and their name stays on the work they did.
                </span>
              </span>
            </label>
          )}
        </form>
      )}

      {step === 'confirm' && (
        <p className="text-sm text-ink">
          {name} is signed out everywhere, and a temporary password is made for them to change when they next sign in.
          You see it once, on the next screen.
        </p>
      )}

      {step === 'password' && <TemporaryPassword name={name} password={password} />}
    </Modal>
  );
}
