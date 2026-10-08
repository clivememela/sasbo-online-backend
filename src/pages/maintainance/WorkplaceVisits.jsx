import { Fragment, useEffect, useState } from 'react';

import DataState from '../../components/common/DataState.jsx';
import Pager from '../../components/common/Pager.jsx';
import { WORKPLACE_PAGE_INVALID, committeeLine } from '../../config/monthEnd.js';
import { EMPTY, MONTHS, formatDateLong, formatInt, parseDate } from '../../lib/format.js';
import reportError from '../../lib/reportError.js';
import { VISIT_PAGE_SIZE, getVisits } from '../../services/workplaceVisitService.js';

/**
 * A WORKPLACE'S VISITS -- month end A.1 (MO22), opened from its row on the workplaces' screen (01.5): the visits newest
 * first, a page at a time, as the server answers them.
 *
 * Each is its day -- or, for SASBO's own history (`dayKnown: false`, the months its branch list prints), its MONTH,
 * never a day nobody wrote down -- and what the organiser found: the staff complement, and where the visit changed it,
 * what it was. SASBO's own history says so: *From SASBO's list*. Who visited and the counts the organiser took are kept
 * and not answered (`withheld`, MD61 and B1), and one sentence says so.
 *
 * Above them, the workplace's row in one line, whole: the register's ladder hides the committee, the employer and the
 * area as the sheet narrows (01.5), and this is where they still are.
 *
 * FOUR STATES (SCREEN-CONTRACT §2): loading; failed -- with Try again where it can work, and never as "none"; empty --
 * *No visits on record*; ready. A page that is gone (`WORKPLACE_PAGE_INVALID`) is the first page again, where the
 * newest are; an answer that is not a page of visits is *not available*, never an empty list (MV-36).
 */

const NOT_AVAILABLE = { message: 'The visits are not available from this server yet.', retryable: false };
const pageOf = (body) => (Array.isArray(body?.visits) ? body : null);

/** A visit's day -- or its month, where only the month is known: 20 Aug 2026 · Jul 2026. */
const whenOf = (visit) => {
  if (visit.dayKnown) return formatDateLong(visit.visitedOn);
  const date = parseDate(visit.visitedOn);
  return date ? `${MONTHS[date.m - 1]} ${date.y}` : EMPTY;
};

/** What a visit found -- or, for SASBO's own history, where it came from. */
const foundBy = (visit) => {
  if (visit.seeded) return 'From SASBO’s list';
  if (visit.complementFound == null) return 'No staff complement taken';
  const was = visit.complementBefore == null ? '' : `, changed from ${formatInt(visit.complementBefore)}`;
  return `Staff complement ${formatInt(visit.complementFound)}${was}`;
};

/** The workplace's row, whole, in one line -- every column the ladder can hide. */
const factsOf = (w) => [
  w.employer ?? w.employerPrinted ?? EMPTY,
  `Area ${w.area ?? EMPTY}`,
  `Committee ${committeeLine(w)}`,
  `Staff ${formatInt(w.staffComplement)}`,
].join(' · ');

export default function WorkplaceVisits({ workplace }) {
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [read, setRead] = useState({});
  const question = `${workplace.id}|${page}|${attempt}`;

  useEffect(() => {
    let current = true;
    const asked = `${workplace.id}|${page}|${attempt}`;
    getVisits(workplace.id, page, VISIT_PAGE_SIZE)
      .then((body) => {
        const answer = pageOf(body);
        if (current) setRead({ asked, answer, failure: answer ? null : NOT_AVAILABLE });
      })
      .catch((error) => {
        if (!current) return;
        if (page && error?.body?.code === WORKPLACE_PAGE_INVALID) {
          setPage(0);
          return;
        }
        reportError('WorkplaceVisits.read', error);
        setRead({ asked, failure: error?.status === 404 ? NOT_AVAILABLE : error });
      });
    return () => {
      current = false;
    };
  }, [workplace.id, page, attempt]);

  const pending = read.asked !== question;
  const answer = pending ? null : read.answer;
  const failure = pending ? null : read.failure;
  const view = pending ? 'loading' : failure ? 'error' : answer.visits.length ? 'ready' : 'empty';
  // The pager stands while the next page loads -- shut, and saying so -- so the place it was pressed in stays put.
  const paged = read.answer?.totalPages > 1;
  const first = page * VISIT_PAGE_SIZE;

  return (
    <section aria-label={`Visits to ${workplace.name}`} data-visits={view} className="grid gap-2.5 bg-surface-2 px-5 py-4">
      <p className="text-sm text-ink-muted">{factsOf(workplace)}</p>
      <h2 className="text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Visits, newest first</h2>
      {view === 'ready' ? (
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          {answer.visits.map((visit) => (
            <Fragment key={visit.id}>
              <dt className="font-medium text-ink tabular-nums">{whenOf(visit)}</dt>
              <dd className={visit.seeded ? 'text-ink-muted' : 'text-ink'}>{foundBy(visit)}</dd>
            </Fragment>
          ))}
        </dl>
      ) : (
        <DataState
          state={view}
          layout="row"
          compact
          className="p-0"
          title={view === 'loading' ? 'Loading visits…' : view === 'empty' ? 'No visits on record' : 'The visits could not be loaded.'}
          message={failure?.message}
          error={failure}
          onRetry={() => setAttempt((n) => n + 1)}
        />
      )}
      {paged && (
        <Pager
          className="p-0 pt-3"
          noun="visits"
          page={page + 1}
          totalPages={read.answer.totalPages}
          count={read.answer.totalElements}
          start={first + 1}
          end={first + (answer?.visits.length ?? 0)}
          loading={pending}
          error={view === 'error'}
          onPageChange={(next) => setPage(next - 1)}
        />
      )}
      {answer?.withheld?.length > 0 && (
        <p className="text-sm text-ink-muted">
          Who visited and the counts the organiser took are kept, and shown once sign-in exists.
        </p>
      )}
    </section>
  );
}
