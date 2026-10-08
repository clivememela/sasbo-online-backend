import { useEffect, useId, useState } from 'react';

import { Button } from '../../components/form';
import DataState from '../../components/common/DataState.jsx';
import Pager from '../../components/common/Pager.jsx';
import { formatDateLong } from '../../lib/format.js';
import reportError from '../../lib/reportError.js';
import { DRAFT_PAGE_SIZE, getMyDrafts } from '../../services/workplaceVisitService.js';
import { DRAFT_STATUS } from './visitDraft.js';

/**
 * MY VISITS -- the drafts I saved and the visits I submitted (October build, 5 Oct Show & Tell: the visit forms saving
 * and submitting). A draft opens into the form to be finished; a submitted visit opens read-only, to be printed.
 *
 * Only ever the signed-in organiser's own: the server answers no one else's. Newest saved first, twenty a page -- an
 * organiser's month is about twenty visits -- with a pager past the first (October build review: only the newest
 * twenty could be opened). A new tab, or a save, starts again at the first page.
 *
 * FOUR STATES: loading; failed, with Try again; empty -- in words that say which list is empty; ready.
 */

const NOT_AVAILABLE = Object.assign(new Error('Your visits are not available from this server yet.'),
  { retryable: false });

const TABS = [
  { status: DRAFT_STATUS.DRAFT, label: 'Drafts', empty: 'No drafts saved yet.' },
  { status: DRAFT_STATUS.SUBMITTED, label: 'Submitted', empty: 'No visits submitted yet.' },
];

/** When a draft was last saved, as a person reads it: the day, and the time in SASBO's zone. */
const savedAt = (instant) => {
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return '';
  const time = date.toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Johannesburg' });
  const day = date.toLocaleDateString('en-CA', { timeZone: 'Africa/Johannesburg' });
  return `${formatDateLong(day)} at ${time}`;
};

export default function MyVisitDrafts({ refreshKey = 0, openId, onOpen }) {
  const headingId = useId();
  const [status, setStatus] = useState(DRAFT_STATUS.DRAFT);
  const [attempt, setAttempt] = useState(0);
  // The page shown, for the list it was turned on: another tab or another save is another list, from its first page.
  const [paging, setPaging] = useState({ list: '', page: 0 });
  const [read, setRead] = useState({});
  const list = `${status}|${refreshKey}`;
  const page = paging.list === list ? paging.page : 0;
  const question = `${list}|${page}|${attempt}`;

  useEffect(() => {
    let current = true;
    const shown = `${status}|${refreshKey}`;
    const asked = `${shown}|${page}|${attempt}`;
    getMyDrafts(status, page, DRAFT_PAGE_SIZE)
      .then((body) => {
        if (!current) return;
        const answer = Array.isArray(body?.drafts) ? body : null;
        setRead({ asked, list: shown, answer, failure: answer ? null : NOT_AVAILABLE });
      })
      .catch((error) => {
        if (!current) return;
        reportError('MyVisitDrafts.read', error);
        setRead({ asked, list: shown, failure: error });
      });
    return () => {
      current = false;
    };
  }, [status, refreshKey, page, attempt]);

  const tab = TABS.find((entry) => entry.status === status);
  const pending = read.asked !== question;
  const answer = pending ? null : read.answer;
  const view = pending ? 'loading' : read.failure ? 'error' : answer.drafts.length ? 'ready' : 'empty';
  // The pager stands on the page answered last for this list while the next one loads, so it does not flicker away.
  const paged = pending ? (read.list === list ? read.answer : null) : answer;

  return (
    <section aria-labelledby={headingId} className="no-print mt-5 max-w-4xl rounded-lg bg-surface p-5 shadow-(--shadow-1)">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id={headingId} className="text-base font-semibold text-ink">My visits</h2>
        <div className="flex gap-1" role="group" aria-label="Which visits">
          {TABS.map((entry) => (
            <Button key={entry.status} size="sm" variant={entry.status === status ? 'primary' : 'default'}
              aria-pressed={entry.status === status} onClick={() => setStatus(entry.status)}>
              {entry.label}
            </Button>
          ))}
        </div>
      </div>
      {view === 'ready' ? (
        <>
          <ul className="mt-3 divide-y divide-line">
            {answer.drafts.map((draft) => (
              <li key={draft.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5 text-sm">
                <div className="min-w-0">
                  <p className="font-medium text-ink">
                    {draft.workplaceName} <span className="font-normal text-ink-muted">· R/A {draft.raNumber}</span>
                  </p>
                  <p className="text-ink-muted">
                    {draft.visitedOn ? `Visit of ${formatDateLong(draft.visitedOn)}` : 'No day chosen yet'}
                    {' · '}
                    {draft.status === DRAFT_STATUS.SUBMITTED ? `submitted ${savedAt(draft.submittedAt)}` : `saved ${savedAt(draft.updatedAt)}`}
                  </p>
                </div>
                <Button size="sm" onClick={() => onOpen?.(draft)} disabled={draft.id === openId}
                  aria-label={`${draft.id === openId ? 'Open now' : 'Open'}: ${draft.workplaceName}, R/A ${draft.raNumber}`}>
                  {draft.id === openId ? 'Open now' : 'Open'}
                </Button>
              </li>
            ))}
          </ul>
        </>
      ) : (
        <DataState
          state={view}
          layout="row"
          compact
          className="mt-2 p-0"
          title={view === 'loading' ? 'Loading your visits…' : view === 'empty' ? tab.empty : 'Your visits could not be loaded.'}
          message={read.failure?.message}
          error={read.failure}
          onRetry={() => setAttempt((n) => n + 1)}
        />
      )}
      {paged?.totalPages > 1 && (
        <Pager
          className="-mx-5 -mb-5 mt-3 rounded-b-lg"
          page={page + 1}
          totalPages={paged.totalPages}
          onPageChange={(next) => setPaging({ list, page: next - 1 })}
          count={paged.totalElements}
          start={page * DRAFT_PAGE_SIZE + 1}
          end={page * DRAFT_PAGE_SIZE + paged.drafts.length}
          noun="visits"
          loading={pending}
          error={view === 'error'}
        />
      )}
    </section>
  );
}
