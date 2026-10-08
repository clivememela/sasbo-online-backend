import { useEffect, useId, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';

import Criteria, { CRITERION } from '../../components/common/Criteria.jsx';
import DataState from '../../components/common/DataState.jsx';
import PageTitle from '../../components/common/PageTitle.jsx';
import Pager from '../../components/common/Pager.jsx';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import SelectInput from '../../components/form/SelectInput.jsx';
import Table from '../../components/table/Table.jsx';
import { UNGROUPED_BECAUSE, listRows, monthLabel, placeOptions, placeQuery } from '../../config/monthEnd.js';
import reportError from '../../lib/reportError.js';
import { STATISTIC_READS } from '../../services/monthEndService.js';
import { getAreas, getRegions } from '../../services/referenceService.js';
import {
  MONTH_CRITERION, SHEET_PAGE, cellText, defaultMonth, failureOf, instantLabel, monthChoices, rowHeader, ruleCNote,
  statisticNamed, statisticOf,
} from './monthEndRules.js';
import useMonths from './useMonths.js';

/**
 * A STATISTIC'S SCREEN -- month end `04.3` to `04.9`, one lazy route for the seven: `/admin/monthly-menu/:statistic`,
 * the month and the place in the address (`?month=2026-08&area=A`), so a statistic can be sent to somebody as it is.
 *
 * The Card world's by rule (screens.md §0.3): page head · criteria row -- the month, and an area or a region · a ruled
 * sheet for each of the server's sheets, laid out as SASBO's report is, the total its last row · the notes beneath.
 * ONE SHEET RENDERER draws all seven statistics (`StatisticSheet`): the server says what the columns are and what kind
 * each is, and this reads it.
 *
 *   the month   chosen from the server's months, as the Month end page does; with none in the address, the newest
 *               month taken (MD55). A taken month says *as taken on … by …*; the open month that it is read live;
 *   the place   SASBO's areas and regions, asked for when the control is opened and kept -- never at first paint (MD69);
 *   rule C      a row of fewer than five reads *fewer than five* and a row withheld beside it *withheld* -- never a
 *               number -- each pointing at the note that says why;
 *   the states  loading · failed · empty · ready, and a failed read is never an empty one. A refusal is said in the
 *               server's own sentence (`MONTH_NOT_TAKEN`, `MONTH_NOT_BEGUN`, `REQUEST_MALFORMED`,
 *               `SUMMARY_AREA_UNKNOWN`), never as its code; a statistic the server has none of is a not-found state.
 */

/** An answer this screen cannot read -- the wrong shape, or a server built before the route (MV-36, MR-ROB-31). */
const NOT_AVAILABLE = 'This statistic is not available from this server yet.';

const ALL_AREAS = placeOptions([], []);

/** A link in a sentence: the brand's ink, underlined, and the ring when it has the keyboard. */
const IN_TEXT = 'rounded-sm font-medium text-brand-ink underline underline-offset-2 '
  + 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring';

/** The place in the address as the Area control spells it: `area:A`, `region:NORTH`, or all of SASBO. */
const placeOf = (area, region) => (area ? `area:${area}` : region ? `region:${region}` : '');

/** A row of a sheet's heading or total level stands apart from the rows it sums. */
const strong = (row, text) => (row.level === 'ROW' ? text : <strong className="font-semibold">{text}</strong>);

/**
 * ONE SHEET, as the server lays it out -- its title with the legacy title beneath, its columns in the server's order,
 * a heading row (`GROUP`) and a total (`TOTAL`) set apart, and the sheet's total as its last row. Counts, money and
 * shares each through the one formatter for their kind. A row of fewer than five, or one withheld, shows the words
 * and no number, and names the note that explains it (`why`).
 */
function StatisticSheet({ sheet, month, why }) {
  const id = useId();
  const [page, setPage] = useState(1);
  // 📄 A sheet longer than a page is paged, its total the last row of every page: 04.9 holds every branch -- on the link,
  // at `all`, some 5,500 rows, 814 KB, and a phone drew every one. Each new question mounts the sheet afresh, at page 1.
  const paged = sheet.rows.length > SHEET_PAGE;
  const pages = paged ? Math.ceil(sheet.rows.length / SHEET_PAGE) : 1;
  const current = Math.min(page, pages);
  const first = (current - 1) * SHEET_PAGE;
  const shown = paged ? sheet.rows.slice(first, first + SHEET_PAGE) : sheet.rows;
  const columns = [
    { key: 'label', header: rowHeader(sheet.key), nowrap: false, cell: (row) => strong(row, row.label) },
    ...sheet.columns.map((column, index) => ({
      key: `c:${column.key}`,
      header: column.label,
      align: 'right',
      className: 'tabular-nums',
      // The first figure carries the words of a row with no number; a phone wraps them rather than widening the sheet.
      ...(index === 0 && { nowrap: false }),
      cell: (row) => {
        if (row.fewerThanFive || row.withheld) {
          return index === 0 && (
            <span className="text-ink-muted" aria-describedby={why}>{row.withheld ? 'withheld' : 'fewer than five'}</span>
          );
        }
        return strong(row, cellText(column.kind, row.cells?.[index]));
      },
    })),
  ];
  return (
    <section aria-labelledby={id} className="mt-8" data-sheet={sheet.key}>
      <h2 id={id} className="text-lg font-semibold text-ink">{sheet.title}</h2>
      {sheet.legacyTitle && <p className="mt-0.5 text-sm text-ink-muted">{sheet.legacyTitle}</p>}
      <Sheet className="mt-3">
        {sheet.rows.length ? (
          <>
            <Table caption={sheet.title} columns={columns} rows={sheet.total ? [...shown, sheet.total] : shown}
              rowKey="key" dense />
            {/* error={false}, said and not defaulted (contract ⑮): a sheet is drawn only from an answer in hand -- the
                page's `ready` -- so its rows cannot have failed; a read that failed is the page's own state, above. */}
            {paged && (
              <Pager page={current} totalPages={pages} onPageChange={setPage} count={sheet.rows.length}
                start={first + 1} end={first + shown.length} noun="rows" error={false} />
            )}
          </>
        ) : (
          /* 📱 A sheet with no rows says so across the SHEET's width, not the table's: a wide sheet scrolls, and its
             empty row spanned eleven columns, so a phone cut the sentence off at its edge (the width matrix, 390). With
             nothing counted there is no total to add up either, and no line of noughts is ruled. */
          <DataState state="empty" layout="row" title={`Nothing was counted on this sheet for ${monthLabel(month)}.`} />
        )}
      </Sheet>
    </section>
  );
}

/** A statistic the server has none of -- a not-found state, never a blank page. */
function NoSuchStatistic({ asked }) {
  return (
    <Page>
      <PageTitle title="No such statistic" subtitle="The Month end page lists the statistics that are built." />
      <Sheet className="mt-6 max-w-4xl px-6 py-7">
        <p className="text-base text-ink">
          There is no statistic called “{asked}” here.{' '}
          <Link to="/admin/monthly-menu" className={IN_TEXT}>Go to Month end</Link>
        </p>
      </Sheet>
    </Page>
  );
}

function StatisticScreen({ statistic }) {
  const [params, setParams] = useSearchParams();
  const askedMonth = params.get('month') ?? '';
  const area = params.get('area') ?? '';
  const region = area ? '' : params.get('region') ?? '';

  // The months: the choices, and the month to open on when the address names none.
  const { months, pending: monthsPending, failure: monthsFailure, retry: retryMonths } = useMonths('MonthEndStatistic.months');
  const month = askedMonth || (months ? defaultMonth(months) : '');

  // The statistic, read for the month and the place asked -- and again only when a person asks.
  const [attempt, setAttempt] = useState(0);
  const question = `${month}|${area}|${region}|${attempt}`;
  const [read, setRead] = useState({ question: null });
  useEffect(() => {
    if (!month) return undefined;
    let current = true;
    const asked = `${month}|${area}|${region}|${attempt}`;
    STATISTIC_READS[statistic.key](month, { area, region })
      .then((body) => {
        const answer = statisticOf(body);
        if (current) setRead({ question: asked, answer, failure: answer ? null : failureOf(null, NOT_AVAILABLE) });
      })
      .catch((error) => {
        reportError('MonthEndStatistic.read', error);
        if (current) setRead({ question: asked, answer: null, failure: failureOf(error, NOT_AVAILABLE) });
      });
    return () => {
      current = false;
    };
  }, [statistic.key, month, area, region, attempt]);

  // SASBO's areas and regions, asked for when the control is first opened and kept for the visit (MD69).
  const [places, setPlaces] = useState(null);
  const placesAsked = useRef(false);
  const askPlaces = () => {
    if (placesAsked.current) return;
    placesAsked.current = true;
    Promise.all([getRegions(), getAreas()])
      .then(([regions, areas]) => setPlaces({ options: placeOptions(listRows(regions) ?? [], listRows(areas) ?? []) }))
      .catch((error) => {
        reportError('MonthEndStatistic.places', error);
        placesAsked.current = false;
        setPlaces({ error });
      });
  };

  /** A person's question: the month or the place changed -- the address says so, and the sheet is read for it. */
  const change = (next) => {
    const merged = { month: askedMonth, area, region, ...next };
    const query = new URLSearchParams();
    if (merged.month) query.set('month', merged.month);
    if (merged.area) query.set('area', merged.area);
    else if (merged.region) query.set('region', merged.region);
    setParams(query, { replace: true });
  };

  const place = placeOf(area, region);
  const placeChoices = places?.options ?? (place
    ? [...ALL_AREAS, { value: place, label: area ? `Area ${area}` : `${region} region` }]
    : ALL_AREAS);

  // No month known: the months are still coming, or could not be read -- then that is the screen's failure.
  const pending = month ? read.question !== question : monthsPending;
  const failure = month ? read.failure : monthsFailure;
  const answer = !pending && !failure ? read.answer : null;
  const view = pending ? 'loading' : failure ? 'error' : answer.sheets.length ? 'ready' : 'empty';
  const note = answer ? ruleCNote(answer.notes) : -1;
  const why = note >= 0 ? `month-end-note-${note}` : undefined;

  return (
    <Page>
      <PageTitle title={statistic.name} subtitle={statistic.legacy} />

      <Criteria className="mt-6" label="Which figures">
        <SelectInput
          className={MONTH_CRITERION}
          label="Month"
          value={month}
          options={months ? monthChoices(months, askedMonth) : month ? [{ value: month, label: monthLabel(month) }] : []}
          loading={monthsPending && !month}
          loadError={monthsFailure}
          onRetryLoad={retryMonths}
          onChange={(value) => change({ month: value })}
        />
        <SelectInput
          className={CRITERION}
          label="Area"
          value={place}
          options={placeChoices}
          loadError={places?.error}
          onRetryLoad={askPlaces}
          onFocus={askPlaces}
          onMouseDown={askPlaces}
          onChange={(value) => change({ area: '', region: '', ...placeQuery(value) })}
        />
      </Criteria>

      {answer && (
        <p className="mt-3 max-w-prose text-sm text-ink-muted" data-month-state={answer.state}>
          {answer.state === 'TAKEN'
            ? `${monthLabel(answer.month)}, as taken on ${instantLabel(answer.takenAt)}${answer.takenBy ? ` by ${answer.takenBy}` : ''}: `
              + 'the month as it stood then.'
            : `${monthLabel(answer.month)} is the open month: these figures are read live, and move until it is taken.`}
          {UNGROUPED_BECAUSE[answer.workplacesServed] && ` ${UNGROUPED_BECAUSE[answer.workplacesServed]}`}
        </p>
      )}

      {view === 'ready' ? (
        <>
          {answer.sheets.map((sheet) => (
            <StatisticSheet key={`${question}|${sheet.key}`} sheet={sheet} month={answer.month} why={why} />
          ))}
          {answer.notes?.length > 0 && (
            <section aria-labelledby="month-end-notes" className="mt-8 max-w-prose">
              <h2 id="month-end-notes" className="text-lg font-semibold text-ink">Notes</h2>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-ink-muted">
                {/* By position: the notes are the server's list for this answer, in its order, and two may read alike. */}
                {answer.notes.map((note, index) => <li key={`${index}`} id={`month-end-note-${index}`}>{note}</li>)}
              </ul>
            </section>
          )}
          <p className="mt-6 max-w-prose text-sm text-ink-muted">
            These sheets are in the statistics pack’s workbooks, which the{' '}
            <Link to={`/admin/monthly-menu?month=${encodeURIComponent(answer.month)}`} className={IN_TEXT}>
              Month end page
            </Link>{' '}
            prepares.
          </p>
        </>
      ) : (
        <Sheet className="mt-6">
          <DataState
            state={view}
            layout="row"
            error={view === 'error' ? failure : null}
            onRetry={month ? () => setAttempt((n) => n + 1) : retryMonths}
            title={view === 'loading' ? `Working out ${statistic.name.toLowerCase()}…`
              : view === 'empty' ? `${statistic.name} has no sheets for ${monthLabel(answer.month)}.` : undefined}
          />
        </Sheet>
      )}
    </Page>
  );
}

/** The route's page: the statistic its address names, or the not-found state for a name the server has none of. */
export default function MonthEndStatistic() {
  const { statistic: key } = useParams();
  const statistic = statisticNamed(key);
  // Keyed by the statistic, so a move from one to another starts afresh rather than showing the last one's sheets.
  return statistic ? <StatisticScreen key={statistic.key} statistic={statistic} /> : <NoSuchStatistic asked={key} />;
}
