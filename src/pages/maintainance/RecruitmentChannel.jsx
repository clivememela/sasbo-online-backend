import { useState, useEffect, useMemo, useCallback } from 'react';
import { Pencil, Trash2, UserPlus, Check, X } from 'lucide-react';
import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import Criteria, { CRITERION_WIDE } from '../../components/common/Criteria.jsx';
import TableScroller from '../../components/table/TableScroller.jsx';
import { HEAD_CELL } from '../../components/table/Table.jsx';
import { Button, TextInput } from '../../components/form';
import { ConfirmDialog, closeOn } from '../../components/overlay/Modal.jsx';
import { recruitmentChannelService } from '../../services/recruitmentChannelService.js';
import DataState from '../../components/common/DataState.jsx';
import reportError from '../../lib/reportError.js';
import DialogNotice, { PageNotice } from '../../components/common/DialogNotice.jsx';
import { focusOnMount } from '../../lib/focusOnMount.js';
import useFocusOn from '../../hooks/useFocusOn.js';
import { messageOf, writeFailure, writeMayHaveLanded } from '../../services/parseApiError.js';

/*
 * 📐 THE CHROME OF AN IN-ROW EDITOR -- one constant, with the same classes as `Subscriptions.jsx`'s
 * `inputCls`. These two inputs are not fields on a form; they are a cell that becomes a control for
 * as long as a rename lasts. So they take the compact chrome rather than `TextInput`'s label
 * block, which would double the row's height mid-edit. The page's own search, which IS a
 * field, went the other way: it is a `TextInput` inside a `Criteria` row now.
 */
// `touch-size`: an in-row input is a replaced element, so it GROWS to 44px on a coarse pointer (`07.2`; index.css).
const CELL_INPUT = 'w-full rounded border border-line-strong bg-surface-2 px-2 py-1 field-text text-ink touch-size';

export default function RecruitmentChannel() {
    const [notice, setNotice] = useState(null);
    const noticeRef = useFocusOn(notice);

    const [recruitmentChannels, setRecruipmentChannels] = useState([]);
    const [search, setSearch] = useState('');
    const [saving, setSaving] = useState(false);
    const [editingId, setEditingId] = useState(null);
    const [editName, setEditName] = useState('');
    const [showAdd, setShowAdd] = useState(false);
    const [newName, setNewName] = useState('');

    // 🔴 THE CARET FOLLOWS THE REVEAL (`10.4`): both inline inputs below take `ref={focusOnMount}`. They mount exactly
    // when the user asks for them, so focusing on mount is the whole rule -- see `lib/focusOnMount.js` for why this
    // replaced `autoFocus`, and then the ref-and-effect pair that was here first. `RecruitmentChannel.focus.test.jsx`
    // holds it: the caret arrives on each reveal, on the row that was pressed, and never before.

    /*
     * 🔴 A LIVE A27 THE REGISTER NEVER NAMED, AND IT WAS THE WORST SHAPE OF IT: this
     * `refresh` had NO `.catch` AT ALL.
     *
     * So a failed load was an unhandled promise rejection -- nothing on screen, nothing in
     * any handler -- and `recruitmentChannels` simply stayed `[]`, which the list below
     * rendered as *"No recruiter types found."* An administrator looking at a maintenance
     * screen for recruiter types was told, in as many words, that the organisation has
     * none. That is a fact about the SERVER stated as a fact about the DATA, which is A27's
     * whole definition, on a screen whose own job is managing that data.
     *
     * ⚠️ AND IT WAS INVISIBLE TO EVERY UPSTREAM PASS, because a missing catch is not a
     * wrong catch: there is no swallow to grep for, no `console.error` to find, and the
     * happy path is perfectly correct. `06.1b` found it by walking the empty-state copies
     * rather than the error handling.
     */
    const [listState, setListState] = useState('loading');
    const [loadError, setLoadError] = useState(null);

    // `useCallback` for the same reason every other list screen here uses one: it is the
    // effect's dependency, and a fresh identity each render is a re-fetch each render --
    // which on screen is indistinguishable from a slow backend.
    const refresh = useCallback(() => {
        return recruitmentChannelService.getAll()
            .then((data) => {
                setRecruipmentChannels(Array.isArray(data) ? data : []);
                setListState('ready');
            })
            .catch((error) => {
                reportError('RecruitmentChannel.refresh', error);
                setLoadError(error);
                setListState('error');
            });
    }, []);

    /*
     * 🔴 THE RESET IS ON EVERY PATH EXCEPT THE MOUNT (`10.4`), AND THAT DISTINCTION IS THE WHOLE CARE HERE.
     *
     * `react-hooks/set-state-in-effect` flagged the effect below, because `refresh` set state synchronously
     * before its fetch. On MOUNT that was a no-op -- `listState` already initialises to 'loading' and
     * `loadError` to null -- so hoisting it out of the effect costs nothing there.
     *
     * ⚠️ BUT THIS SCREEN IS NOT THE OTHER FOUR LOADERS: `refresh` HAS FIVE NON-EFFECT CALLERS, not one.
     * Besides Try again, the four write handlers (add, rename, enable/disable, delete) reload the table
     * afterwards, and TODAY THAT SHOWS THE LOADING STATE while the reloaded rows are fetched. Hoisting the
     * reset onto the retry path alone would have silently deleted that -- after a rename the table would sit
     * on the stale rows with no sign anything was happening, which is a real behaviour change smuggled in as
     * a lint fix. So `reloadChannels` is what all five call, and the effect alone calls the bare fetch.
     *
     * `RecruitmentChannel.reload.test.jsx` holds it: nothing asserted that flash before this phase.
     */
    const reloadChannels = useCallback(() => {
        setListState('loading');
        setLoadError(null);
        return refresh();
    }, [refresh]);

    useEffect(() => { refresh(); }, [refresh]);

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        return recruitmentChannels
            .filter(r => !q || r.name.toLowerCase().includes(q))
            .sort((a, b) => a.name.localeCompare(b.name));
    }, [recruitmentChannels, search]);

    /**
     * The three in-row writes, one shape (RF -- each held its own copy of this try/catch/finally): write, re-read
     * the table (with its loading state -- see `reloadChannels`), then the caller's own step, which runs only on
     * success, so a failed add keeps what was typed. A failure is the sentence `writeFailure` chooses.
     */
    const runWrite = async (what, write, then) => {
        setSaving(true);
        try {
            await write();
            await reloadChannels();
            then?.();
        } catch (err) {
            setNotice(writeFailure(err, what));
        } finally {
            setSaving(false);
        }
    };

    const handleAdd = async () => {
        const name = newName.trim();
        if (!name) return;
        await runWrite('That recruitment channel', () => recruitmentChannelService.create(name), () => {
            setNewName('');
            setShowAdd(false);
        });
    };

    const handleEdit = async (r) => {
        const name = editName.trim();
        if (!name) return;
        await runWrite(`The new name for ${r.name}`, () => recruitmentChannelService.update(r.id, { name, enabled: r.enabled }),
            () => setEditingId(null));
    };

    const handleToggleEnabled = (r) => runWrite(`The change to ${r.name}`,
        () => recruitmentChannelService.update(r.id, { name: r.name, enabled: !r.enabled }));

    // 🔴 `03.4`: was `window.confirm()`. That dialog is unstyleable, says only OK/Cancel
    // rather than naming what will happen, blocks the whole browser thread, and is
    // SUPPRESSIBLE -- a user who ticks "don't show me these again" silently loses every
    // confirmation in the application.
    const [pendingDelete, setPendingDelete] = useState(null);
    // A failed delete is said in the dialog that is still open (R4-F1): the page notice renders under its scrim.
    const [deleteNotice, setDeleteNotice] = useState(null);
    const askToDelete = (r) => { setDeleteNotice(null); setPendingDelete(r); };

    const handleDelete = async () => {
        const r = pendingDelete;
        if (!r) return;
        setSaving(true);
        setDeleteNotice(null);
        try {
            await recruitmentChannelService.remove(r.id);
            setPendingDelete(null);
            await reloadChannels();
        } catch (err) {
            reportError('RecruitmentChannel.remove', err);
            // R3-F2: only a refusal proves nothing was deleted; a lost answer may have deleted it.
            setDeleteNotice({
                message: writeMayHaveLanded(err)
                    ? `The delete of ${r.name} could not be confirmed. Reload the list and check before trying again.`
                    : `${r.name} was not deleted. ${messageOf(err)}`,
            });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Page>
            <PageTitle
                title="Recruitment Channel Maintenance"
                subtitle="The recruiter types a member can be signed up through."
            />

            {/* 🔴 WHAT FOUR `alert(err)` CALLS BECAME. Each passed a raw Error object, which
                prints "[object Error]" in some browsers -- so the member was shown a dialog
                that told them nothing, and told them it modally. */}
            {notice && (
                <PageNotice ref={noticeRef} id="channel-notice-title" className="max-w-4xl" onDismiss={() => setNotice(null)}>{notice}</PageNotice>
            )}

            {/* 🔴 THE SEARCH WAS A RAW <input> WITH NO VISIBLE LABEL, held together by an
                `aria-label`. Every other list in this application prints its criteria ON the
                page, in a `Criteria` row, because the mock does -- a filter you cannot see is
                a filter you cannot remember you set. */}
            <Criteria className="mt-5 max-w-4xl">
                <TextInput
                    className={CRITERION_WIDE}
                    label="Search"
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    placeholder="Any recruitment channel"
                />
                <Button variant="primary" onClick={() => { setShowAdd(true); setNewName(''); }}>
                    <UserPlus size={16} strokeWidth={1.75} aria-hidden="true" /> Add a channel
                </Button>
            </Criteria>

            <Sheet className="mt-4 max-w-4xl">
                {showAdd && (
                    <div className="flex items-center gap-2 border-b border-line bg-brand-soft p-3">
                        <input
                            ref={focusOnMount}
                            value={newName}
                            onChange={e => setNewName(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && handleAdd()}
                            aria-label="New recruitment channel name"
                            placeholder="Recruitment Channel name"
                            className={CELL_INPUT}
                        />
                        <button
                            type="button"
                            onClick={handleAdd}
                            disabled={!newName.trim() || saving}
                            aria-label="Add this recruitment channel"
                            className="touch-size rounded-md p-1.5 text-status-active transition hover-hover:hover:bg-status-active-bg disabled:opacity-40">
                            <Check size={16} />
                        </button>
                        <button
                            type="button"
                            onClick={() => setShowAdd(false)}
                            aria-label="Cancel adding a recruitment channel"
                            className="touch-size rounded-md p-1.5 text-ink-muted transition hover-hover:hover:bg-surface-2">
                            <X size={16} />
                        </button>
                    </div>
                )}

                {/* 🔴 THIS WAS THE LAST PSEUDO-TABLE IN THE APPLICATION. Four `<div>`s across
                    the top and a matching `grid-cols-[1fr_auto_auto_auto]` on every row: it
                    LOOKED like a table and carried none of a table's meaning, so a screen
                    reader read it as a stream of values with no column names, and nothing said
                    which of the two icon buttons did what to which row. `BankListing`,
                    `BankBranch` and `Recruiters` were each converted for this reason; this is
                    the one that was missed. The rows are inline-editable, so it is a
                    hand-built `<table>` in the world's own cell chrome rather than the shared
                    `Table` -- the same call, and the same reason, as `Subscriptions`. */}
                <TableScroller label="Recruitment channels">
                    <table className="w-full border-collapse text-sm">
                        <caption className="sr-only">
                            Recruitment channels, with each one&apos;s status and the controls to rename or delete it
                        </caption>
                        <thead className="bg-surface-2">
                            <tr>
                                {/* `w-full` on the NAME column: an auto-layout table shares its slack between all
                                    the columns, which left a 160px gap between the status chip and
                                    the two icon buttons and floated them in the middle of nowhere.
                                    The name is the column with something to say, so it takes the
                                    slack and everything else sits tight at the right edge. */}
                                <th scope="col" className={`${HEAD_CELL} w-full`}>Channel Name</th>
                                <th scope="col" className={HEAD_CELL}>Status</th>
                                {/* The two control columns are named for a screen reader and
                                    silent on screen: a visible "Edit"/"Delete" head over a pair
                                    of icon buttons is two words of furniture on every row. */}
                                <th scope="col" className="w-px px-3.5"><span className="sr-only">Rename</span></th>
                                <th scope="col" className="w-px px-3.5 pr-5"><span className="sr-only">Delete</span></th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                            {/* 🔑 THE THREE STATES ARE SEPARATE BRANCHES, and `error` can no
                                longer reach the empty copy -- which is the fix, not the states. */}
                            {listState !== 'ready' && (
                                <tr>
                                    <td colSpan={4}>
                                        {listState === 'loading' && <DataState state="loading" />}
                                        {listState === 'error' && (
                                            <DataState
                                                state="error"
                                                error={loadError}
                                                onRetry={reloadChannels}
                                                title="We could not load the recruiter types."
                                            />
                                        )}
                                    </td>
                                </tr>
                            )}

                            {listState === 'ready' && filtered.length === 0 && (
                                <tr>
                                    <td colSpan={4}>
                                        <DataState state="empty" title="No recruiter types found." />
                                    </td>
                                </tr>
                            )}

                            {listState === 'ready' && filtered.map(r => (
                                <tr key={r.id} className="transition hover-hover:hover:bg-surface-2/40">
                                    <td className="px-3.5 py-[11px] text-ink first:pl-5">
                                        {editingId === r.id ? (
                                            <input
                                                ref={focusOnMount}
                                                value={editName}
                                                onChange={e => setEditName(e.target.value)}
                                                aria-label={`Rename ${r.name}`}
                                                onKeyDown={e => {
                                                    if (e.key === 'Enter') handleEdit(r);
                                                    if (e.key === 'Escape') setEditingId(null);
                                                }}
                                                className={CELL_INPUT}
                                            />
                                        ) : (
                                            <span className={r.enabled ? '' : 'text-ink-muted line-through'}>{r.name}</span>
                                        )}
                                    </td>

                                    <td className="px-3.5 py-[11px]">
                                        <button
                                            type="button"
                                            onClick={() => handleToggleEnabled(r)}
                                            disabled={saving}
                                            aria-pressed={r.enabled}
                                            aria-label={`${r.name} is ${r.enabled ? 'active' : 'inactive'} — press to ${r.enabled ? 'disable' : 'enable'} it`}
                                            className={`touch-target rounded-full px-2 py-0.5 text-xs font-semibold transition disabled:opacity-40 ${
                                                r.enabled
                                                    ? 'bg-status-active-bg text-status-active hover-hover:hover:bg-status-active-dot hover-hover:hover:text-on-brand'
                                                    : 'bg-surface-2 text-ink-muted hover-hover:hover:bg-surface'
                                            }`}>
                                            {r.enabled ? 'Active' : 'Inactive'}
                                        </button>
                                    </td>

                                    <td className="px-3.5 py-[11px]">
                                        {editingId === r.id ? (
                                            <div className="flex items-center gap-1">
                                                <button
                                                    type="button"
                                                    onClick={() => handleEdit(r)}
                                                    disabled={!editName.trim() || saving}
                                                    aria-label={`Save the new name for ${r.name}`}
                                                    className="touch-size rounded-md p-1.5 text-status-active transition hover-hover:hover:bg-status-active-bg disabled:opacity-40">
                                                    <Check size={14} />
                                                </button>
                                                <button
                                                    type="button"
                                                    onClick={() => setEditingId(null)}
                                                    aria-label="Stop renaming and keep the current name"
                                                    className="touch-size rounded-md p-1.5 text-ink-muted transition hover-hover:hover:bg-surface-2">
                                                    <X size={14} />
                                                </button>
                                            </div>
                                        ) : (
                                            <button
                                                type="button"
                                                onClick={() => { setEditingId(r.id); setEditName(r.name); }}
                                                disabled={saving}
                                                aria-label={`Rename ${r.name}`}
                                                className="touch-size rounded-md p-1.5 text-brand-ink transition hover-hover:hover:bg-brand-soft disabled:opacity-40">
                                                <Pencil size={14} />
                                            </button>
                                        )}
                                    </td>

                                    <td className="px-3.5 py-[11px] pr-5">
                                        <button
                                            type="button"
                                            onClick={() => askToDelete(r)}
                                            disabled={saving}
                                            aria-label={`Delete ${r.name}`}
                                            className="touch-size rounded-md p-1.5 text-status-suspended transition hover-hover:hover:bg-status-suspended-bg disabled:opacity-40">
                                            <Trash2 size={14} />
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </TableScroller>
            </Sheet>

            {/* 🔴 A COUNT IS A FACT ABOUT DATA THAT ARRIVED. This line sat outside all three
                state branches, so a failed load printed "0 recruiter types" directly beneath
                "We could not load the recruiter types." The file's own header comment above
                describes fixing exactly this class of defect in the list BODY, and left it
                standing in the footer. It renders only on a resolved load now. */}
            {listState === 'ready' && (
                <p className="mt-3 max-w-4xl text-right text-xs text-ink-muted">
                    {filtered.length} recruiter type{filtered.length !== 1 ? 's' : ''}
                </p>
            )}

            <ConfirmDialog
                open={Boolean(pendingDelete)}
                onOpenChange={closeOn(() => setPendingDelete(null))}
                title="Delete recruiter type"
                description={pendingDelete ? `"${pendingDelete.name}" will be removed from the list of recruiter types.` : undefined}
                confirmLabel="Delete recruiter type"
                loading={saving}
                onConfirm={handleDelete}
            >
                <DialogNotice notice={deleteNotice} onDismiss={() => setDeleteNotice(null)} />
            </ConfirmDialog>
        </Page>
    );
}
