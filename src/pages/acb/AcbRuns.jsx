import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import PageTitle from '../../components/common/PageTitle.jsx';
import Criteria, { CRITERION_WIDE } from '../../components/common/Criteria.jsx';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import Pager from '../../components/common/Pager.jsx';
import StatusChip from '../../components/common/StatusChip.jsx';
import DialogNotice from '../../components/common/DialogNotice.jsx';
import Table from '../../components/table/Table.jsx';
import { ConfirmDialog, closeOn } from '../../components/overlay/Modal.jsx';
import { Button, SelectInput } from '../../components/form';
import reportError from '../../lib/reportError.js';
import { formatDate, formatInt, formatZar } from '../../lib/format.js';
import { acbRefusalOf, acbRunService } from '../../services/acbRunService.js';
import { messageOf, writeMayHaveLanded } from '../../services/parseApiError.js';
import AcbRunPanel from './AcbRunPanel.jsx';
import {
  CANCELLABLE, POLL_DELAYS_MS, REFUSAL, RUN_STATUS, RUN_STATUS_LABEL, asPage, calendarNotice, declinedMonthWarning,
  monthLabel, pollRun, preparableMonths, rateNote,
} from './acbRunRules.js';

/** Twelve runs to a page: a year of months. */
const PAGE_SIZE = 12;

/**
 * Debit Order Deductions -> Runs (`01.5`): every month's debit-order run, preparing a new one, and the run itself.
 * Headed *Debit Order Runs*: the screen keeps its name and "ACB" becomes "Debit Order" (Stephne, DOD).
 *
 * 🔑 PREPARING IS THE DRY RUN. Nothing leaves the building until a run is approved, and approving
 * waits until administrators sign in (register B1) -- so this screen prepares, shows who would be
 * debited and who is left out and why, and cancels. `planning/branches/acb/screens.md` §1 is the
 * arrangement; the Card world's by rule: page head, criteria row, sheet, pager, the record below.
 *
 * 🔴 202, THEN POLL -- AND NEVER A SECOND POST. The server answers a prepare at once with the run
 * `PREPARING` and does the work as a job. This screen re-reads that run with backoff, stops when it
 * settles or after about two minutes ("still preparing -- check back"), and stops the moment the
 * screen is left. A write that timed out or lost its connection MAY have landed, so it is never
 * repeated: the list is re-read and the screen says what it found (`writeMayHaveLanded`).
 *
 * `pollDelaysMs` is a test seam; the route renders with the real backoff.
 */
export default function AcbRuns({ pollDelaysMs = POLL_DELAYS_MS }) {
  const [page, setPage] = useState(1);
  const [listState, setListState] = useState('loading');
  const [runs, setRuns] = useState(() => asPage(null));
  const [selectedId, setSelectedId] = useState(null);
  const [panelKey, setPanelKey] = useState(0);
  // Said by DialogNotice's own live region (alert, or status for good news) -- not focused as
  // well, which would announce one outcome twice.
  const [notice, setNotice] = useState(null);

  const months = useMemo(() => preparableMonths(), []);
  const [month, setMonth] = useState(months[0].value);
  const [confirming, setConfirming] = useState(false);
  const [preparing, setPreparing] = useState(false);
  const [prepareNotice, setPrepareNotice] = useState(null);
  const [calendar, setCalendar] = useState(null);
  const [rates, setRates] = useState(null);

  // The screen was left: every poll and every late answer stops touching its state.
  const left = useRef(false);
  useEffect(() => () => { left.current = true; }, []);

  // Only the newest read may paint (R3-F4): a prepare re-reads the list, and so does the run
  // settling -- an older answer arriving last must not put the older list back.
  const ticket = useRef(0);
  const loadRuns = useCallback((pageNumber) => {
    const mine = ++ticket.current;
    const current = () => !left.current && mine === ticket.current;
    return acbRunService.list(pageNumber - 1, PAGE_SIZE)
      .then((data) => {
        if (!current()) return;
        setRuns(asPage(data));
        setListState('ready');
      })
      .catch((err) => {
        reportError('AcbRuns.loadRuns', err);
        if (current()) setListState('error');
      });
  }, []);

  useEffect(() => {
    loadRuns(page);
  }, [loadRuns, page]);

  // The holiday calendar, once: what it says is a note beside preparing, never a condition of the
  // list -- a calendar that cannot be read leaves the screen as it was, and preparing still refuses
  // a month the calendar does not reach (HOLIDAYS_NOT_KNOWN, in the dialog).
  useEffect(() => {
    acbRunService.holidayCalendar()
      .then((data) => { if (!left.current) setCalendar(data); })
      .catch((err) => reportError('AcbRuns.loadCalendar', err));
  }, []);
  const calendarSays = calendarNotice(calendar);

  // The dated rates, once (Q43): what the chosen month will charge sits beside Prepare -- a note, never a
  // condition; the server still refuses a month no rate reaches (RATE_NOT_IN_FORCE, in the dialog).
  useEffect(() => {
    acbRunService.rates()
      .then((data) => { if (!left.current) setRates(data); })
      .catch((err) => reportError('AcbRuns.loadRates', err));
  }, []);

  /** Back to loading, then read again -- the retry, a page change and every write come here. */
  const reloadRuns = useCallback((pageNumber = page) => {
    setListState('loading');
    if (pageNumber === page) loadRuns(pageNumber);
    else setPage(pageNumber);
  }, [loadRuns, page]);

  const openRun = useCallback((run) => {
    setSelectedId(run.id);
    setPanelKey((k) => k + 1);
  }, []);

  /** The run changed (cancelled, or settled after preparing): re-read the list and the run. */
  const runChanged = useCallback(() => {
    reloadRuns();
    setPanelKey((k) => k + 1);
  }, [reloadRuns]);

  const watchPreparation = useCallback((run) => {
    pollRun(acbRunService.get, run.id, { delays: pollDelaysMs, stopped: () => left.current })
      .then((settled) => {
        if (settled === null || left.current) return;
        if (settled === undefined) {
          setNotice({ message: `The ${monthLabel(run.actionMonth)} run is still preparing. `
            + 'Check back in a few minutes — the list shows it the moment it is done.' });
          return;
        }
        setNotice(settled.status === RUN_STATUS.FAILED
          ? { message: `The ${monthLabel(run.actionMonth)} run could not be prepared: ${settled.failureReason ?? 'no reason was given'}.` }
          : { tone: 'ok', message: `The ${monthLabel(run.actionMonth)} run is prepared: `
            + `${formatInt(settled.lineCount)} debits, ${formatZar(settled.totalAmount)}.` });
        runChanged();
      })
      .catch((err) => {
        reportError('AcbRuns.watchPreparation', err);
        if (!left.current) {
          setNotice({ message: 'The run is being prepared, but its progress could not be read. '
            + 'Reload the list to see where it stands.' });
        }
      });
  }, [pollDelaysMs, runChanged]);

  const prepare = async () => {
    setPreparing(true);
    setPrepareNotice(null);
    try {
      const run = await acbRunService.prepare(month);
      if (left.current) return;
      setConfirming(false);
      setNotice({ tone: 'ok', message: `Preparing the ${monthLabel(month)} run. This page checks on it by itself.` });
      reloadRuns(1);
      openRun(run);
      watchPreparation(run);
    } catch (err) {
      reportError('AcbRuns.prepare', err);
      if (left.current) return;
      const refusal = acbRefusalOf(err);
      if (refusal?.code === REFUSAL.RUN_ALREADY_EXISTS && refusal.details.runId) {
        // The month is held by a live run: show that run, and say what can be done about it.
        const status = refusal.details.status;
        const advice = CANCELLABLE.has(status) ? ' To prepare the month again, cancel it first.'
          : status === RUN_STATUS.PREPARING ? ' It is still being prepared.' : '';
        setConfirming(false);
        setNotice({ message: `The ${monthLabel(month)} run already exists and is `
          + `${(RUN_STATUS_LABEL[status] ?? status ?? '').toLowerCase()}.${advice}` });
        openRun({ id: Number(refusal.details.runId) });
      } else if (writeMayHaveLanded(err)) {
        // Never a second POST: it may have landed. Close, re-read, and say what is uncertain.
        setConfirming(false);
        setNotice({ message: `The ${monthLabel(month)} run could not be confirmed. It may have been started — `
          + 'the list below has been read again; if the month is there, it is preparing.' });
        reloadRuns(1);
      } else {
        setPrepareNotice({ message: `The ${monthLabel(month)} run was not prepared. Nothing was written — ${messageOf(err)}` });
      }
    } finally {
      if (!left.current) setPreparing(false);
    }
  };

  const columns = [
    {
      key: 'actionMonth', header: 'Month', priority: 'essential',
      cell: (run) => (
        <button
          type="button"
          className="touch-target inline-flex items-center rounded-sm font-medium text-brand-ink underline-offset-2 hover-hover:hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring"
          aria-pressed={selectedId === run.id}
          aria-label={`Open the ${monthLabel(run.actionMonth)} run`}
          onClick={() => openRun(run)}
        >
          {monthLabel(run.actionMonth)}
        </button>
      ),
    },
    {
      key: 'status', header: 'State', priority: 'essential',
      cell: (run) => <StatusChip status={RUN_STATUS_LABEL[run.status] ?? run.status} />,
    },
    { key: 'lineCount', header: 'Lines', priority: 'secondary', cell: (run) => formatInt(run.lineCount) },
    { key: 'totalAmount', header: 'Control total', priority: 'essential', cell: (run) => formatZar(run.totalAmount) },
    { key: 'setupDate', header: 'Prepared', priority: 'detail', cell: (run) => formatDate(run.setupDate) },
  ];

  // SD3-H: after a wrong-date decline, the month's prepare dialog says so, and whether the calendar moved.
  const declinedWarning = declinedMonthWarning(runs.content, month);
  const start = runs.page * PAGE_SIZE + 1;
  const end = runs.page * PAGE_SIZE + runs.content.length;

  return (
    <Page>
      <PageTitle title="Debit Order Runs" subtitle="Every month's debit order run, and what became of it" />

      <div className="mt-4 empty:hidden">
        <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />
      </div>

      <Criteria className="mt-6" label="Prepare a run">
        <SelectInput
          className={CRITERION_WIDE}
          label="Action month"
          value={month}
          onChange={(value) => setMonth(value)}
          options={months}
        />
        <Button variant="primary" onClick={() => { setPrepareNotice(null); setConfirming(true); }}>
          Prepare {monthLabel(month)}
        </Button>
      </Criteria>
      {rateNote(rates, month) && <p className="mt-2 text-sm text-ink-muted">{rateNote(rates, month)}</p>}
      {rates?.januaryNotice && <p className="mt-2 text-sm text-status-suspended">{rates.januaryNotice}</p>}
      {calendarSays && <p className="mt-2 text-sm text-ink-muted">{calendarSays}</p>}

      <Sheet className="mt-4">
        <Table
          caption="Debit order runs"
          columns={columns}
          rows={runs.content}
          rowKey="id"
          loading={listState === 'loading'}
          error={listState === 'error' ? 'We could not load the runs.' : null}
          onRetry={() => reloadRuns()}
          emptyTitle="No runs yet"
          emptyMessage="Prepare this month's run to see who would be debited, on which date, and who is left out."
          selectedKey={selectedId}
          onRowSelect={openRun}
          dense
        />
        <Pager
          page={runs.page + 1}
          totalPages={Math.max(runs.totalPages, 1)}
          onPageChange={(next) => reloadRuns(next)}
          count={runs.totalElements}
          start={start}
          end={end}
          noun="runs"
          loading={listState === 'loading'}
          error={listState === 'error'}
        />
      </Sheet>

      {selectedId !== null && (
        <AcbRunPanel key={`${selectedId}:${panelKey}`} runId={selectedId} onChanged={runChanged}
          onClose={() => setSelectedId(null)} />
      )}

      <ConfirmDialog
        open={confirming}
        onOpenChange={closeOn(() => setConfirming(false))}
        title={`Prepare the ${monthLabel(month)} run?`}
        description={'This is the dry run: it works out who would be debited, on which date and for how much, '
          + 'and who is left out and why. Nothing is sent to the bank.'}
        confirmLabel="Prepare"
        cancelLabel="Not now"
        destructive={false}
        loading={preparing}
        onConfirm={prepare}
      >
        {declinedWarning && <p className="text-sm text-status-suspended">{declinedWarning}</p>}
        <DialogNotice notice={prepareNotice} onDismiss={() => setPrepareNotice(null)} />
      </ConfirmDialog>
    </Page>
  );
}
