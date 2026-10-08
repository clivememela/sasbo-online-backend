import { useId } from 'react';
import { Link, useLocation } from 'react-router-dom';

import DataState from '../../components/common/DataState.jsx';
import TableScroller from '../../components/table/TableScroller.jsx';
import { useAuth } from '../../auth/useAuth';
import { PERMISSIONS } from '../../auth/permissions';
import { committeeLine } from '../../config/monthEnd.js';
import { EMPTY, MONTHS, formatInt, parseDate } from '../../lib/format.js';

/**
 * ONE BRANCH'S LOCATION LISTING, from SASBO's workplaces (October build, 5 Oct Show & Tell): the branch, the members
 * at it, and its complement and last visits -- what an organiser prints before a visit. It is `LocationListing`'s
 * layout for a workplace found by its R/A number, which knows no IBT number, cost code, telephone or address -- the
 * workplaces carry none -- and does know its area, region and committee.
 *
 * 🔒 THE MEMBERS NEED `MEMBERS_VIEW` ("listings that show members", held until sign-in), and are read for the whole batch
 * at once by the page (`BatchPrint`): a user who may not see them is told so in one sentence, under the branch, and the
 * rest of the listing still prints.
 */

/** `2024-03-01` as a listing prints it: `Mar 2024`. */
const monthOf = (iso) => {
  const date = parseDate(iso);
  return date ? `${MONTHS[date.m - 1]} ${date.y}` : EMPTY;
};

/** A member's name as the legacy listing printed it: surname, initials, title. */
const nameOf = (member) => [member.surname, member.initials, member.title].filter(Boolean).join(', ') || EMPTY;

const flag = (on) => (on ? 'Y' : '');

/** A subscription, two decimals; nothing where none is on record. */
const amountOf = (value) => (value == null || value === '' ? '' : Number(value).toFixed(2));

const NOT_ANSWERED = Object.assign(new Error('The members at this branch could not be read from this server.'),
  { retryable: false });

/**
 * The members at one branch, from the batch print's ONE read of every branch's members (October build review: one
 * read for the batch, not one a branch), or the sentence that says why they are not shown. `members` is that read as
 * this branch sees it: `{ state: 'loading' }`; `{ state: 'ready', branch }` -- the server's `{ workplaceId, outOfScope,
 * members, totalElements }`, or none where the answer left the branch out; or `{ state: 'error', error, onRetry }`.
 */
export function BranchMembers({ workplace, members }) {
  const { status: authStatus, can } = useAuth();
  const location = useLocation();
  const allowed = can(PERMISSIONS.MEMBERS_VIEW);

  if (!allowed) {
    const next = encodeURIComponent(`${location.pathname}${location.search}`);
    return (
      <p role="status" className="my-4 rounded-md border border-line bg-surface-2 px-4 py-3 text-sm text-ink">
        {authStatus === 'signed-in' ? (
          'You need permission to see the members at this branch. Ask your administrator if you should have it.'
        ) : (
          <>
            Sign in to see the members at this branch.{' '}
            <Link className="no-print font-medium text-brand-ink underline" to={`/login?next=${next}`}>Sign in</Link>
          </>
        )}
      </p>
    );
  }

  if (!members || members.state === 'loading') {
    return <DataState state="loading" layout="row" compact className="my-4 p-0" title="Loading the members at this branch…" />;
  }
  const branch = members.state === 'ready' ? members.branch : null;
  if (!branch || branch.outOfScope) {
    const outside = Boolean(branch?.outOfScope);
    const failure = members.error ?? NOT_ANSWERED;
    return (
      <DataState
        state="error"
        layout="row"
        compact
        className="my-4 p-0"
        title={outside ? 'This branch is outside your region or area.' : 'The members at this branch could not be loaded.'}
        message={outside ? null : failure.message}
        error={outside ? { retryable: false } : failure}
        onRetry={outside ? undefined : members.onRetry}
      />
    );
  }
  if (branch.members.length === 0) {
    return <p className="my-4 text-sm text-ink-muted">No members are on record at this branch.</p>;
  }
  return (
    <>
      <TableScroller label={`Members at ${workplace.name}`} className="my-4">
        <table className="w-full border-collapse text-sm">
          <caption className="sr-only">Members at {workplace.name}</caption>
          <thead>
            <tr className="border-b border-line-strong bg-surface-2 text-left text-xs uppercase tracking-wide text-ink-muted">
              <th scope="col" className="whitespace-nowrap px-2 py-2">Mem No</th>
              <th scope="col" className="px-2 py-2">Name of Member</th>
              <th scope="col" className="px-2 py-2 text-right">Subscr</th>
              <th scope="col" className="px-2 py-2">Emp Num</th>
              <th scope="col" className="px-2 py-2 text-center">Email</th>
              <th scope="col" className="px-2 py-2 text-center">ID</th>
              <th scope="col" className="px-2 py-2 text-center">Cell</th>
              <th scope="col" className="whitespace-nowrap px-2 py-2">Joined</th>
              <th scope="col" className="px-2 py-2 text-center">Susp</th>
            </tr>
          </thead>
          <tbody>
            {branch.members.map((member) => (
              <tr key={member.id} className="border-b border-line text-ink">
                <td className="whitespace-nowrap px-2 py-1.5 tabular-nums">{member.membershipNumber}</td>
                <td className="whitespace-nowrap px-2 py-1.5 font-semibold">{nameOf(member)}</td>
                <td className="px-2 py-1.5 text-right tabular-nums">{amountOf(member.monthlySubscription)}</td>
                <td className="px-2 py-1.5 tabular-nums">{member.employeeNumber ?? ''}</td>
                <td className="px-2 py-1.5 text-center">{flag(member.email)}</td>
                <td className="px-2 py-1.5 text-center">{flag(member.identity)}</td>
                <td className="px-2 py-1.5 text-center">{flag(member.cell)}</td>
                <td className="whitespace-nowrap px-2 py-1.5">{monthOf(member.joinedOn)}</td>
                <td className="px-2 py-1.5 text-center">{flag(member.suspended)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </TableScroller>
      {branch.totalElements > branch.members.length && (
        <p className="text-sm text-ink-muted">
          The first {formatInt(branch.members.length)} of {formatInt(branch.totalElements)} members are listed.
        </p>
      )}
    </>
  );
}

/** A visit's month, as the branch list prints its ten: `Aug 2026`. */
const lastVisitedOf = (visits) => (visits?.length ? visits.map((visit) => monthOf(visit.visitedOn)).join(', ') : EMPTY);

/**
 * One branch's listing. `members` is the batch's one read of the members as this branch sees it ({@link BranchMembers});
 * the Membership line is the branch's whole count from it, or nothing where the members are not shown.
 */
export default function WorkplaceListing({ listing, members }) {
  const headingId = useId();
  const w = listing.workplace;
  const branch = members?.state === 'ready' ? members.branch : null;
  const membership = branch && !branch.outOfScope ? branch.totalElements : null;

  return (
    <article aria-labelledby={headingId} className="print-branch mt-4 rounded-lg bg-surface p-6 shadow-(--shadow-1)">
      <div className="print-branch-head mb-4 border-b border-line-strong pb-3">
        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">SASBO Finance Union — Location Listing</p>
        <h3 id={headingId} className="mt-1 text-base font-semibold text-ink">
          {w.name} <span className="font-normal text-ink-muted">· R/A {w.raNumber}</span>
        </h3>
      </div>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-1.5 rounded-lg border border-line bg-surface-2 p-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
        {[
          ['Bank', w.employer ?? w.employerPrinted],
          ['R/A No', w.raNumber],
          ['Branch', w.name],
          ['Area', w.area],
          ['Region', w.region],
          ['Committee', committeeLine(w)],
        ].map(([label, value]) => (
          <div key={label} className="flex gap-1.5 text-ink">
            <dt className="shrink-0 font-semibold">{label}:</dt>
            <dd>{value ?? EMPTY}</dd>
          </div>
        ))}
      </dl>
      <BranchMembers workplace={w} members={members} />
      <dl className="space-y-1 border-t border-line pt-4 text-sm">
        {[
          ['Complement', w.staffComplement == null ? EMPTY : formatInt(w.staffComplement)],
          ['Membership', membership == null ? EMPTY : formatInt(membership)],
          ['Last Visited', lastVisitedOf(listing.lastVisited)],
        ].map(([label, value]) => (
          <div key={label} className="flex gap-2 text-ink">
            <dt className="min-w-[160px] shrink-0 font-semibold">{label}:</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </article>
  );
}
