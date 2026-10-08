import { useEffect, useState } from 'react';

import useAsyncOptions from '../../hooks/useAsyncOptions';
import { workplaceService } from '../../services/workplaceService';
import { Button, SearchableSelect, TextInput } from '../form';
import LoadFailureFrame from '../form/LoadFailureFrame';
import { FILLABLE, WORKPLACE_SEARCH, answerFailed, workplaceResults } from '../../utils/workplaces';
import { committeeLine } from '../../config/monthEnd';
import { EMPTY } from '../../lib/format';

/** card.css `.field__label`: the small capitals every label on these screens wears. */
const LABEL = 'text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted';
const SAID = 'text-sm text-ink-muted';
/** The mock's ⑦: the words that say the control has nothing to offer, in the record's ink, before the reason. */
const NOT_AVAILABLE = <strong className="font-semibold text-ink">Not available.</strong>;

/**
 * THE MEMBER'S WORKPLACE -- month end `01.3`, on capture's and the record's Employment tab.
 *
 * The employer is chosen from SASBO's list, then the workplace is found BY THE SERVER, by its name or its R/A number
 * (MR-PERF-12): nothing is asked below the second character, one question goes after a pause in typing, and an answer
 * that arrives after a newer question is DROPPED -- `useAsyncOptions` asks, and a question the typing has moved past
 * is superseded, its answer thrown away however late it lands. It is never a list of 5,507.
 *
 * Choosing one shows its R/A number, area and committee -- read-only, with where they came from (MD28) -- and hands the
 * row to the page's `useWorkplaceChoice`, whose form holds only its id: a save names a workplace by `workplaceId` and
 * nothing else (BR66).
 *
 * THE OFFER is the page's to compute (`workplaceFill`, `utils/workplaces.js`) and this control's to ask: *Fill "Bank
 * Where Employed" and "Bank Branch" from this workplace?* Never done silently; declining leaves both as they are.
 *
 * FOUR STATES, from `source` (`useWorkplaceSource`): loading -- the employer field says so; ready; not available --
 * with the sentence that says why, never an empty control; failed -- with Try again, never "none". A member with no
 * workplace is a value, said as one: *No workplace on record*.
 *
 * @param {object}  [source]    `useWorkplaceSource`'s answer. Without one the control only SHOWS (the record, read).
 * @param {object}  choice      `useWorkplaceChoice`'s answer: the chosen row, the offer, and what to do with either
 * @param {object}  [current]   the member's workplace as the record reads it: `{ workplace }` · `{ none }` ·
 *                              `{ loading }` · `{ why }` · `{ failure, retry }`, and `from`, its list's provenance.
 *                              Capture shows the row it chose.
 * @param {boolean} [removable] capture only, where no workplace is a member's to have yet
 * @param {string}  [error]     the save's refusal of the workplace (WORKPLACE_UNKNOWN), in the server's words
 * @param {boolean} [stacked]   the record's panel: the employer over the search, in the panel's one column (mock ⑫)
 */
export default function WorkplaceField({
  source, choice, current = choice.workplace && { workplace: choice.workplace }, removable, error, stacked,
  className = '',
}) {
  const [employer, setEmployer] = useState('');
  const [term, setTerm] = useState('');
  const [settled, setSettled] = useState(null);

  const search = term.trim();
  const question = employer && search.length >= WORKPLACE_SEARCH.from ? JSON.stringify([employer, search]) : null;

  // The pause: a question is only asked once typing has stopped on it.
  useEffect(() => {
    if (!question) return undefined;
    const timer = setTimeout(() => setSettled(question), WORKPLACE_SEARCH.pauseMs);
    return () => clearTimeout(timer);
  }, [question]);

  // What is being asked NOW -- nothing while the typing has moved past the last question that settled.
  const asked = question && settled === question ? question : null;
  const found = useAsyncOptions(() => {
    const [wanted, text] = JSON.parse(asked);
    return workplaceService.getWorkplaces({ employer: wanted, search: text, page: 0, size: WORKPLACE_SEARCH.size })
      .then((page) => [workplaceResults(page)], answerFailed('workplaces.search'));
  }, [asked], { enabled: Boolean(asked) });
  const answer = asked && !found.loading ? found.options[0] : null;
  const rows = answer?.page?.content ?? [];
  const total = answer?.page?.totalElements;
  const said = !asked || answer?.failure ? ''
    : !answer ? 'Searching…'
      : answer.why ? `Not available. ${answer.why}`
        : total ? `${total} found${total > rows.length ? ` — the first ${rows.length} are shown. Type more to narrow them` : ''}.`
          : `No workplace matches “${search}”.`;

  const workplace = current?.workplace;
  const offered = source ? Object.keys(choice.fill) : [];

  return (
    <fieldset className={`grid min-w-0 gap-y-3.5 ${className}`}>
      <legend className={LABEL}>Workplace</legend>

      {workplace ? (
        /* card.html's `.panel__meta` -- one ruled line of what the workplace is, as the result rows read, and where it
           came from beneath it. Read-only: nothing here can be typed into. */
        <div className="grid gap-1.5 border-y border-line py-3">
          <div className="flex items-center justify-between gap-3">
            <p className="text-base font-medium">{workplace.name}</p>
            {removable && (
              <Button variant="ghost" size="sm" onClick={() => choice.choose(null)} aria-label={`Remove ${workplace.name}`}>
                Remove
              </Button>
            )}
          </div>
          {/* 🔴 NOT IN TABULAR FIGURES (MO24's "Q01 ," -- the stand-in review). This face gives its comma a figure's
              width under `tabular-nums`, so "Q01, INVENTED" read "Q01 , INVENTED": one line of facts stacks against
              nothing, so its figures are the face's own (03.6's rule). Each part trimmed as well, so a padded value
              cannot put a space before the comma either. */}
          <p className="text-sm text-ink-muted">
            {`R/A ${workplace.raNumber} · Area ${workplace.area || EMPTY} · Committee ${committeeLine(workplace)}`}
          </p>
          <p className="text-xs text-ink-muted">From SASBO’s list of workplaces. {source?.workplacesFrom ?? current.from}</p>
        </div>
      ) : current?.failure ? (
        <LoadFailureFrame failed label="The member’s workplace" loadError={current.failure} onRetryLoad={current.retry} />
      ) : current && (
        <p className={current.none ? 'font-medium' : SAID} role={current.none ? undefined : 'status'}>
          {current.none ? 'No workplace on record' : current.why ? <>{NOT_AVAILABLE} {current.why}</> : 'Loading…'}
        </p>
      )}

      {error && <p role="alert" className="text-xs text-status-suspended">{error}</p>}

      {offered.length > 0 && (
        <div className="rounded-lg border border-line-strong bg-surface-2 p-3 text-sm text-ink-muted">
          <p className="font-medium text-ink">Fill {offered.map((key) => `"${FILLABLE[key]}"`).join(' and ')} from this workplace?</p>
          <p>{offered.map((key) => choice.fill[key]).join(' · ')}</p>
          <div className="mt-2 flex gap-2">
            <Button variant="primary" size="sm" onClick={choice.accept}>Fill</Button>
            <Button size="sm" onClick={choice.decline}>No thanks</Button>
          </div>
        </div>
      )}

      {source && (source.failure ? (
        <LoadFailureFrame failed label="SASBO’s workplaces" loadError={source.failure} onRetryLoad={source.retry} />
      ) : source.why ? (
        <p className={SAID} role="status">{NOT_AVAILABLE} {source.why}</p>
      ) : (
        <>
          <div className={`grid gap-x-4 gap-y-3.5 ${stacked ? '' : 'grid-cols-2 max-sm:grid-cols-1'}`}>
            <SearchableSelect
              label="Employer"
              options={source.employers}
              optionValue="id"
              optionLabel="name"
              value={employer}
              onChange={setEmployer}
              loading={source.loading}
              hint={source.employersFrom}
            />
            <TextInput
              label="Find the workplace"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              disabled={!employer}
              maxLength={WORKPLACE_SEARCH.maxLength}
              hint={employer ? 'By its name or R/A number.' : 'Choose the employer first.'}
            />
          </div>
          {rows.length > 0 && (
            <ul className="grid gap-0.5 rounded-lg border border-line p-2">
              {rows.map((row) => (
                <li key={row.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setTerm('');
                      choice.choose(row);
                    }}
                    className="touch-size flex w-full flex-wrap items-center justify-between gap-x-3 rounded-md px-3 py-2 text-left hover-hover:hover:bg-brand-soft focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring"
                  >
                    <span className="font-medium">{row.name}</span>
                    <span className="text-xs text-ink-muted tabular-nums">R/A {row.raNumber} · Area {row.area || EMPTY}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          {answer?.failure && (
            <LoadFailureFrame
              failed
              label={`The workplaces for “${search}”`}
              loadError={answer.failure}
              onRetryLoad={found.refetch}
            />
          )}
          <p className="text-xs text-ink-muted" role="status">{said}</p>
        </>
      ))}
    </fieldset>
  );
}
