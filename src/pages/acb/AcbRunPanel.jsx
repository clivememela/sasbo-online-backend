import { useCallback, useEffect, useRef, useState } from 'react';
import Sheet from '../../components/common/Sheet.jsx';
import DataState from '../../components/common/DataState.jsx';
import StatusChip from '../../components/common/StatusChip.jsx';
import DialogNotice from '../../components/common/DialogNotice.jsx';
import Table from '../../components/table/Table.jsx';
import { ConfirmDialog, closeOn } from '../../components/overlay/Modal.jsx';
import { PagerFor, usePagedRead } from './acbPaging.jsx';
import { Button } from '../../components/form';
import reportError from '../../lib/reportError.js';
import { formatDate, formatInt, formatZar } from '../../lib/format.js';
import { acbRefusalOf, acbRunService } from '../../services/acbRunService.js';
import { messageOf, writeMayHaveLanded } from '../../services/parseApiError.js';
import {
  CANCELLABLE, DECLINE_REASON_LABEL, EXCLUSION_LABEL, REFUSAL, RUN_STATUS, RUN_STATUS_LABEL, maskedAccount,
  monthLabel,
} from './acbRunRules.js';

const PAGE_SIZE = 50;

/**
 * One run: its facts, its actions, the debits it holds and the members it leaves out.
 *
 * Keyed by the Runs screen on the run's id and a refresh count, so every open and every change
 * mounts it fresh -- nothing here resets state on the way into an effect.
 *
 * 🔑 FOUR STATES FOR THE RUN, AND A PARTIAL ONE FOR ITS PARTS. The run loads first; its lines and
 * its exclusions each load on their own, so a sheet that fails says so beside the run's facts,
 * which still show, and offers its own retry (`screens.md` §1, *Partial*).
 *
 * 🔒 The account shows its last four digits -- all the server ever sends.
 */
export default function AcbRunPanel({ runId, onChanged, onClose }) {
  const [runState, setRunState] = useState('loading');
  const [run, setRun] = useState(null);
  const [runError, setRunError] = useState(null);

  const left = useRef(false);
  useEffect(() => () => { left.current = true; }, []);

  // Only the newest read may paint -- a retry can overtake the read it retries.
  const ticket = useRef(0);
  const loadRun = useCallback(() => {
    const mine = ++ticket.current;
    const current = () => !left.current && mine === ticket.current;
    return acbRunService.get(runId)
      .then((data) => {
        if (!current()) return;
        setRun(data);
        setRunState('ready');
      })
      .catch((err) => {
        reportError('AcbRunPanel.loadRun', err);
        if (!current()) return;
        setRunError(err);
        setRunState('error');
      });
  }, [runId]);

  useEffect(() => {
    loadRun();
  }, [loadRun]);

  const retryRun = () => {
    setRunState('loading');
    loadRun();
  };

  const [confirming, setConfirming] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [cancelNotice, setCancelNotice] = useState(null);

  const cancel = async () => {
    setCancelling(true);
    setCancelNotice(null);
    try {
      await acbRunService.cancel(run.id, run.version, 'Cancelled from the Runs screen');
      if (left.current) return;
      setConfirming(false);
      onChanged();
    } catch (err) {
      reportError('AcbRunPanel.cancel', err);
      if (left.current) return;
      const refusal = acbRefusalOf(err);
      if (refusal?.code === REFUSAL.RUN_VERSION_CONFLICT || refusal?.code === REFUSAL.RUN_NOT_IN_STATE) {
        // Someone else moved it: say what it is now, from the refusal itself, not "try again".
        const now = RUN_STATUS_LABEL[refusal.details.status] ?? refusal.details.status;
        setCancelNotice({ message: `Someone else changed this run first — it is now ${String(now).toLowerCase()}. `
          + 'Nothing was cancelled. Close this and the run shows as it stands.' });
      } else if (writeMayHaveLanded(err)) {
        setCancelNotice({ message: 'The cancellation could not be confirmed. It may have gone through — '
          + 'close this and the run is read again.' });
      } else {
        setCancelNotice({ message: `The run was not cancelled. Nothing was written — ${messageOf(err)}` });
      }
    } finally {
      if (!left.current) setCancelling(false);
    }
  };

  const closeConfirm = () => {
    setConfirming(false);
    // A refusal or an unconfirmed write: whatever the answer was, show the run as it now stands.
    if (cancelNotice) onChanged();
  };

  if (runState !== 'ready') {
    return (
      <Sheet className="mt-6 p-4">
        <DataState
          state={runState === 'error' ? 'error' : 'loading'}
          title={runState === 'error' ? 'This run could not be loaded' : 'Loading the run'}
          error={runError}
          onRetry={retryRun}
        />
      </Sheet>
    );
  }

  const month = monthLabel(run.actionMonth);
  const preparing = run.status === RUN_STATUS.PREPARING;

  return (
    <Sheet className="mt-6 p-4" aria-labelledby="acb-run-heading">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="acb-run-heading" className="text-lg font-semibold text-ink">{month} run</h2>
          <div className="mt-1"><StatusChip status={RUN_STATUS_LABEL[run.status] ?? run.status} /></div>
        </div>
        <Button variant="ghost" size="sm" onClick={onClose}>Close</Button>
      </div>

      {preparing && (
        <p className="mt-3 text-sm text-ink-muted">
          Preparing — who would be debited, and who is left out, appears here when it is done.
        </p>
      )}
      {run.status === RUN_STATUS.DECLINED && (
        <p className="mt-3 text-sm text-status-suspended">
          The bank declined this file: {DECLINE_REASON_LABEL[run.declinedReason] ?? run.declinedReason}
          {run.declinedWords ? ` — “${run.declinedWords}”` : ''}. The month is released, to be prepared again as a new run.
        </p>
      )}
      {run.replacesRunId != null && (
        <p className="mt-3 text-sm text-ink-muted">This run was prepared to replace the one the bank declined.</p>
      )}
      {run.status === RUN_STATUS.FAILED && run.failureReason && (
        <p className="mt-3 text-sm text-status-suspended">This run could not be prepared: {run.failureReason}</p>
      )}

      <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-4">
        <Fact term="Debits" value={formatInt(run.lineCount)} />
        <Fact term="Control total" value={formatZar(run.totalAmount)} />
        <Fact term="Rate charged" value={run.rateAmount == null ? '—'
          : `${formatZar(run.rateAmount)} from ${monthLabel(String(run.rateEffectiveFrom ?? '').slice(0, 7))}`} />
        <Fact term="Prepared" value={formatDate(run.setupDate)} />
        <Fact term="First action date" value={formatDate(run.firstDueDate)} />
        <Fact term="Last action date" value={formatDate(run.lastDueDate)} />
        {run.creationDate
          ? <Fact term="File made on" value={formatDate(run.creationDate)} />
          : <Fact term="Make the file by" value={formatDate(run.makeBy)} />}
        <Fact term="Purge date" value={formatDate(run.purgeDate)} />
      </dl>

      {run.totalsAgree === false && (
        <p className="mt-3 text-sm text-status-suspended">
          The totals stored when this run was prepared no longer match its lines. Do not approve it — prepare the month again.
        </p>
      )}
      {run.makeByPassed && (
        <p className="mt-3 text-sm text-status-suspended">
          The day to make this run's file by, {formatDate(run.makeBy)}, has passed. It can still be made — the bank decides whether it accepts a late file.
        </p>
      )}
      {run.calendarAgrees === false && (
        <p className="mt-3 text-sm text-status-suspended">
          A public holiday was added in this month after the run was prepared, so a debit may now fall on it — and the bank declines a file with a holiday in it. Do not approve it — cancel it and prepare the month again.
        </p>
      )}
      {!run.holidaysKnown && (
        <p className="mt-3 text-sm text-ink-muted">
          Action dates move off weekends only. Public holidays are not yet taken into account.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        {run.status === RUN_STATUS.PREPARED && (
          <Button variant="primary" disabled aria-describedby="acb-approve-why">Approve</Button>
        )}
        {CANCELLABLE.has(run.status) && (
          <Button variant="danger" onClick={() => { setCancelNotice(null); setConfirming(true); }}>Cancel this run</Button>
        )}
      </div>
      {run.status === RUN_STATUS.PREPARED && (
        <p id="acb-approve-why" className="mt-2 text-sm text-ink-muted">
          Approving a run, and downloading its bank file, open when administrators sign in to this system.
        </p>
      )}

      {!preparing && <RunLines runId={run.id} />}
      {!preparing && <RunExclusions runId={run.id} summary={run.exclusions ?? []} />}

      <ConfirmDialog
        open={confirming}
        onOpenChange={closeOn(closeConfirm)}
        title={`Cancel the ${month} run?`}
        description="Cancelling withdraws the run and releases the month, so it can be prepared again. The run stays on record."
        confirmLabel="Cancel run"
        cancelLabel="Keep it"
        loading={cancelling}
        onConfirm={cancel}
      >
        <DialogNotice notice={cancelNotice} onDismiss={() => setCancelNotice(null)} />
      </ConfirmDialog>
    </Sheet>
  );
}

function Fact({ term, value }) {
  return (
    <div>
      <dt className="text-ink-muted">{term}</dt>
      <dd className="font-medium text-ink">{value}</dd>
    </div>
  );
}

function RunLines({ runId }) {
  const read = useCallback((page, size) => acbRunService.lines(runId, page, size), [runId]);
  const { data, state, goTo, retry } = usePagedRead(read, { size: PAGE_SIZE, label: 'AcbRunPanel.read' });
  const columns = [
    {
      key: 'member', header: 'Member', priority: 'essential',
      cell: (line) => (
        <span>
          {[line.surname, line.initials].filter(Boolean).join(', ')}
          {line.rePresentsLineId != null && <span className="ml-2 text-xs text-ink-muted">re-presented</span>}
        </span>
      ),
    },
    { key: 'membershipNumber', header: 'Membership no.', priority: 'secondary' },
    { key: 'bankName', header: 'Bank', priority: 'detail' },
    { key: 'account', header: 'Account', priority: 'secondary', cell: (line) => maskedAccount(line.accountLastFour) },
    { key: 'actionDate', header: 'Action date', priority: 'essential', cell: (line) => formatDate(line.actionDate) },
    { key: 'amount', header: 'Amount', priority: 'essential', cell: (line) => formatZar(line.amount) },
  ];
  return (
    <section className="mt-6" aria-label="Debits in this run">
      <h3 className="text-base font-semibold text-ink">Debits</h3>
      <Table
        caption="Debits in this run"
        columns={columns}
        rows={data.content}
        rowKey="id"
        loading={state === 'loading'}
        error={state === 'error' ? 'We could not load this run’s debits.' : null}
        onRetry={retry}
        emptyTitle="Nobody is debited in this run"
        emptyMessage="Every member was left out — the list below says why."
        dense
      />
      <PagerFor data={data} state={state} goTo={goTo} noun="debits" size={PAGE_SIZE} />
    </section>
  );
}

function RunExclusions({ runId, summary }) {
  const read = useCallback((page, size) => acbRunService.exclusions(runId, page, size), [runId]);
  const { data, state, goTo, retry } = usePagedRead(read, { size: PAGE_SIZE, label: 'AcbRunPanel.read' });
  const counted = summary.filter((s) => !s.listed);
  const columns = [
    { key: 'member', header: 'Member', priority: 'essential',
      cell: (row) => [row.surname, row.initials].filter(Boolean).join(', ') || '—' },
    { key: 'membershipNumber', header: 'Membership no.', priority: 'secondary' },
    { key: 'reason', header: 'Why', priority: 'essential', cell: (row) => EXCLUSION_LABEL[row.reason] ?? row.reason },
    { key: 'detail', header: 'Detail', priority: 'secondary', cell: (row) => row.detail ?? '—' },
  ];
  return (
    <section className="mt-6" aria-label="Members left out">
      <h3 className="text-base font-semibold text-ink">Left out — to fix</h3>
      <Table
        caption="Members left out of this run"
        columns={columns}
        rows={data.content}
        rowKey="memberId"
        loading={state === 'loading'}
        error={state === 'error' ? 'We could not load the members left out.' : null}
        onRetry={retry}
        emptyTitle="Nobody to fix"
        emptyMessage="Every debit-order member who is Active is in this run."
        dense
      />
      <PagerFor data={data} state={state} goTo={goTo} noun="members" size={PAGE_SIZE} />
      {counted.length > 0 && (
        <p className="mt-3 text-sm text-ink-muted">
          Outside this run, and counted rather than listed:{' '}
          {counted.map((s) => `${s.detail ?? EXCLUSION_LABEL[s.reason]} ${formatInt(s.memberCount)}`).join(' · ')}
        </p>
      )}
    </section>
  );
}
