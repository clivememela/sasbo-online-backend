import { useEffect, useRef, useState } from 'react';

import DataState from '../../components/common/DataState.jsx';
import Pager from '../../components/common/Pager.jsx';
import StatusChip from '../../components/common/StatusChip.jsx';
import { HISTORY_PAGE_INVALID } from '../../config/monthEnd.js';
import { EMPTY, formatDateLong } from '../../lib/format.js';
import reportError from '../../lib/reportError.js';
import { cn } from '../../lib/utils.js';
import { HISTORY_PAGE_SIZE, getMemberHistory } from '../../services/memberHistoryService.js';
import {
  STATUS_LABEL, UNKNOWN, failureMessage, failureOf, historyPageOf, historyRows, isSaveOfSeveral, maskedTail,
  olderThanHistory, pageGoneSentence, statusReasonText, valueText, whenOf, whoOf, workplaceOf,
} from './memberHistoryRules.js';

/**
 * THE RECORD'S HISTORY TAB -- month end 03.6, drawn as the owner approved it on 2026-09-30
 * (`planning/branches/monthly-menu/mockups/record-history-tab.html`).
 *
 * 📐 ONE RULED ROW A CHANGE, NOT FIVE COLUMNS. The plan's sheet is *When · Who · What changed · From · To*, and docked
 * the record gives it 365px (341px on a phone): five columns side by side would leave From and To some 60px each, and a
 * workplace's name would stack a word to a line. So each change is one row read in the plan's order -- when and who down
 * the left, what changed on the right with its old value over its new one, the From and To values starting on one line
 * down the sheet. The head names all five. It is a list of changes, each a description list: the Table primitive's
 * columns do not fit the panel, and its `secondary` priority answers the viewport, not the panel.
 *
 * 🔑 A CHUNK OF ITS OWN (`Members.jsx` imports it with `lazy`): nothing of it is fetched until the tab is opened, never at
 * the register's first paint -- and then the one read it makes, `getMemberHistory`, imported by name.
 *
 *   loading  the head stays, a line saying so, three placeholder rows, and the pager saying *Loading changes…*;
 *   failed   *The history could not be loaded.* -- never an empty history -- with *Try again* where a retry can work and
 *            the failure's details behind a disclosure; an answer of the wrong shape, or a server with no such route,
 *            is *not available*;
 *   empty    *No changes recorded since* the day the history began (ME-7), or *yet* where it has not begun;
 *   ready    the sheet, newest first, and the pager, which counts entries as the server does: a save of three fields
 *            is three, and one row that says so.
 *
 * `HISTORY_PAGE_INVALID` -- a page that no longer exists -- goes back to the first page and says so in the server's
 * words; asking for it again would get the same answer, so there is no *Try again* for it.
 */

/*
 * ⚖️ THE CSS IS SPENT ON WHAT THE LAYOUT NEEDS, AND ON NOTHING ELSE. The stylesheet was within 0.4 KiB of its ceiling,
 * and the mock's own measures -- 5px, 7px, 13px, 12.5px, a line height apiece -- each cost a rule of its own. So every
 * spacing, size and rule here is one the build already ships, within a pixel or two of the mock (the ramp's `text-sm`
 * for its 12.5px, `text-xs` for its 12px, `py-3.5` for its 13/14px), and the new ones are only these: the two fixed
 * measures that make the sheet a sheet -- the When column, and the labels' column that starts every From and every To
 * on one line down the page -- the bleed into the record's gutter, baseline alignment, and one line height for all of it.
 */
/** The sheet's two measures, the mock's own: the When column, and the From / To / Reason / Note labels. */
const ROW = 'grid grid-cols-[5rem_minmax(0,1fr)] gap-x-3 px-3';
const VALUES = 'grid grid-cols-[3.5rem_minmax(0,1fr)] items-baseline gap-x-3 gap-y-1';
const TERM = 'text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted';
const WHAT = 'text-base font-semibold text-ink';
const VALUE = 'min-w-0 text-base [overflow-wrap:anywhere]';
/**
 * A value's second line -- a workplace's R/A number, the reason a member gave under SASBO's code -- and the When column:
 * the mock's 12.5px, the size the record's own foot already ships (`MemberPanel`), so it costs no rule. 13px would not
 * do in the When column: "Not recorded" no longer fits its 5rem, and breaks.
 */
const SMALL = 'text-[12.5px]';
const SUB = cn('mt-0.5 block font-normal text-ink-muted', SMALL);
/**
 * 🔢 TABULAR FIGURES FOR A NUMBER, AND FOR NOTHING ELSE -- as the mock sets them (`.num`). This font gives its colon and
 * its full stop figure width under `tabular-nums`, so a sentence set that way reads "email ." and "Invented : letter".
 * A number-shaped value -- a phone number, a date, an amount -- keeps its figures in step with the one above it.
 */
const NUMBER_SHAPED = /^(R\s)?[+\d][\d\s\-/.,]*$/;
const figures = (text) => (NUMBER_SHAPED.test(text) ? 'tabular-nums' : null);
/**
 * An e-mail address breaks after its "@" and after its dots, never mid-word (MO24): the column is about twenty
 * characters of one, and `overflow-wrap: anywhere` alone broke "synthetic.member@exampl" from "e.invalid". The
 * anywhere stays, for a part that is longer than the column on its own.
 */
const breakable = (text) =>
  (text.includes('@') ? text.split(/([@.])/).flatMap((part, i) => (i % 2 ? [part, <wbr key={i} />] : [part])) : text);
/** The small print beside a change: the mask's reason, and a save that runs on past the page. */
const ASIDE = 'text-xs font-normal text-ink-muted';

const EMPTY_MESSAGE =
  'Nothing on this record has changed since the history was switched on. Changes made before then were not recorded.';

/** The shape of what is coming, as the mock draws it: three rows, each a What over a From and a To. */
const PLACEHOLDERS = [[58, 62, 48], [40, 74, 70], [52, 44, 56]];

/**
 * The saveId of the entry just past one edge of a page -- `undefined` where there is no page beyond that edge or the row
 * there is not a save, {@link UNKNOWN} where it could not be read. Asked for as a page of ONE entry, whose page number is
 * then its place in the history; only where the row at the edge is a save, so a history of one page asks nothing more.
 */
const edgeSave = (memberId, answer, side) => {
  const start = answer.page * answer.size;
  const edge = side === 'before' ? answer.content[0] : answer.content.at(-1);
  const at = side === 'before' ? start - 1 : start + answer.content.length;
  if (edge?.kind !== 'FIELD' || !edge.saveId || at < 0 || at >= answer.totalElements) return Promise.resolve(undefined);
  return getMemberHistory(memberId, at, 1).then(
    (body) => {
      const next = historyPageOf(body);
      return next?.content.length ? (next.content[0].saveId ?? null) : UNKNOWN;
    },
    (error) => {
      reportError('MemberHistory.edge', error);
      return UNKNOWN;
    },
  );
};

const isStatus = (entry) => entry.kind === 'STATUS' || entry.what === 'status';

/** The head names all five, as the plan's sheet does -- for the eye; each row says what it holds to a screen reader. */
function Head() {
  return (
    <div
      aria-hidden="true"
      className={cn(ROW, 'whitespace-nowrap border-b border-line-strong bg-surface-2 pb-2 pt-[9px]',
        'text-xs font-semibold uppercase tracking-[0.08em] text-head-ink')}
    >
      <span>When · Who</span>
      <span className="truncate">What changed · From · To</span>
    </div>
  );
}

/** The loading state's rows: the shape of what is coming, so nothing jumps when the changes land. */
function Placeholders() {
  return (
    <ol aria-hidden="true" className="divide-y divide-line">
      {PLACEHOLDERS.map(([what, from, to]) => (
        <li key={`${what}-${from}-${to}`} className={cn(ROW, 'py-3.5')}>
          {/* `.skeleton` is unlayered (index.css), so each is sized inline: a utility would lose to it. */}
          <div className="grid content-start gap-1.5">
            <span className="skeleton" style={{ width: 70, height: 12 }} />
            <span className="skeleton" style={{ width: 36, height: 11 }} />
          </div>
          <div className="grid gap-2">
            <span className="skeleton" style={{ width: `${what}%`, height: 13, marginBottom: 3 }} />
            {[from, to].map((width) => (
              <span key={width} className="grid grid-cols-[3.5rem_minmax(0,1fr)] items-center gap-x-3">
                <span className="skeleton" style={{ width: 34, height: 9 }} />
                <span className="skeleton" style={{ width: `${width}%`, height: 11 }} />
              </span>
            ))}
          </div>
        </li>
      ))}
    </ol>
  );
}

/**
 * One value of a change, as the record reads it: a status as its chip; a workplace as its name over its R/A number; a
 * number the server keeps only the last four of as `••••1234`, read aloud as *ending 1234*; anything missing as *Not on
 * file*. The old value in the quieter ink, the new one in the record's. `aside` is small print under the value -- the
 * mask's reason, said once, under the new value.
 */
function Value({ entry, value, side, aside = null }) {
  const text = valueText(entry.what, value);
  const under = aside && <span className={cn(ASIDE, 'mt-0.5 block')}>{aside}</span>;
  if (text === null) return <dd className={cn(VALUE, 'font-normal text-ink-muted')}>Not on file{under}</dd>;
  if (isStatus(entry)) return <dd className={VALUE}><StatusChip status={text} /></dd>;
  const tone = side === 'from' ? 'text-ink-muted' : 'font-medium text-ink';
  const tail = maskedTail(text);
  if (tail !== null) {
    return (
      <dd className={cn(VALUE, tone)}>
        <span className="tabular-nums">
          <span aria-hidden="true">••••</span>
          <span className="sr-only">{tail ? 'ending ' : 'kept hidden'}</span>
          {tail}
        </span>
        {under}
      </dd>
    );
  }
  const workplace = entry.what === 'workplace' ? workplaceOf(text) : null;
  if (workplace) {
    return (
      <dd className={cn(VALUE, tone)}>
        {workplace.name}
        <span className={cn(SUB, 'tabular-nums')}>R/A {workplace.raNumber}</span>
        {under}
      </dd>
    );
  }
  return <dd className={cn(VALUE, tone)}><span className={figures(text)}>{breakable(text)}</span>{under}</dd>;
}

/**
 * One change: what changed, named by the record's own labels (`labelOf`), with its From over its To. A change of status
 * adds SASBO's code and its name -- *Code 2 · Resigned Society* -- with the reason the member gave under it as that
 * cell's second line (04.5), and the note written with it beneath. A capture is one line: *Captured as M-900417*.
 */
function Change({ entry, labelOf }) {
  if (entry.kind === 'FIELD' && entry.what === 'captured') {
    return (
      <p className={WHAT}>
        Captured
        {entry.to && (
          <>
            {' as '}
            <span className="font-semibold tracking-[0.06em] text-brand-ink tabular-nums">{entry.to}</span>
          </>
        )}
      </p>
    );
  }
  const status = entry.kind === 'STATUS';
  const reason = status ? statusReasonText(entry) : null;
  const note = status && typeof entry.note === 'string' && entry.note.trim() ? entry.note : null;
  const masked = !isStatus(entry) && [entry.from, entry.to].some((value) => maskedTail(value) !== null);
  return (
    <div>
      <p className={cn(WHAT, 'mb-1.5')}>{isStatus(entry) ? STATUS_LABEL : labelOf(entry.what)}</p>
      <dl className={VALUES}>
        <dt className={TERM}>From</dt>
        <Value entry={entry} value={entry.from} side="from" />
        <dt className={TERM}>To</dt>
        <Value entry={entry} value={entry.to} side="to" aside={masked ? 'Only the last four digits are kept.' : null} />
        {reason && (
          <>
            <dt className={TERM}>Reason</dt>
            <dd className={cn(VALUE, 'font-medium text-ink')}>
              {reason}
              {entry.reasonUnderCode && <span className={SUB}>{entry.reasonUnderCode}</span>}
            </dd>
          </>
        )}
        {note && (
          <>
            <dt className={TERM}>Note</dt>
            <dd className={cn(VALUE, 'text-ink')}>{note}</dd>
          </>
        )}
      </dl>
    </div>
  );
}

/**
 * One row of the sheet: when and who down the left -- *Not recorded* until sign-in -- and the change on the right. A save
 * of several fields is one row that says how many; one that runs on past the page says so at that edge.
 */
function HistoryRow({ row, labelOf }) {
  const when = whenOf(row.first.changedAt);
  const count = row.entries.length;
  return (
    <li className={cn(ROW, 'py-3.5')} data-history-row="">
      <div className={cn('grid content-start text-ink-muted', SMALL)}>
        <time dateTime={row.first.changedAt || undefined} className="grid">
          <span className="text-sm font-medium text-ink tabular-nums">{when.date}</span>
          {/* Read, not stacked against a column of sums: proportional figures, so the colon does not stand apart. */}
          {when.time && <span>{when.time}</span>}
        </time>
        <span className="mt-1">
          <span className="sr-only">Who: </span>
          {whoOf(row.first.changedBy)}
        </span>
        {isSaveOfSeveral(row) && (
          <span className="mt-1.5 text-xs" data-history-save="">
            {count} {count === 1 ? 'change' : 'changes'}{' '}
            <br />
            in one save
          </span>
        )}
      </div>
      <div className="grid min-w-0 gap-y-3.5">
        {row.continuedFrom !== false && (
          <p className={ASIDE}>
            {row.continuedFrom ? 'Continued from the previous page.' : 'This save may have begun on the previous page.'}
          </p>
        )}
        {row.entries.map((entry) => <Change key={`${entry.kind}:${entry.id}`} entry={entry} labelOf={labelOf} />)}
        {row.continuesOn !== false && (
          <p className={ASIDE}>
            {row.continuesOn ? 'Continues on the next page.' : 'This save may continue on the next page.'}
          </p>
        )}
      </div>
    </li>
  );
}

/**
 * @param {{ member: object, labelOf: (field: string) => string }} props -- the record open in the panel, and the
 *   record's own field labels (`fieldLabel` in `Members.jsx`), so a change is named as the record names its field.
 */
export default function MemberHistory({ member, labelOf }) {
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [notice, setNotice] = useState(null);
  const [read, setRead] = useState({ question: null });
  /*
   * WHAT LIES JUST PAST EACH EDGE OF THE PAGE -- read after the page, never before it. The rows are shown the moment the
   * page arrives, and a save at an edge says nothing of the page beyond until the entry there has been read: an answer
   * held back for it cost every multi-page history a round trip, and a look that hung would have held the whole sheet.
   */
  const [edges, setEdges] = useState({ question: null });
  const sectionRef = useRef(null);

  /*
   * 🔑 A SAVE REPLACES THE RECORD WITH A NEW OBJECT FOR THE SAME MEMBER (`Members.jsx`), and the tab stays where it was:
   * the history is then read again from its first page, where the change just made is. A different member is a
   * different tab altogether -- `Members.jsx` keys this by the member's id.
   */
  const [seen, setSeen] = useState(member);
  if (member !== seen) {
    setSeen(member);
    setPage(0);
    setNotice(null);
    setAttempt((n) => n + 1);
  }

  const memberId = member.id;
  const question = `${memberId}|${page}|${attempt}`;
  useEffect(() => {
    let current = true;
    const asked = `${memberId}|${page}|${attempt}`;
    const backToFirst = (sentence) => {
      setNotice(sentence);
      setPage(0);
    };
    getMemberHistory(memberId, page, HISTORY_PAGE_SIZE)
      .then((body) => {
        if (!current) return;
        const answer = historyPageOf(body);
        if (answer && !answer.content.length && page > 0) {
          backToFirst(pageGoneSentence(null));
          return;
        }
        setRead({ question: asked, answer, failure: answer ? null : failureOf(null) });
        if (!answer) return;
        // `edgeSave` settles every way it can end -- a look that fails is UNKNOWN -- so this never rejects.
        Promise.all([edgeSave(memberId, answer, 'before'), edgeSave(memberId, answer, 'after')])
          .then(([before, after]) => {
            if (current) setEdges({ question: asked, before, after });
          });
      })
      .catch((error) => {
        if (!current) return;
        if (error?.body?.code === HISTORY_PAGE_INVALID && page > 0) {
          backToFirst(pageGoneSentence(error));
          return;
        }
        reportError('MemberHistory.read', error);
        setRead({ question: asked, answer: null, failure: failureOf(error) });
      });
    return () => {
      current = false;
    };
  }, [memberId, page, attempt]);

  const pending = read.question !== question;
  const failure = pending ? null : read.failure;
  const answer = pending || failure ? null : read.answer;
  const view = pending ? 'loading' : failure ? 'error' : answer.content.length ? 'ready' : 'empty';
  // Until the entries past the edges are read, nothing is said of them: no page beyond, as far as the rows can tell.
  const edgesKnown = edges.question === question ? edges : {};
  const rows = view === 'ready' ? historyRows(answer.content, { before: edgesKnown.before, after: edgesKnown.after }) : [];
  const first = answer ? answer.page * answer.size : 0;
  const began = answer?.historyFrom ? formatDateLong(answer.historyFrom) : EMPTY;

  /*
   * A person's turn of the page. The pager's controls shut while the next page loads, and a control that shuts takes
   * the keyboard's place with it -- so the place moves to the top of the sheet, which is where the new page begins.
   */
  const turnTo = (next) => {
    setNotice(null);
    setPage(next);
    const section = sectionRef.current;
    section?.focus({ preventScroll: true });
    section?.scrollIntoView?.({ block: 'start' });
  };
  const retry = () => {
    setNotice(null);
    setAttempt((n) => n + 1);
  };

  return (
    <section
      ref={sectionRef}
      tabIndex={-1}
      aria-label="History of changes, newest first"
      data-history={view}
      className="-mx-3 rounded-sm leading-[1.4] focus:outline-none focus-visible:outline-2 focus-visible:outline-brand-ring"
    >
      {notice && <p role="status" className="px-3 pb-3 text-sm text-ink-muted">{notice}</p>}
      {view === 'ready' && olderThanHistory(member, answer.historyFrom) && (
        <p className="px-3 pb-3 text-sm text-ink-muted">History begins {began}</p>
      )}

      {(view === 'loading' || view === 'ready') && <Head />}
      {/* The two states' spacing is the mock's, set on a wrapper: `DataState` carries padding of its own, and one
          `p-0` beats it outright where a side-by-side `pt-*` over its `py-*` would be a question of stylesheet order. */}
      {view === 'loading' && (
        <>
          <div className="px-3 pb-1 pt-3.5">
            <DataState state="loading" layout="row" compact className="gap-2.5 p-0" />
          </div>
          <Placeholders />
        </>
      )}
      {view === 'ready' && (
        <ol className="divide-y divide-line">
          {rows.map((row) => <HistoryRow key={row.key} row={row} labelOf={labelOf} />)}
        </ol>
      )}
      {(view === 'error' || view === 'empty') && (
        <div className="px-5 py-8">
          <DataState
            state={view}
            className="gap-2 p-0"
            error={failure}
            onRetry={retry}
            title={view === 'error'
              ? 'The history could not be loaded.'
              : began === EMPTY ? 'No changes recorded yet' : `No changes recorded since ${began}`}
            message={view === 'error' ? failureMessage(failure) : EMPTY_MESSAGE}
          />
        </div>
      )}

      {view !== 'empty' && (
        <Pager
          className="px-3 py-2.5"
          noun="changes"
          page={answer ? answer.page + 1 : page + 1}
          totalPages={answer ? answer.totalPages : 1}
          count={answer ? answer.totalElements : 0}
          start={first + 1}
          end={first + (answer ? answer.content.length : 0)}
          loading={view === 'loading'}
          error={view === 'error'}
          onPageChange={(next) => turnTo(next - 1)}
        />
      )}
    </section>
  );
}
