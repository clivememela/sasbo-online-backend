import { useCallback, useState } from 'react';
import Sheet from '../../components/common/Sheet.jsx';
import StatusChip from '../../components/common/StatusChip.jsx';
import Table from '../../components/table/Table.jsx';
import { Button } from '../../components/form';
import { acbRunService } from '../../services/acbRunService.js';
import { PagerFor, usePagedRead } from './acbPaging.jsx';
import { monthLabel } from './acbRunRules.js';

const PAGE_SIZE = 50;

/** The member status the returns loop writes for SUBS_IN_ARREARS (03.5). */
const TERMINATED = 'Terminated';

/**
 * 03.5 -- who three unpaid presentations would terminate, and who is one away (Q27: every unpaid counts, a
 * re-debit included). Three can fall inside two months, so it is a report an administrator acts on, never a
 * rule that acts: nobody is terminated by reading it.
 *
 * Terminating -- through the member service's narrow status change, for the reason SUBS_IN_ARREARS (Q25) -- is
 * built and proven on the server and refused to every caller until administrators sign in (B1). So the action is
 * shown, disabled, with the reason beside it, exactly as Approve is on the Runs screen.
 */
export default function AcbTerminationReport() {
  const [about, setAbout] = useState({ countingFrom: null, blindness: null });
  const read = useCallback((page, size) => acbRunService.terminationReport(page, size).then((report) => {
    setAbout({ countingFrom: report?.countingFrom ?? null, blindness: report?.blindness ?? null });
    return report?.members;
  }), []);
  const { data, state, goTo, retry } = usePagedRead(read, { size: PAGE_SIZE, label: 'AcbTerminationReport.read' });
  const anyDue = data.content.some((member) => member.terminate && member.status !== TERMINATED);

  const columns = [
    {
      key: 'member', header: 'Member', priority: 'essential',
      cell: (member) => (
        <span>
          {member.surname || member.membershipNumber}
          <span className="ml-2 text-xs text-ink-muted">{member.membershipNumber}</span>
        </span>
      ),
    },
    {
      key: 'count', header: 'Unpaids in a row', priority: 'essential',
      cell: (member) => (
        <span className={member.terminate ? 'font-semibold text-ink' : 'text-ink'}>
          {member.unpaidCount}
          <span className="ml-2 text-xs font-normal text-ink-muted">
            {member.terminate ? 'three — the rule terminates' : 'one more makes three'}
          </span>
        </span>
      ),
    },
    {
      key: 'months', header: 'Months unpaid', priority: 'secondary',
      cell: (member) => (member.unpaidMonths ?? []).map(monthLabel).join(', '),
    },
    { key: 'twelve', header: 'Last 12 months', priority: 'secondary', cell: (member) => member.twelveMonthTotal },
    {
      key: 'opening', header: 'From the old system', priority: 'secondary',
      cell: (member) => (member.openingUnpaids ? member.openingUnpaids : '—'),
    },
    { key: 'status', header: 'Status', priority: 'essential', cell: (member) => <StatusChip status={member.status} /> },
    {
      key: 'act', header: 'Terminate', priority: 'essential',
      cell: (member) => (member.terminate && member.status !== TERMINATED ? (
        <Button disabled aria-describedby="acb-terminate-why"
          aria-label={`Terminate ${member.surname || member.membershipNumber}’s membership`}>
          Terminate
        </Button>
      ) : null),
    },
  ];

  return (
    <Sheet className="mt-6" aria-labelledby="acb-termination-heading">
      <div className="border-b border-line px-6 py-4">
        <h2 id="acb-termination-heading" className="text-base font-semibold text-ink">
          Members at two unpaids or more
        </h2>
        <p className="mt-0.5 text-sm text-ink-muted">
          Three unpaid presentations in a row terminate a membership — a debit, the next month’s and a re-debit
          count alike.{about.countingFrom ? ` Counted from ${monthLabel(about.countingFrom)}.` : ''}
        </p>
        {about.blindness && <p className="mt-1 text-sm text-ink-muted">{about.blindness}</p>}
      </div>
      <Table
        caption="Members the termination rule reaches, or is one away from"
        columns={columns}
        rows={data.content}
        rowKey="memberId"
        loading={state === 'loading'}
        error={state === 'error' ? 'We could not load the termination report.' : null}
        onRetry={retry}
        emptyTitle="Nobody at two unpaids"
        emptyMessage="No member has two unpaid presentations in a row in the last thirteen months."
        dense
      />
      {/* Only when there is a next page, as the member search does: a short list needs no pager. */}
      {data.totalPages > 1 && (
        <PagerFor data={data} state={state} goTo={goTo} noun="members" size={PAGE_SIZE} />
      )}
      {anyDue && (
        <p id="acb-terminate-why" className="border-t border-line px-6 py-3 text-sm text-ink-muted">
          Terminating a membership — for subscriptions in arrears — opens when administrators sign in to this system.
        </p>
      )}
    </Sheet>
  );
}
