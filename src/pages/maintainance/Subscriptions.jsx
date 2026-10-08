import { Pencil } from 'lucide-react';
import PageTitle from '../../components/common/PageTitle';
import Criteria, { CRITERION, CRITERION_WIDE } from '../../components/common/Criteria.jsx';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import { Button, SearchableSelect, SelectInput } from '../../components/form';
import { formatDateLong, formatZar } from '../../lib/format.js';
import { subscriptionsService } from '../../services/subscriptionsService';
import { bankService } from '../../services/bankService.js';
import { useState, useEffect, useMemo } from 'react';
import { useLocation } from 'react-router-dom';
import StatusChip from "../../components/common/StatusChip.jsx";
import TableScroller from "../../components/table/TableScroller.jsx";
import { HEAD_CELL } from "../../components/table/Table.jsx";
import DataState from "../../components/common/DataState.jsx";
import useReferenceData from "../../hooks/useReferenceData.js";
import reportError from "../../lib/reportError.js";
import { useAuth } from "../../auth/useAuth";
import RequirePermission from "../../auth/RequirePermission";
import { PERMISSIONS } from "../../auth/permissions";

// 🔴 `text-sm` WAS 13px, WHICH IS THE iOS ZOOM TRAP, AND THE 16px CHECK COULD NOT SEE
// IT. That check matches `text-sm` on the TAG; these classes arrive through this variable,
// so four controls sat under the floor while the measurement read zero. `field-text` is
// the design's size on a fine pointer and 16px on a coarse one, which is the whole reason
// the utility exists.
// `touch-size`: an in-row input is a replaced element, so it GROWS to 44px on a coarse pointer (`07.2`; index.css).
const inputCls = 'w-full px-2 py-1 rounded border border-line-strong bg-surface-2 text-ink field-text touch-size';

/** A body cell of this hand-built table: card.css `.table td`'s padding, the first column at 20px. */
const BODY_CELL = 'px-3.5 py-[11px] first:pl-5';

/**
 * The bank list behind this screen's filter. `06.1b`, EH-D9.
 *
 * 🔴 IT LOADED WITH `.catch(() => setBankNames([]))`. A filter whose options silently
 * vanish is quieter than most: the administrator simply does not filter, sees the whole
 * list, and never learns that the control was broken rather than empty.
 */
const REFERENCE_LOADERS = {
    banks: () => bankService.getBankNames(),
};

/** A number as its field holds it: its digits, or nothing. */
const textOf = (value) => (value === null || value === undefined || Number.isNaN(value) ? '' : String(value));

/**
 * What a changed row may not be saved with, field by field, in the server's own words (its PUT is @Valid): a debit day
 * from 1 to 31, and an amount above R0. Empty when the row may be saved.
 */
const refusalsOf = (row) => {
    const refused = {};
    const day = String(row.acbDebitDay ?? '').trim();
    if (!/^\d{1,2}$/.test(day) || Number(day) < 1 || Number(day) > 31) {
        refused.acbDebitDay = 'Debit day must be between 1 and 31.';
    }
    const amount = String(row.subscriptionAmount ?? '').trim();
    if (amount === '' || !Number.isFinite(Number(amount))) refused.subscriptionAmount = 'Subscription amount is required.';
    else if (Number(amount) <= 0) refused.subscriptionAmount = 'Subscription amount must be more than R0.';
    return refused;
};

export default function Subscriptions() {
    const location = useLocation();

    const [filters, setFilters] = useState({
        bankName: location.state?.filters?.bankName || '',
        isCoreBanking: '',
    });
    const [allSubscriptions, setAllSubscriptions] = useState([]);
    // 🔴 IT STARTED `false`, SO THE FIRST FRAME SAID "No subscriptions match your search." -- a confident
    // empty answer to a request not yet sent (`06.4`). The mount effect sets it too; this is the frame before.
    const [loading, setLoading] = useState(true);
    // 🔴 A FAILED SAVE IS NOT A FAILED LOAD. It went into `error`, which replaced the whole list with "We could
    // not load the subscriptions." -- the rows that had loaded vanished, and the change that failed was never
    // mentioned (`06.4`). It is its own notice now, and the row stays open with the change in it.
    const [saveError, setSaveError] = useState(null);
    const [error, setError] = useState(null);
    // Bumped by the retry control, and a dependency of the load effect, so "try again"
    // re-runs exactly the effect that failed rather than duplicating its body.
    const [attempt, setAttempt] = useState(0);
    const [editingId, setEditingId] = useState(null);
    const [editData, setEditData] = useState({});
    const refs = useReferenceData(REFERENCE_LOADERS);
    const bankNames = refs.data.banks;
    // 🔒 Only staff change amounts (the 5 Oct Show & Tell): the server refuses the writes without
    // SUBSCRIPTIONS_MAINTAIN, so the screen offers no edit it would refuse, and says what is needed instead.
    const { can } = useAuth();
    const mayChange = can(PERMISSIONS.SUBSCRIPTIONS_MAINTAIN);

    /* ♻️ The private date formatter is gone. It printed the wire's own `2026-09-09` for an
       array and the browser's locale string for anything else -- two shapes on one screen,
       neither of them the design's. `formatDateLong` is the one every other surface reads,
       and it already knows the API's `[y, m, d]`. */

    /*
     * 🔴 THE TWO RESETS MOVED TO THE RETRY CONTROL (`10.4`), which is the only thing that can
     * bump `attempt`. `react-hooks/set-state-in-effect` is an error as of this phase.
     *
     * 🔑 NOTHING MOVES ON SCREEN, and the reason is worth stating rather than trusting: on mount
     * both were no-ops (`loading` initialises to `true`, `error` to `null`), and on a retry they
     * now run in the SAME RENDER as the `attempt` bump that re-triggers this effect -- so the
     * committed frames are identical either way. The effect that follows sets state only from
     * its own promise, which is what the rule is asking for.
     *
     * ⚠️ `setError(null)` IS STILL LOAD-BEARING and must stay on that path: nothing else ever
     * unsets `error`, so a SUCCESSFUL retry without it would leave the failure banner sitting
     * above the list it had just loaded.
     */
    useEffect(() => {
        subscriptionsService.getAll()
            .then(data => setAllSubscriptions(data))
            // 🔴 IT WAS `setError(String(err))`, WHICH RENDERS "Error: Error: ..." ON SCREEN.
            // EH-D12: what happened and what to do next, in the reading voice, and no codes in
            // the sentence. The error OBJECT is kept rather than stringified, so `DataState`
            // can read `retryable` off it and put `kind`/`status` behind the details line.
            .catch(setError)
            .finally(() => setLoading(false));
    }, [attempt]);


    const subscriptions = useMemo(() => {
        let result = allSubscriptions;
        if (filters.bankName) {
            const q = filters.bankName.toLowerCase();
            result = result.filter(s =>
                s.bankName?.toLowerCase().includes(q) || q.includes(s.bankName?.toLowerCase() ?? '')
            );
        }
        if (filters.isCoreBanking !== '') {
            const isCore = filters.isCoreBanking === 'true';
            result = result.filter(s => s.isCoreBanking === isCore);
        }
        return result;
    }, [allSubscriptions, filters.bankName, filters.isCoreBanking]);

    /*
     * 🔴 AN EMPTIED NUMBER WAS SAVED AS 0 AND SHOWN AS "NaN" (the manual's reading of Maintenance, 6 Oct). Each field
     * was `parseInt`/`parseFloat` of what was typed, so emptying the debit day stored NaN; JSON wrote it as null, the
     * server's whole-number field took that as 0 and saved it, and the row read "NaN". The two numbers are held as typed
     * now and checked before anything is sent -- the server holds the same rules since (PUT is @Valid).
     */
    const [fieldErrors, setFieldErrors] = useState({});

    const handleRowClick = (sub) => {
        if (!mayChange || editingId === sub.id) return;
        setEditingId(sub.id);
        setEditData({ ...sub, acbDebitDay: textOf(sub.acbDebitDay), subscriptionAmount: textOf(sub.subscriptionAmount) });
        setFieldErrors({});
    };

    const handleCancel = (e) => {
        e.stopPropagation();
        setSaveError(null);
        setFieldErrors({});
        setEditingId(null);
        setEditData({});
    };

    const handleSave = async (e) => {
        e.stopPropagation();
        setSaveError(null);
        const refused = refusalsOf(editData);
        setFieldErrors(refused);
        if (Object.keys(refused).length > 0) return;
        const row = { ...editData, acbDebitDay: Number(editData.acbDebitDay), subscriptionAmount: Number(editData.subscriptionAmount) };
        try {
            await subscriptionsService.update(editingId, row);
            setAllSubscriptions(prev => prev.map(s => s.id === editingId ? row : s));
            setEditingId(null);
            setEditData({});
        } catch (err) {
            reportError('Subscriptions.save', err);
            setSaveError({ bankName: editData.bankName, error: err });
        }
    };

    const handleField = (field, value) => {
        setEditData(prev => ({ ...prev, [field]: value }));
        setFieldErrors(prev => (prev[field] ? { ...prev, [field]: undefined } : prev));
    };

    /** The row being edited, by name, for its controls' labels -- spelled five times before (RF). */
    const editName = editData.bankName || 'this subscription';

    return (
        <Page>
            <PageTitle title="Subscriptions" subtitle="Each bank's subscription: the debit day, the amount, and whether it runs through core banking." />

            <Criteria className="mt-5">
                <SearchableSelect
                    className={CRITERION_WIDE}
                    label="Bank"
                    value={filters.bankName}
                    onChange={(v) => setFilters(prev => ({ ...prev, bankName: v }))}
                    placeholder="All banks"
                    {...refs.field('banks')}
                    options={[{ value: '', label: 'All banks' }, ...bankNames.map(b => ({ value: b, label: b }))]}
                />
                <SelectInput
                    className={CRITERION}
                    label="Core banking"
                    value={filters.isCoreBanking}
                    onChange={(v) => setFilters(prev => ({ ...prev, isCoreBanking: v }))}
                    options={[{ value: '', label: 'Any' }, { value: 'true', label: 'Yes' }, { value: 'false', label: 'No' }]}
                />
            </Criteria>

            <Sheet className="mt-4">

                {!mayChange && (
                    <div className="mx-5 mt-4">
                        <RequirePermission permission={PERMISSIONS.SUBSCRIPTIONS_MAINTAIN} />
                    </div>
                )}

                {/* 🔴 A STATUS THAT DECIDES WHO IS DEBITED, SAID NOWHERE (the manual's reading of Maintenance, 6 Oct): a
                    debit order run collects at a bank only while its row is ACTIVE (`AcbRuns.unambiguousBanks`, the
                    run's NO_SUBSCRIPTION_ROW otherwise), and two ACTIVE rows for one bank stop the run. */}
                <p id="subscription-status-meaning" className="mx-5 mt-4 text-sm text-ink-muted">
                    Debit orders are collected at a bank only while its row is <strong className="font-medium text-ink">ACTIVE</strong>.
                    Any other status leaves that bank&rsquo;s members out of the debit order run, and a bank with two ACTIVE rows
                    stops the run until one is changed.
                </p>

                {/* 🔴 IT WAS A BARE `Error: {String(err)}` BANNER WITH NO WAY OUT. Two things
                    changed: the sentence is written for the person reading it rather than a
                    stringified Error, and there is a retry -- an error state whose only affordance is
                    reloading the page is a dead end (EH-D4). */}
                {error && (
                    <div className="mx-5 mt-4 rounded-lg bg-surface-2 p-3">
                        <DataState
                            state="error"
                            error={error}
                            onRetry={() => { setLoading(true); setError(null); setAttempt((n) => n + 1); }}
                            title="We could not load the subscriptions."
                        />
                    </div>
                )}

                {/* The fourth state -- the list loaded, its filter's options did not -- is the Bank field's own
                    now (`06.4`): the field says it could not be loaded and offers the retry where the choice is
                    made, so the sheet no longer repeats it. */}
                {saveError && (
                    <div className="mx-5 mt-4 rounded-lg bg-surface-2 p-3">
                        <DataState
                            state="error"
                            layout="row"
                            compact
                            error={saveError.error}
                            title={`The changes to ${saveError.bankName || 'this subscription'} were not saved.`}
                            message="The row is still open with your changes. Check them and save again."
                        />
                    </div>
                )}

                {/* 🔴 THE INNER `max-h-[60dvh]` SCROLLER IS GONE, 2026-09-10. A sheet whose
                    own box scrolls ends mid-row with the next one sliced in half, which is
                    what the finish review's frame showed: the page already scrolls, and a
                    sheet in this world ends where its rows end. (The `dvh` note it carried
                    was right about `vh` and is kept in conventions §6; there is simply no
                    viewport-height box here any more.) */}
                <div className="relative">
                    {/* 🔑 THE THIRD HAND-ROLLED SPINNER, RETIRED. `06.2`.
                        It was a bordered div with `animate-spin` and its own copy -- one of
                        three separate spinners in the tree, none of which agreed about size,
                        colour or wording, and this one had no `motion-reduce` handling at
                        all. Through `DataState` it inherits the shared loading state AND the
                        5s "taking longer than expected" notice (EH-D2), which a hand-rolled
                        spinner can never grow. */}
                    {loading && (
                        <div className="absolute inset-0 bg-surface/60 z-20 flex items-center justify-center backdrop-blur-[1px]">
                            <DataState state="loading" title="Retrieving subscriptions…" />
                        </div>
                    )}

                    {/* 🔴 THE ONE `<table>` THE PLAN NAMED AS UNWRAPPED, wrapped through the
                        SHARED scroller rather than by hand. blastRadius.md is explicit that
                        hand-wrapping this one file is the wrong fix -- it is how MobiZone's
                        worst responsive defect happened, because the next table anybody
                        adds has to remember. */}
                    <TableScroller label="Bank subscriptions">
                    <table className="w-full border-collapse text-sm">
                        <caption className="sr-only">Bank subscriptions and their debit details</caption>
                        <thead className="sticky top-0 bg-surface-2 z-10">
                            <tr>
                                <th className={HEAD_CELL}>Bank Name</th>
                                <th className={HEAD_CELL}>Debit Order Day</th>
                                <th className={HEAD_CELL}>Amount</th>
                                <th className={HEAD_CELL}>Core Banking</th>
                                <th className={HEAD_CELL}>Status</th>
                                <th className={HEAD_CELL}>Registration Date</th>
                                <th className="whitespace-nowrap px-3.5 pb-[11px] pt-[13px] text-center text-xs font-semibold uppercase tracking-[0.08em] text-head-ink">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-line">
                            {/* 🔴 `!error` IS THE FIX, AND ITS ABSENCE WAS A LIVE A27 THE
                                REGISTER NEVER NAMED. Without it a failed load rendered the
                                error banner AND "No subscriptions match your search" at the
                                same time -- so the screen told an administrator both that
                                something had gone wrong and that their search was simply too
                                narrow, and the second sentence is the one that invites them
                                to widen the filter and try again forever. conventions §8: the
                                empty copy renders ONLY when data genuinely resolved empty. */}
                            {error ? null : !loading && subscriptions.length === 0 ? (
                                <tr>
                                    <td colSpan={7} className="px-4 py-6 text-center text-sm text-ink-muted">
                                        No subscriptions match your search.
                                    </td>
                                </tr>
                            ) : subscriptions.map((sub) => (
                                <tr
                                    key={sub.id}
                                    onClick={() => handleRowClick(sub)}
                                    className={`${mayChange ? 'cursor-pointer ' : ''}transition ${editingId === sub.id ? 'bg-brand-soft' : 'hover-hover:hover:bg-surface-2/50'}`}
                                >
                                    {editingId === sub.id ? (
                                        <>
                                            <td className={`${BODY_CELL} text-ink`}>{editData.bankName}</td>
                                            <td className={BODY_CELL}>
                                                <input type="number" min="1" max="31" step="1" required aria-label={`Debit order day for ${editName}`} aria-invalid={Boolean(fieldErrors.acbDebitDay)} aria-describedby={fieldErrors.acbDebitDay ? 'subscription-day-error' : undefined} className={inputCls} value={editData.acbDebitDay ?? ''} onChange={(e) => handleField('acbDebitDay', e.target.value)} onClick={e => e.stopPropagation()} />
                                                {fieldErrors.acbDebitDay && <p id="subscription-day-error" role="alert" className="mt-1 text-xs text-status-suspended">{fieldErrors.acbDebitDay}</p>}
                                            </td>
                                            <td className={BODY_CELL}>
                                                <input type="number" step="0.01" min="0.01" required aria-label={`Subscription amount for ${editName}`} aria-invalid={Boolean(fieldErrors.subscriptionAmount)} aria-describedby={fieldErrors.subscriptionAmount ? 'subscription-amount-error' : undefined} className={inputCls} value={editData.subscriptionAmount ?? ''} onChange={(e) => handleField('subscriptionAmount', e.target.value)} onClick={e => e.stopPropagation()} />
                                                {fieldErrors.subscriptionAmount && <p id="subscription-amount-error" role="alert" className="mt-1 text-xs text-status-suspended">{fieldErrors.subscriptionAmount}</p>}
                                            </td>
                                            <td className={BODY_CELL}>
                                                <select aria-label={`Core banking for ${editName}`} className={inputCls} value={editData.isCoreBanking === true ? 'true' : 'false'} onChange={(e) => handleField('isCoreBanking', e.target.value === 'true')} onClick={e => e.stopPropagation()}>
                                                    <option value="true">Yes</option>
                                                    <option value="false">No</option>
                                                </select>
                                            </td>
                                            <td className={BODY_CELL}>
                                                <select aria-label={`Status for ${editName}`} aria-describedby="subscription-status-meaning" className={inputCls} value={editData.status || ''} onChange={(e) => handleField('status', e.target.value)} onClick={e => e.stopPropagation()}>
                                                    <option value="ACTIVE">ACTIVE</option>
                                                    <option value="PENDING">PENDING</option>
                                                    <option value="CANCELLED">CANCELLED</option>
                                                    <option value="INACTIVE">INACTIVE</option>
                                                </select>
                                            </td>
                                            <td className={`${BODY_CELL} text-ink-muted`}>{formatDateLong(sub.registrationDate)}</td>
                                            {/* ♻️ The two hand-rolled buttons are the primitive's now: one
                                                focus ring, one hit area, one hover rule, and the primary
                                                LAST, as every other surface in this world orders them. */}
                                            <td className="whitespace-nowrap px-3.5 py-[11px] text-right first:pl-5">
                                                <span className="inline-flex gap-2">
                                                    <Button size="sm" onClick={handleCancel} aria-label="Discard these changes">Cancel</Button>
                                                    <Button size="sm" variant="primary" onClick={handleSave} aria-label={`Save changes to ${editName}`}>Save</Button>
                                                </span>
                                            </td>
                                        </>
                                    ) : (
                                        <>
                                            {/* A subscription whose bank never arrived is a fact worth
                                                seeing: an em dash says "no bank on this row", a blank
                                                cell says nothing and reads as a rendering fault. */}
                                            <td className="px-3.5 py-[11px] font-medium text-ink first:pl-5">{sub.bankName || '—'}</td>
                                            <td className={`${BODY_CELL} tabular-nums`}>{sub.acbDebitDay ?? '—'}</td>
                                            <td className={`${BODY_CELL} tabular-nums`}>{formatZar(sub.subscriptionAmount)}</td>
                                            <td className={`${BODY_CELL} text-ink-muted`}>{sub.isCoreBanking ? 'Yes' : 'No'}</td>
                                            <td className={BODY_CELL}>
                                                {/* ♻️ THE FOURTH COPY OF THE STATUS CHIP, and the one
                                                    that was wrong in the most damaging direction: it knew
                                                    ACTIVE and PENDING and painted EVERYTHING ELSE with the
                                                    Suspended red -- so a CANCELLED subscription and an
                                                    INACTIVE one both read as suspended, which is a different
                                                    fact about a member's money.

                                                    🔴 The vocabulary here is UPPERCASE (`ACTIVE`) where the
                                                    member register's is title case (`Active`), so the shared
                                                    component is given the shape it knows rather than being
                                                    taught a second spelling. The mismatch itself is real and
                                                    is on the wire, not ours to change here. */}
                                                <StatusChip status={sub.status} />
                                            </td>
                                            <td className={`${BODY_CELL} text-ink-muted`}>{formatDateLong(sub.registrationDate)}</td>
                                            {/* 🔴 IT SAID "click to edit" -- a hint where a control belongs,
                                                and the only way in was a mouse on the row. The row still
                                                opens on a click as a pointer convenience; this is the
                                                control a keyboard reaches, named for the bank it edits. */}
                                            <td className="whitespace-nowrap px-3.5 py-[11px] text-right first:pl-5">
                                                {mayChange && (
                                                    <Button
                                                        size="sm"
                                                        variant="ghost"
                                                        onClick={(e) => { e.stopPropagation(); handleRowClick(sub); }}
                                                        aria-label={`Edit the subscription for ${sub.bankName || 'this bank'}`}
                                                    >
                                                        <Pencil size={14} strokeWidth={1.75} aria-hidden="true" /> Edit
                                                    </Button>
                                                )}
                                            </td>
                                        </>
                                    )}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    </TableScroller>
                </div>
            </Sheet>
        </Page>
    );
}
