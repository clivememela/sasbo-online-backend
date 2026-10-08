import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Printer, Search } from 'lucide-react';

import { Button, TextInput } from '../../components/form';
import DataState from '../../components/common/DataState.jsx';
import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import RequirePermission from '../../auth/RequirePermission.jsx';
import { PERMISSIONS } from '../../auth/permissions';
import { useAuth } from '../../auth/useAuth';
import { LIST_EMPTY_REASON } from '../../config/monthEnd.js';
import { formatDateLong, formatInt, today } from '../../lib/format.js';
import { MAX_RA_NUMBERS, groupByBank, parseRaNumbers } from '../../lib/raNumbers.js';
import reportError from '../../lib/reportError.js';
import { locationService } from '../../services/locationService.js';
import WorkplaceListing from './WorkplaceListing.jsx';

/**
 * THE ORGANISERS' BATCH PRINT (October build, 5 Oct Show & Tell). An organiser with twenty branches to visit this
 * month typed one R/A number, printed, and typed the next: "let me put all 20 numbers in at once, click print, and
 * then it prints everything. And not like 1 by one." -- and "if you print them, separate them by bank ... if it's a
 * new bank, start it [on a new page]".
 *
 * So: the numbers are typed or pasted all at once (spaces, commas or new lines; up to fifty), read here -- each once,
 * the repeats and anything that is not a number said before anything is asked -- and fetched in ONE request. A number
 * that is no workplace here is named, and the rest still print. The listings are grouped by bank, each bank a section
 * that begins a new printed page with its own heading -- the bank, the day it was printed, and the organiser's area --
 * on A4, in the light palette paper always gets.
 *
 * One R/A number is a batch of one: the single listing an organiser printed before is this screen with one number.
 *
 * The members under the branches are read in ONE request for the whole batch (October build review: not one a branch,
 * fifty requests at once against a server sized for a handful) and need `MEMBERS_VIEW`; an answer that arrives after its
 * listings have gone -- the numbers shown again, the page left -- is dropped. Print waits until every branch has its
 * members or the sentence that says why not, so a sheet never prints half-loaded.
 *
 * 🔒 The listings are held by `REPORTS_PRINT` ("organisers' reports, batch print, location listings"): a signed-in user
 * without it is told what they need; nobody signed in keeps the page while sign-in is open (contracts/signin.md).
 */

/** What the page says of the numbers as they are typed: how many will print, what repeats, what is not a number. */
function readingOf(parsed) {
  const parts = [];
  if (parsed.numbers.length) {
    parts.push(`${formatInt(parsed.numbers.length)} R/A number${parsed.numbers.length === 1 ? '' : 's'} to print`);
  }
  if (parsed.repeated.length) {
    parts.push(`${parsed.repeated.join(', ')} typed more than once, printed once`);
  }
  if (parsed.notNumbers.length) {
    const shown = parsed.notNumbers.slice(0, 5).map((token) => `“${token.slice(0, 12)}”`).join(', ');
    const more = parsed.notNumbers.length > 5 ? ` and ${parsed.notNumbers.length - 5} more` : '';
    parts.push(`${shown}${more} ${parsed.notNumbers.length === 1 ? 'is' : 'are'} not an R/A number`);
  }
  return parts.join(' · ');
}

/** The heading's area: the organiser's own, where their scope names one; else the areas of the branches printed. */
function areaOf(user, listings) {
  const scope = user?.scope;
  if (scope?.type === 'AREA' && scope.codes?.length) return `Organiser's area: ${scope.codes.join(', ')}`;
  if (scope?.type === 'REGION' && scope.codes?.length) return `Region: ${scope.codes.join(', ')}`;
  const areas = [...new Set(listings.map((listing) => listing.workplace.area).filter(Boolean))];
  return areas.length ? `Area${areas.length === 1 ? '' : 's'}: ${areas.join(', ')}` : '';
}

const NOT_AVAILABLE = Object.assign(new Error('The listings are not available from this server yet.'),
  { retryable: false });

const MEMBERS_NOT_AVAILABLE = Object.assign(new Error('The members could not be read from this server.'),
  { retryable: false });

export function BatchPrint() {
  const { user, status: authStatus, can } = useAuth();
  const [text, setText] = useState('');
  const [read, setRead] = useState({ state: 'idle' });
  const [attempt, setAttempt] = useState(0);
  const [membersRead, setMembersRead] = useState({});
  const [membersAttempt, setMembersAttempt] = useState(0);
  const printHeld = authStatus === 'signed-in' && !can(PERMISSIONS.REPORTS_PRINT);

  const parsed = useMemo(() => parseRaNumbers(text), [text]);
  const tooMany = parsed.tooMany
    ? `Up to ${MAX_RA_NUMBERS} R/A numbers can be printed at a time; ${formatInt(parsed.numbers.length)} are typed.`
    : undefined;

  const show = async (numbers) => {
    setRead({ state: 'loading' });
    try {
      const body = await locationService.getListings(numbers);
      if (!Array.isArray(body?.listings)) throw NOT_AVAILABLE;
      setRead({ state: 'ready', body });
    } catch (error) {
      reportError('BatchPrint.read', error);
      setRead({ state: 'error', error, numbers });
    }
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!parsed.numbers.length || parsed.tooMany) return;
    setAttempt((n) => n + 1);
    show(parsed.numbers);
  };

  const body = read.state === 'ready' ? read.body : null;
  const groups = useMemo(() => groupByBank(body?.listings), [body]);
  const listings = body?.listings ?? [];
  const ids = useMemo(() => (body?.listings ?? []).map((listing) => listing.workplace.id), [body]);

  // The members of every branch shown, in one read -- asked only of a user who may see them, and answered to the
  // listings that asked: an answer for listings since replaced is dropped (`current`), never shown under others.
  const membersQuestion = can(PERMISSIONS.MEMBERS_VIEW) && ids.length ? `${attempt}|${ids.join(',')}|${membersAttempt}` : null;
  useEffect(() => {
    if (!membersQuestion) return undefined;
    let current = true;
    (async () => {
      try {
        const answer = await locationService.getMembersOf(ids);
        if (!Array.isArray(answer?.branches)) throw MEMBERS_NOT_AVAILABLE;
        if (current) {
          setMembersRead({ asked: membersQuestion, byId: new Map(answer.branches.map((branch) => [branch.workplaceId, branch])) });
        }
      } catch (error) {
        if (!current) return;
        reportError('BatchPrint.members', error);
        setMembersRead({ asked: membersQuestion, failure: error });
      }
    })();
    return () => {
      current = false;
    };
  }, [membersQuestion, ids]);

  const membersPending = Boolean(membersQuestion) && membersRead.asked !== membersQuestion;
  /** The batch's members as one branch sees them -- see `BranchMembers`; nothing where they are not to be read. */
  const membersOf = (id) => {
    if (!membersQuestion) return undefined;
    if (membersPending) return { state: 'loading' };
    if (membersRead.failure) {
      return { state: 'error', error: membersRead.failure, onRetry: () => setMembersAttempt((n) => n + 1) };
    }
    return { state: 'ready', branch: membersRead.byId.get(id) };
  };
  const ready = listings.length > 0 && !membersPending;
  const printedOn = formatDateLong(today());

  if (printHeld) {
    return (
      <Page>
        <PageTitle title="Batch print" />
        <div className="mt-5 max-w-3xl">
          <RequirePermission permission={PERMISSIONS.REPORTS_PRINT} />
        </div>
      </Page>
    );
  }

  return (
    <Page>
      <PageTitle
        title="Batch print"
        subtitle="Type or paste the R/A numbers of the branches you will visit and print every location listing at once. Each bank starts on a new page."
      />

      <Sheet className="no-print mt-5 p-5">
        <form onSubmit={handleSubmit} className="grid gap-3">
          <TextInput
            label="R/A numbers"
            name="raNumbers"
            multiline
            rows={4}
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="For example: 1204, 1388, 2051"
            hint={`Separate them with spaces, commas or new lines. Up to ${MAX_RA_NUMBERS} at a time.`}
            error={tooMany}
          />
          <p aria-live="polite" className="min-h-5 text-sm text-ink-muted">{readingOf(parsed)}</p>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" variant="primary" loading={read.state === 'loading'}
              disabled={!parsed.numbers.length || parsed.tooMany}>
              <Search size={16} strokeWidth={1.75} aria-hidden="true" />
              Show listings
            </Button>
            {read.state === 'ready' && listings.length > 0 && (
              <Button onClick={() => window.print()} disabled={!ready}>
                <Printer size={16} strokeWidth={1.75} aria-hidden="true" />
                {ready ? 'Print listings' : 'Preparing the listings…'}
              </Button>
            )}
            <Link to="/reports/locations" className="ml-auto text-sm text-brand-ink underline">
              Search the bank branches instead
            </Link>
          </div>
        </form>
      </Sheet>

      {read.state === 'loading' && (
        <Sheet className="no-print mt-4">
          <DataState state="loading" layout="row" title="Finding the branches…" />
        </Sheet>
      )}

      {read.state === 'error' && (
        <Sheet className="no-print mt-4">
          <DataState
            state="error"
            layout="row"
            title="We could not find those branches."
            message={read.error?.message}
            error={read.error}
            onRetry={() => show(read.numbers)}
          />
        </Sheet>
      )}

      {body && listings.length === 0 && (
        <Sheet className="no-print mt-4">
          <DataState
            state="empty"
            layout="row"
            title={body.reason ? 'SASBO’s workplaces are not on record here' : 'None of these R/A numbers is on record here'}
            message={body.reason ? LIST_EMPTY_REASON[body.reason] ?? null : `Not found: ${(body.unknown ?? []).join(', ')}.`}
          />
        </Sheet>
      )}

      {body && listings.length > 0 && (
        <>
          <div className="no-print mt-4 grid gap-1 text-sm text-ink-muted" aria-live="polite" key={attempt}>
            <p>
              <b className="font-semibold tabular-nums text-ink">{formatInt(listings.length)}</b>{' '}
              listing{listings.length === 1 ? '' : 's'} in {formatInt(groups.length)} bank{groups.length === 1 ? '' : 's'}
              {' '}for {formatInt(body.asked?.length ?? listings.length)} R/A number{(body.asked?.length ?? listings.length) === 1 ? '' : 's'}.
            </p>
            {body.unknown?.length > 0 && (
              <p role="status" className="text-ink">
                Not on record here: {body.unknown.join(', ')}. The other listings are below.
              </p>
            )}
          </div>

          <div className="print-area print-batch">
            {groups.map((group) => (
              <section key={group.key} aria-label={group.bank} className="print-bank mt-6">
                <div className="print-bank-header border-b-2 border-line-strong pb-2">
                  <h2 className="text-lg font-semibold text-ink">{group.bank}</h2>
                  <p className="text-sm text-ink-muted">
                    Printed {printedOn}
                    {areaOf(user, group.listings) && ` · ${areaOf(user, group.listings)}`}
                    {' · '}{formatInt(group.listings.length)} branch{group.listings.length === 1 ? '' : 'es'}
                  </p>
                </div>
                {group.listings.map((listing) => (
                  <WorkplaceListing key={listing.workplace.id} listing={listing} members={membersOf(listing.workplace.id)} />
                ))}
              </section>
            ))}
          </div>
        </>
      )}
    </Page>
  );
}

export default BatchPrint;
