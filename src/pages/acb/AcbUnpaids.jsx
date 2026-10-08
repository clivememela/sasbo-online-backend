import { useCallback, useId, useState } from 'react';
import Sheet from '../../components/common/Sheet.jsx';
import DialogNotice from '../../components/common/DialogNotice.jsx';
import Table from '../../components/table/Table.jsx';
import { Button, SelectInput } from '../../components/form';
import reportError from '../../lib/reportError.js';
import { formatZar } from '../../lib/format.js';
import { acbRefusalOf, acbRunService } from '../../services/acbRunService.js';
import { messageOf, writeMayHaveLanded } from '../../services/parseApiError.js';
import { PagerFor, usePagedRead } from './acbPaging.jsx';
import {
  BANKING_REASONS, DECISION_LABEL, LETTER_STATUS_LABEL, REFUSAL, RUN_STATUS, RUN_STATUS_LABEL, UNPAID_REASON_LABEL, monthLabel,
} from './acbRunRules.js';

const PAGE_SIZE = 50;
const EVERY_REASON = 'ALL';

const REASONS = [
  { value: EVERY_REASON, label: 'Every reason' },
  ...Object.entries(UNPAID_REASON_LABEL).map(([value, label]) => ({ value, label })),
];

const DECISIONS = Object.entries(DECISION_LABEL).map(([value, label]) => ({ value, label }));

/** The member as the list reads them -- surname first. */
const listName = (line) => [line.surname, line.initials].filter(Boolean).join(', ') || line.membershipNumber || '—';

/** The member as a sentence says them. */
const nameOf = (line) => [line.initials, line.surname].filter(Boolean).join(' ') || line.membershipNumber || 'this member';

/**
 * The unpaids of the newest run whose file went to the bank (`04.1`) -- EVERY unpaid, whatever its code
 * (D8 as widened) -- each with its code, its reason, where its letter is, and its DECISION: present it
 * again in the next run, or suspend it (Q7, Q9). Undecided, the decision its code suggests is already
 * chosen (Q40, Q44) and stays the administrator's to change; *Confirm* records it, and a recorded
 * decision stands -- the server refuses a second. A filter to *Invalid Account Number* is the legacy
 * *Invalid Accounts* list (Q11), and the two reasons whose cure is the member's banking offer the
 * banking sheet.
 *
 * 🔒 The screen's word for a recorded decision is the server's answer, never the choice it sent (the
 * design arc's `06.4` rule): until the answer arrives the row says nothing new, and an answer that does
 * not carry the decision is treated as one that never came.
 */
export default function AcbUnpaids({ run, onBanking }) {
  const [reason, setReason] = useState(EVERY_REASON);
  const month = monthLabel(run.actionMonth);
  return (
    <Sheet className="mt-6" aria-labelledby="acb-unpaids-heading">
      <div className="flex flex-wrap items-end justify-between gap-4 border-b border-line px-6 py-4">
        <div className="min-w-0">
          <h2 id="acb-unpaids-heading" className="text-base font-semibold text-ink">Unpaid debits — {month}</h2>
          <p className="mt-0.5 text-sm text-ink-muted">
            The {month} run is {String(RUN_STATUS_LABEL[run.status] ?? run.status).toLowerCase()}. Present each
            unpaid again in the next run, or suspend it — the choice is yours until you confirm it.
          </p>
        </div>
        <SelectInput className="w-full sm:w-64" label="Reason" value={reason} onChange={(value) => setReason(value)}
          options={REASONS} />
      </div>
      {/* Keyed on the filter, so a new reason starts at its first page rather than on the old list's page. */}
      <UnpaidList key={reason} run={run} reason={reason === EVERY_REASON ? null : reason} onBanking={onBanking} />
    </Sheet>
  );
}

function UnpaidList({ run, reason, onBanking }) {
  const read = useCallback((page, size) => acbRunService.unpaids(run.id, reason, page, size), [run.id, reason]);
  const { data, state, goTo, retry } = usePagedRead(read, { size: PAGE_SIZE, label: 'AcbUnpaids.read' });
  const suspendWhy = useId();

  // The administrator's choice per line, before it is confirmed; and the server's answer per line, after.
  const [chosen, setChosen] = useState({});
  const [answered, setAnswered] = useState({});
  const [busyLine, setBusyLine] = useState(null);
  const [notice, setNotice] = useState(null);

  const decisionOf = (line) => chosen[line.id] ?? line.suggestedDecision ?? '';

  const confirm = async (line) => {
    const decision = decisionOf(line);
    if (!decision) return;
    setBusyLine(line.id);
    setNotice(null);
    try {
      const answer = await acbRunService.decide(run.id, line.id, decision);
      if (answer?.decision !== decision) {
        // An answer that does not say what was recorded is not a recording: read the list again.
        setNotice({ message: `The decision for ${nameOf(line)} could not be confirmed. The list is read again — check it before deciding again.` });
        retry();
        return;
      }
      setAnswered((previous) => ({ ...previous, [line.id]: answer }));
      setNotice({
        tone: 'ok',
        message: decision === 'RE_PRESENT'
          ? `Recorded: ${nameOf(line)}’s unpaid is presented again in the next run.`
          : `Recorded: ${nameOf(line)}’s unpaid is suspended — it is not presented again.`,
      });
    } catch (err) {
      reportError('AcbUnpaids.decide', err);
      const refusal = acbRefusalOf(err);
      if (refusal?.code === REFUSAL.UNPAID_ALREADY_RECORDED) {
        setNotice({ message: `A decision on ${nameOf(line)}’s unpaid was already recorded. Nothing was changed — the list is read again.` });
        retry();
      } else if (refusal?.code === REFUSAL.RUN_NOT_IN_STATE) {
        const now = RUN_STATUS_LABEL[refusal.details.status] ?? refusal.details.status;
        setNotice({ message: `This run no longer takes decisions${now ? ` — it is ${String(now).toLowerCase()}` : ''}. Nothing was recorded.` });
        retry();
      } else if (writeMayHaveLanded(err)) {
        setNotice({ message: `The decision for ${nameOf(line)} could not be confirmed. It may have been recorded — the list is read again, so check it before deciding again.` });
        retry();
      } else {
        setNotice({ message: `The decision for ${nameOf(line)} was not recorded. Nothing was written — ${messageOf(err)}` });
      }
    } finally {
      setBusyLine(null);
    }
  };

  const rows = data.content.map((line) => answered[line.id] ?? line);
  const columns = [
    {
      key: 'member', header: 'Member', priority: 'essential',
      cell: (line) => (
        <span>
          {listName(line)}
          <span className="ml-2 text-xs text-ink-muted">{line.membershipNumber}</span>
        </span>
      ),
    },
    {
      key: 'reason', header: 'Reason', priority: 'essential',
      cell: (line) => (
        <span>
          {UNPAID_REASON_LABEL[line.unpaidReason] ?? line.unpaidReason ?? '—'}
          {line.unpaidCode && <span className="ml-2 text-xs text-ink-muted tabular-nums">code {line.unpaidCode}</span>}
        </span>
      ),
    },
    { key: 'amount', header: 'Amount', priority: 'secondary', cell: (line) => formatZar(line.amount) },
    {
      key: 'letter', header: 'Letter', priority: 'secondary',
      cell: (line) => (line.letterStatus ? LETTER_STATUS_LABEL[line.letterStatus] ?? line.letterStatus : 'No letter'),
    },
    {
      key: 'decision', header: 'Decision', priority: 'essential',
      cell: (line) => (line.decision ? (
        <span className="font-medium text-ink">
          {DECISION_LABEL[line.decision] ?? line.decision}
          <span className="ml-2 text-xs font-normal text-ink-muted">recorded{line.permissionNote ? ' · permission on record' : ''}</span>
          {/* 03.5: the decision is recorded; moving the member to Suspended waits for sign-in (B1), as Approve does. */}
          {line.decision === 'SUSPEND' && (
            <Button className="ml-3" disabled aria-describedby={suspendWhy}
              aria-label={`Suspend ${nameOf(line)} as a member`}>
              Suspend member
            </Button>
          )}
        </span>
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          <SelectInput
            className="w-40"
            aria-label={`Decision on ${nameOf(line)}’s unpaid`}
            value={decisionOf(line)}
            placeholder="Choose"
            onChange={(value) => setChosen((previous) => ({ ...previous, [line.id]: value }))}
            options={DECISIONS}
            disabled={busyLine != null}
          />
          <Button
            variant="primary"
            onClick={() => confirm(line)}
            loading={busyLine === line.id}
            disabled={busyLine != null || !decisionOf(line)}
            aria-label={`Confirm the decision on ${nameOf(line)}’s unpaid`}
          >
            Confirm
          </Button>
        </div>
      )),
    },
    {
      key: 'banking', header: 'Banking', priority: 'secondary',
      cell: (line) => (BANKING_REASONS.has(line.unpaidReason) ? (
        <Button variant="ghost" onClick={() => onBanking(line)} aria-label={`Banking details for ${nameOf(line)}`}>
          Banking
        </Button>
      ) : null),
    },
  ];

  return (
    <>
      {notice && (
        <div className="px-6 pt-4">
          <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />
        </div>
      )}
      <Table
        caption="Unpaid debits and their decisions"
        columns={columns}
        rows={rows}
        rowKey="id"
        loading={state === 'loading'}
        error={state === 'error' ? 'We could not load the unpaid debits.' : null}
        onRetry={retry}
        emptyTitle={reason ? 'No unpaid debits for this reason' : 'No unpaid debits in this run'}
        emptyMessage={reason ? 'Choose another reason, or every reason.'
          : run.status === RUN_STATUS.RETURNED ? 'The bank returned every debit of this run paid.'
            : 'No unpaid has been recorded for this run yet.'}
        dense
      />
      <PagerFor data={data} state={state} goTo={goTo} noun="unpaids" size={PAGE_SIZE} />
      {rows.some((line) => line.decision === 'SUSPEND') && (
        <p id={suspendWhy} className="border-t border-line px-6 py-3 text-sm text-ink-muted">
          Moving a member decided Suspend to the Suspended status opens when administrators sign in to this system.
        </p>
      )}
    </>
  );
}
