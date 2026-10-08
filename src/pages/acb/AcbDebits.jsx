import { useState, useCallback, useMemo, useEffect, useRef } from "react";
import { Search } from "lucide-react";
import PageTitle from "../../components/common/PageTitle.jsx";
import Criteria, { CRITERION, CRITERION_WIDE } from "../../components/common/Criteria.jsx";
import DataState from "../../components/common/DataState.jsx";
import { TextInput, SelectInput, Button } from "../../components/form";
import Modal, { ModalClose } from "../../components/overlay/Modal.jsx";
import { memberService } from "../../services/memberService.js";
import { acbRunService } from "../../services/acbRunService.js";
import { validateSAID } from "../../utils/validateSAID.js";
import Table from '../../components/table/Table.jsx';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import StatusChip from '../../components/common/StatusChip.jsx';
import reportError from '../../lib/reportError.js';
import { formalName } from '../../lib/names.js';
import useFocusOn from '../../hooks/useFocusOn.js';
import DialogNotice, { PageNotice } from '../../components/common/DialogNotice.jsx';
import { messageOf, writeMayHaveLanded } from '../../services/parseApiError.js';
import { formatZar } from '../../lib/format.js';
import { ACCOUNT_TYPE_OPTIONS } from '../../config/memberConstants.js';
import AcbUnpaids from './AcbUnpaids.jsx';
import AcbHeldOver from './AcbHeldOver.jsx';
import AcbHeldLetters from './AcbHeldLetters.jsx';
import AcbTerminationReport from './AcbTerminationReport.jsx';
import { PagerFor } from './acbPaging.jsx';
import { DEBIT_DAYS, asPage, debitDayReplaced, debitsRuns, maskedAccount, ordinal } from './acbRunRules.js';

const SEARCH_PAGE_SIZE = 25;
const NO_CRITERIA = { surname: "", membershipNumber: "", identityNumber: "" };
const DAY_OPTIONS = DEBIT_DAYS.map((day) => ({ value: String(day), label: ordinal(day) }));

/**
 * *Manage Debits* -- the Re-Debits screen (`04.1`).
 *
 * The newest run whose file went to the bank leads: its unpaids, EVERY one, each with its decision
 * (`AcbUnpaids`); then the next run's second re-debits held over for the member's permission
 * (`AcbHeldOver`, D32) and the run's letters that could not go (`AcbHeldLetters`, Q46, EH-E). The
 * member's banking sheet and *Edit Details* stay -- for the *Invalid Account Number* and *Account
 * Closed* unpaids, and for any member found by the search.
 *
 * 🔒 THE SEARCH IS THE SERVER'S (R-SEC-B, R-PERF-D). This screen used to call `getMembers()` on mount,
 * which shipped every member's banking fields to the browser -- and would have shipped 65,000 of them.
 * It now asks `POST /api/acb/members/search` for one page, and the answer carries each account's last
 * four digits and no identity number. The full record is ONE member's, loaded when chosen.
 */
export default function AcbDebits() {
    const [notice, setNotice] = useState(null);
    const noticeRef = useFocusOn(notice);

    // ---- one member's banking: an unpaid's member, or one found by the search ----------------------
    const [selectedMember, setSelectedMember] = useState(null);
    const [loadingId, setLoadingId] = useState(null);
    const sheetRef = useFocusOn(selectedMember?.id);

    // ---- the runs this screen works on ------------------------------------------------------------
    const [runsState, setRunsState] = useState('loading');
    const [runs, setRuns] = useState({ returned: null, next: null });
    const loadRuns = useCallback(() => acbRunService.list(0, 12)
        .then((page) => { setRuns(debitsRuns(asPage(page).content)); setRunsState('ready'); })
        .catch((err) => { reportError('AcbDebits.loadRuns', err); setRunsState('error'); }), []);
    const retryRuns = useCallback(() => { setRunsState('loading'); loadRuns(); }, [loadRuns]);
    useEffect(() => { loadRuns(); }, [loadRuns]);

    // ---- the member search ------------------------------------------------------------------------
    const [filters, setFilters] = useState(NO_CRITERIA);
    const [idError, setIdError] = useState(null);
    const [searchHint, setSearchHint] = useState(null);
    // What was searched for -- the criteria the results belong to -- or null before a search.
    const [searched, setSearched] = useState(null);
    const [results, setResults] = useState(() => asPage(null));
    const [listState, setListState] = useState('loading');
    const searchTicket = useRef(0);

    /**
     * 🔴 A COUNT IS A FACT ABOUT DATA THAT ARRIVED. Only the newest search may paint -- an answer to an
     * earlier one arriving late must not put its members under the newer question -- and the count and
     * the empty copy render only once the answer resolved (conventions §8).
     */
    const runSearch = useCallback((criteria, page) => {
        const mine = ++searchTicket.current;
        setListState('loading');
        return acbRunService.searchMembers(criteria, page, SEARCH_PAGE_SIZE)
            .then((data) => { if (mine === searchTicket.current) { setResults(asPage(data)); setListState('ready'); } })
            .catch((err) => { reportError('AcbDebits.search', err); if (mine === searchTicket.current) setListState('error'); });
    }, []);

    const handleSearch = () => {
        if (idError) return;
        const criteria = {
            surname: filters.surname.trim(),
            membershipNumber: filters.membershipNumber.trim(),
            identityNumber: filters.identityNumber.trim(),
        };
        if (!criteria.surname && !criteria.membershipNumber && !criteria.identityNumber) {
            // An untouched search box is a question nobody has asked yet -- never "everyone".
            setSearchHint('Type a surname, a membership number or an identity number to search.');
            return;
        }
        setSearchHint(null);
        setSearched(criteria);
        runSearch(criteria, 0);
    };
    const handleClear = () => {
        searchTicket.current += 1;
        setFilters(NO_CRITERIA);
        setIdError(null); setSearchHint(null); setSearched(null); setSelectedMember(null);
    };
    const handleChange = (field) => (e) => {
        const val = e.target.value;
        setFilters(p => ({ ...p, [field]: val }));
        setSearched(null);
        setSearchHint(null);
        if (field === "identityNumber") setIdError(val ? validateSAID(val) : null);
    };
    const goToResults = (pageNumber) => { if (searched) runSearch(searched, pageNumber - 1); };

    // Stable, because `memberColumns` closes over it: a callback rebuilt every render
    // rebuilds the column list every render, which rebuilds every cell.
    const handleSelectMember = useCallback((m) => {
        if (selectedMember?.id === m.id) { setSelectedMember(null); return; }
        setLoadingId(m.id);
        memberService.getMemberById(m.id)
            .then(full => { setSelectedMember(full); setNotice(null); })
            // 🔴 A FAILED RECORD FETCH USED TO OPEN THE REGISTER ROW AS IF IT WERE THE RECORD (`06.4`). The row
            // is the list's summary: a banking field it does not carry printed "-", and Edit Details seeded its
            // form with blanks that Update would write. Now nothing is opened -- not this member, and not the
            // one open before -- and the page says why.
            .catch((err) => {
                reportError('AcbDebits.loadMember', err);
                setSelectedMember(null);
                setNotice(`The banking details for ${m.membershipNumber || 'that member'} could not be loaded, so nothing is shown for them. Select the member again to try again.`);
            })
            .finally(() => setLoadingId(null));
    }, [selectedMember]);

    // An unpaid's *Banking* opens the same sheet, for the member the line debited.
    const openBankingFor = useCallback((line) => {
        handleSelectMember({ id: line.memberId, membershipNumber: line.membershipNumber });
    }, [handleSelectMember]);

    // Edit modal state
    const [editing, setEditing] = useState(false);
    const [bankForm, setBankForm] = useState({});
    const [saving, setSaving] = useState(false);
    // A failed update is said in the dialog, which stays open on a failure (R4-F1): the page notice renders under its scrim.
    const [bankingNotice, setBankingNotice] = useState(null);

    const openEdit = () => {
        if (!selectedMember) return;
        setBankForm({
            bankName: selectedMember.bankName || "",
            branch: selectedMember.branch || "",
            bankAccountNumber: selectedMember.bankAccountNumber || "",
            accountType: selectedMember.accountType || "",
            ibtNo: selectedMember.ibtNo || "",
            bankAddress: selectedMember.bankAddress || "",
            workBankAddress: selectedMember.workBankAddress || "",
            paymentType: selectedMember.paymentType || "",
            debitDay: selectedMember.debitDay == null ? "" : String(selectedMember.debitDay),
        });
        setBankingNotice(null);
        setEditing(true);
    };

    const handleBankChange = (field) => (e) => setBankForm(f => ({ ...f, [field]: e.target.value }));

    const saveBanking = async () => {
        if (!selectedMember) return;
        setSaving(true);
        setBankingNotice(null);
        try {
            const updated = await memberService.updateBanking(selectedMember.id, bankForm);
            setSelectedMember(updated);
            setEditing(false);
            // The day on screen is the day the server kept -- and when it is not the day chosen, the page says so.
            const replaced = debitDayReplaced(bankForm.debitDay, updated?.debitDay);
            if (replaced) setNotice(replaced);
        } catch (err) {
            reportError('AcbDebits.updateBanking', err);
            // 🔴 A BANKING CHANGE THAT FAILED, ANNOUNCED IN A DIALOG THE USER DISMISSES.
            // This screen writes a member's debit-order details; "Failed to update banking
            // details" arriving as a native modal, and leaving no trace once dismissed, is
            // the worst place in the application for that pattern.
            // R3-F2: and "Nothing was written" is true only of a refusal. After a timeout, a lost connection or a 5xx
            // the change may have landed, and re-submitting a debit-order change whose outcome is unknown is the harm.
            setBankingNotice({
                message: writeMayHaveLanded(err)
                    ? 'This banking change could not be confirmed. It may have been saved — reload the member and check their details before trying again.'
                    : `Those banking details were not saved. Nothing was written — ${messageOf(err)}`,
            });
        } finally {
            setSaving(false);
        }
    };

    // A day on record outside the window is shown, never silently replaced: the choice starts blank and says why.
    const dayOutsideWindow = Boolean(bankForm.debitDay) && !DEBIT_DAYS.includes(Number(bankForm.debitDay));

    /**
     * 🔴 THE ROW WAS A `<div onClick>` INSIDE A `grid-cols-5`. Selecting a member for an
     * ACB banking change -- which is what this screen exists to do -- was mouse-only: no
     * tab stop, no accessible name, no Enter. `Table` offers no `onRowClick` at all, so
     * the action is a real button on the membership number.
     *
     * 📱 R-RESP-K: the bank and the masked account drop into the row's detail panel at 390px.
     */
    const memberColumns = useMemo(() => [
        {
            key: 'membershipNumber',
            header: 'Membership No.',
            priority: 'essential',
            cell: (m) => (
                <button
                    type="button"
                    onClick={() => handleSelectMember(m)}
                    aria-pressed={selectedMember?.id === m.id}
                    aria-label={`${m.membershipNumber || 'This member'} — load banking details for ${formalName(m) || 'this member'}`}
                    className="touch-target inline-flex items-center gap-2 rounded-sm font-medium text-brand-ink underline-offset-2 hover-hover:hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring"
                >
                    {m.membershipNumber}
                    {loadingId === m.id && (
                        <span
                            role="status"
                            aria-label="Loading banking details"
                            className="inline-block size-3 animate-spin rounded-full border-2 border-brand border-t-transparent motion-reduce:animate-none"
                        />
                    )}
                </button>
            ),
        },
        {
            key: 'name',
            header: 'Name',
            priority: 'essential',
            truncate: true,
            cell: (m) => formalName(m) || '—',
        },
        { key: 'bankName', header: 'Bank', priority: 'secondary', truncate: true, cell: (m) => m.bankName || '—' },
        { key: 'account', header: 'Account', priority: 'secondary', cell: (m) => maskedAccount(m.accountLastFour) },
        { key: 'debitDay', header: 'Debit Day', priority: 'secondary', cell: (m) => (m.debitDay ? ordinal(m.debitDay) : '—') },
        { key: 'status', header: 'Status', priority: 'essential', cell: (m) => <StatusChip status={m.status} /> },
    ], [loadingId, selectedMember, handleSelectMember]);

    const count = results.totalElements;
    return (
        <Page>
            <PageTitle title="Manage Debits" subtitle="Unpaid debits presented again, and banking corrected" />

            {/* 🔴 WHAT THE `alert()` BECAME. Focused rather than `role="alert"`: the
                form primitives already give inline messages that role, and stacking a
                page-level live region on top is two announcements for one failure. */}
            {notice && (
                <PageNotice ref={noticeRef} id="acb-notice-title" onDismiss={() => setNotice(null)}>{notice}</PageNotice>
            )}

            <div className="max-w-5xl">
                {runsState !== 'ready' ? (
                    <Sheet className="mt-6 p-4">
                        <DataState
                            state={runsState === 'error' ? 'error' : 'loading'}
                            title={runsState === 'error' ? 'The runs could not be loaded' : 'Loading the latest run'}
                            onRetry={retryRuns}
                        />
                    </Sheet>
                ) : runs.returned ? (
                    <AcbUnpaids run={runs.returned} onBanking={openBankingFor} />
                ) : (
                    <Sheet className="mt-6 px-6 py-4">
                        <p className="text-sm text-ink-muted">
                            No run has gone to the bank yet, so there are no unpaid debits to decide.
                        </p>
                    </Sheet>
                )}

                {/* Banking Details -- for an unpaid's member, or for a member found below. */}
                {selectedMember && (
                    <Sheet className="mt-6" aria-labelledby="acb-banking-heading">
                        <div className="px-6 py-4 border-b border-line flex items-center justify-between">
                            <div>
                                <p id="acb-banking-heading" ref={sheetRef} tabIndex={-1}
                                    className="text-xs font-semibold uppercase tracking-wide text-ink-muted focus:outline-none">
                                    Banking Details
                                </p>
                                <p className="mt-0.5 font-semibold text-ink">
                                    {selectedMember.title ? `${selectedMember.title} ` : ""}{selectedMember.initials} {selectedMember.surname}
                                    <span className="ml-2 text-ink-muted font-normal text-sm">· {selectedMember.membershipNumber}</span>
                                </p>
                            </div>
                            <div className="flex items-center gap-3">
                                <Button onClick={openEdit}>Edit Details</Button>
                                <StatusChip status={selectedMember.status} />
                            </div>
                        </div>

                        <div className="px-6 py-5 grid grid-cols-2 md:grid-cols-3 gap-5 text-sm">
                            {[
                                ["Bank", selectedMember.bankName],
                                ["Branch", selectedMember.branch],
                                ["Bank Account Number", selectedMember.bankAccountNumber],
                                ["IBT No.", selectedMember.ibtNo],
                                ["Payment Type", selectedMember.paymentType],
                                ["Debit Day", selectedMember.debitDay ? ordinal(selectedMember.debitDay) : null],
                                ["Monthly Subscription", selectedMember.monthlySubscription == null ? null : formatZar(selectedMember.monthlySubscription)],
                                ["Subscription Frequency", selectedMember.subscriptionFrequency],
                                ["Deduct by Debit Order", selectedMember.deductACB],
                            ].map(([label, val]) => (
                                <div key={label}>
                                    <p className="text-xs font-semibold uppercase tracking-wide text-ink-muted">{label}</p>
                                    <p className="mt-0.5 font-semibold text-ink">{val || "-"}</p>
                                </div>
                            ))}
                        </div>

                        {/* 🔴 `Confirm Details` WAS HERE, AND IT RECORDED NOTHING: "Banking details confirmed" was said
                            by the browser to itself. The decision on an unpaid is recorded by the unpaid's own Confirm,
                            and a corrected account is what the next run prepared takes. */}
                        <div className="px-6 py-4 border-t border-line bg-surface-2 flex items-center gap-4">
                            <p className="text-sm text-ink-muted flex-1">
                                A corrected account is what the next run prepared debits. Decide on the unpaid itself above.
                            </p>
                            <Button onClick={() => setSelectedMember(null)}>Close</Button>
                        </div>

                        {/* 🔴 `03.4`: was a bare `fixed inset-0` div -- not portalled, no focus
                            trap, no Escape, no scroll lock. Tab walked straight out of it into the
                            page behind. `Modal` brings all four, and the dvh height cap with them. */}
                        <Modal
                            open={editing}
                            onOpenChange={setEditing}
                            title="Update Banking Details"
                            size="lg"
                            busy={saving}
                            footer={
                                <>
                                    <ModalClose>Cancel</ModalClose>
                                    <Button variant="primary" onClick={saveBanking} loading={saving}>Update</Button>
                                </>
                            }
                        >
                            <DialogNotice notice={bankingNotice} onDismiss={() => setBankingNotice(null)} />
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <TextInput label="Bank" value={bankForm.bankName} onChange={handleBankChange('bankName')} />
                                <TextInput label="Branch" value={bankForm.branch} onChange={handleBankChange('branch')} />
                                <TextInput label="Account Number" value={bankForm.bankAccountNumber} onChange={handleBankChange('bankAccountNumber')} />
                                {/* 00.5: the file prints it on every debit, so a debit order saved here must carry one -- the server says so if not. */}
                                <SelectInput
                                    label="Account Type"
                                    value={bankForm.accountType}
                                    onChange={(value) => setBankForm(f => ({ ...f, accountType: value }))}
                                    options={ACCOUNT_TYPE_OPTIONS}
                                    placeholder="No account type"
                                />
                                <TextInput label="IBT No." value={bankForm.ibtNo} onChange={handleBankChange('ibtNo')} />
                                <TextInput label="Payment Type" value={bankForm.paymentType} onChange={handleBankChange('paymentType')} />
                                {/* Q42: any day from the 20th to the 31st, the member's own -- a choice, not a text box. */}
                                <SelectInput
                                    label="Debit Day"
                                    value={bankForm.debitDay}
                                    onChange={(value) => setBankForm(f => ({ ...f, debitDay: value }))}
                                    options={DAY_OPTIONS}
                                    placeholder={dayOutsideWindow ? `The ${ordinal(bankForm.debitDay)} — outside the window` : 'No debit day'}
                                    hint={dayOutsideWindow
                                        ? `The ${ordinal(bankForm.debitDay)} on record is outside the 20th to the 31st. Choose a day in the window.`
                                        : 'Any day from the 20th to the 31st.'}
                                />
                                <TextInput label="Bank Address" value={bankForm.bankAddress} onChange={handleBankChange('bankAddress')} />
                                <TextInput label="Work Bank Address" value={bankForm.workBankAddress} onChange={handleBankChange('workBankAddress')} />
                            </div>
                        </Modal>
                    </Sheet>
                )}

                {runsState === 'ready' && runs.next && <AcbHeldOver run={runs.next} />}
                {runsState === 'ready' && runs.returned && <AcbHeldLetters run={runs.returned} />}
                {/* 03.5: who three unpaids would terminate -- read from every sent month, not from one run. */}
                <AcbTerminationReport />

                {/* The member search -- for a member outside the unpaid list. card.html's criteria row. */}
                <div className="mt-8">
                    <Criteria label="Member search">
                        <TextInput className={CRITERION_WIDE} label="Surname" kind="name" value={filters.surname} onChange={handleChange("surname")} placeholder="Any" />
                        <TextInput className={CRITERION} label="Membership number" kind="identifier" value={filters.membershipNumber} onChange={handleChange("membershipNumber")} placeholder="Any" />
                        <TextInput className={CRITERION_WIDE} label="Identity number" kind="identifier" value={filters.identityNumber} onChange={handleChange("identityNumber")} placeholder="Any" error={idError || undefined} />
                        <Button variant="primary" onClick={handleSearch}>
                            <Search size={16} strokeWidth={1.75} aria-hidden="true" /> Search
                        </Button>
                        <Button variant="ghost" onClick={handleClear}>Clear</Button>
                    </Criteria>
                    {searchHint && <p className="mt-2 text-sm text-ink-muted" role="status">{searchHint}</p>}
                </div>

                {searched && (
                    <Sheet className="mt-6">
                        {/* 🔴 A COUNT IS A FACT ABOUT DATA THAT ARRIVED, and this one used to be stated whatever
                            happened -- "0 members found" above "We could not load the member list.", or a confident
                            zero under a spinner. The count and the empty copy render ONLY when the answer resolved
                            (conventions §8). The count is the server's: every member matching, not one page's. */}
                        {listState === 'ready' && (
                            <div className="px-6 py-3 border-b border-line text-sm text-ink-muted">
                                {count} member{count !== 1 ? "s" : ""} found
                            </div>
                        )}
                        <Table
                            caption="Members matching the debit order search"
                            columns={memberColumns}
                            rows={results.content}
                            rowKey="id"
                            loading={listState === 'loading'}
                            error={listState === 'error' ? 'We could not search the members.' : null}
                            onRetry={() => runSearch(searched, results.page)}
                            emptyTitle="No members match your search."
                            emptyMessage="Try a different membership or identity number."
                            dense
                        />
                        {results.totalPages > 1 && (
                            <PagerFor data={results} state={listState} goTo={goToResults} noun="members" size={SEARCH_PAGE_SIZE} />
                        )}
                    </Sheet>
                )}
            </div>
        </Page>
    );
}
