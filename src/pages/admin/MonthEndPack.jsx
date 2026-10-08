import { useCallback, useEffect, useRef, useState } from 'react';
import { Download, Loader2 } from 'lucide-react';

import DialogNotice from '../../components/common/DialogNotice.jsx';
import Sheet from '../../components/common/Sheet.jsx';
import Button from '../../components/form/Button.jsx';
import { monthLabel } from '../../config/monthEnd.js';
import reportError from '../../lib/reportError.js';
import { askPack, getPack, packWorkbookUrl } from '../../services/monthEndService.js';
import { messageOf, writeMayHaveLanded } from '../../services/parseApiError.js';
import { POLL_DELAYS_MS, pollRun } from '../acb/acbRunRules.js';
import { MONTH_END_P4_REFUSAL, PACK, PACK_STATUS, instantLabel, workbookLabel } from './monthEndRules.js';

/**
 * THE STATISTICS PACK -- month end `04.10`: the two workbooks the Secretariat and the Management Committee are sent,
 * produced by the server as a JOB and saved from here as `.xlsx`.
 *
 * 🔴 ONE POST, THEN POLL -- AND NEVER A SECOND POST WHILE ONE IS BEING PREPARED. `POST /{month}/pack` answers at once:
 * 202 with a new pack `PREPARING`, or 200 with the one in hand (a taken month's READY pack is that month's for good).
 * The pack is then re-read on A.C.B.'s delays (`pollRun`, `POLL_DELAYS_MS`) until it is `READY` or `FAILED`, and the
 * reading stops the moment the screen is left. While a pack is being prepared the control is shut: the server has no
 * queue, and another month's ask is `PACK_BUSY`. *Check on it* re-reads; it never asks again.
 *
 * What each answer says, in the server's words where it has some (ME-13):
 *   READY            one link a workbook -- the browser saves the attachment;
 *   FAILED           the pack's own `failure`, and the control open to ask again;
 *   PACK_BUSY        another month's pack is in hand: said, and the control stays open to try later;
 *   PACK_NOT_FOUND   the pack went while it was polled: said;
 *   MONTH_NOT_TAKEN  a month the server will not make a pack of: said, and the control shut for that month;
 *   no answer        a timeout or a lost connection MAY have started one: said as unconfirmed -- asking again is safe,
 *                    because a pack in hand for the month is answered, never started twice.
 *
 * 🧭 THE OPEN MONTH HAS A PACK. The server makes one of the month as it stands, afresh each time it is asked for
 * (`MonthEndPacks.ask`), and a past month that was never taken is the one it refuses -- so the open month's control is
 * open, and says what its pack is.
 *
 * `pollDelaysMs` is a test seam, as `AcbRuns` has; the route renders with the real backoff.
 */

/** A pack that failed, in one sentence: its month, and the server's own reason. */
const failedSentence = (pack) =>
  `The pack for ${monthLabel(pack.month)} could not be prepared: ${pack.failure ?? 'no reason was given'}`
  + `${/[.!?]$/.test(pack.failure ?? '') ? '' : '.'} Ask for it again.`;

export default function MonthEndPack({ month, open = false, pollDelaysMs = POLL_DELAYS_MS }) {
  // The pack in hand -- the one this visit asked for -- and what the screen is waiting on.
  const [pack, setPack] = useState(null);
  const [asking, setAsking] = useState(false);
  const [watching, setWatching] = useState(false);
  const [notice, setNotice] = useState(null);
  // A month the server refused a pack of, and its sentence: that month's control is shut, with its reason.
  const [refused, setRefused] = useState({});

  // A POST in flight, known at once: two presses inside one tick both see the render before either, so the state
  // alone cannot keep the second from asking.
  const inFlight = useRef(false);
  // The screen was left: every poll and every late answer stops touching its state. Set on the way in as well as
  // out, so React's development remount (StrictMode) does not leave the screen believing it was left.
  const left = useRef(false);
  useEffect(() => {
    left.current = false;
    return () => {
      left.current = true;
    };
  }, []);

  const preparing = pack?.status === PACK_STATUS.PREPARING;
  const busy = asking || watching || preparing;

  const watch = useCallback((inHand) => {
    setWatching(true);
    pollRun(getPack, inHand.id, { delays: pollDelaysMs, stopped: () => left.current })
      .then((settled) => {
        if (settled === null || left.current) return;
        if (settled === undefined) {
          setNotice({ check: true, message: `The pack for ${monthLabel(inHand.month)} is still being prepared. `
            + 'Check on it again in a few minutes.' });
          return;
        }
        setPack(settled);
        if (settled.status === PACK_STATUS.FAILED) setNotice({ message: failedSentence(settled) });
      })
      .catch((error) => {
        reportError('MonthEndPack.watch', error);
        if (left.current) return;
        if (error?.body?.code === MONTH_END_P4_REFUSAL.PACK_NOT_FOUND) {
          setPack(null);
          setNotice({ message: error.message });
        } else {
          setNotice({ check: true, message: `The pack for ${monthLabel(inHand.month)} is being prepared, but how far it `
            + 'has got could not be read.' });
        }
      })
      .finally(() => {
        if (!left.current) setWatching(false);
      });
  }, [pollDelaysMs]);

  const ask = async () => {
    // The control is shut while a pack is being prepared; this is the rule, not only the button's state.
    if (busy || inFlight.current || !month) return;
    inFlight.current = true;
    setAsking(true);
    setNotice(null);
    try {
      const answer = await askPack(month);
      if (left.current) return;
      setPack(answer);
      if (answer?.status === PACK_STATUS.PREPARING) watch(answer);
      else if (answer?.status === PACK_STATUS.FAILED) setNotice({ message: failedSentence(answer) });
    } catch (error) {
      reportError('MonthEndPack.ask', error);
      if (left.current) return;
      const code = error?.body?.code;
      if (code === MONTH_END_P4_REFUSAL.MONTH_NOT_TAKEN) {
        setRefused((was) => ({ ...was, [month]: error.message }));
      } else if (code === MONTH_END_P4_REFUSAL.PACK_BUSY) {
        const inHand = error.body.details?.inHand;
        setNotice({ message: `${error.message}${inHand ? ` The pack in hand is ${monthLabel(inHand)}’s.` : ''}` });
      } else if (writeMayHaveLanded(error)) {
        setNotice({ message: `The pack for ${monthLabel(month)} could not be confirmed. Asking again is safe — a pack `
          + 'in hand for the month is answered, never started twice.' });
      } else {
        setNotice({ message: `The pack for ${monthLabel(month)} was not prepared. ${messageOf(error)}` });
      }
    } finally {
      inFlight.current = false;
      if (!left.current) setAsking(false);
    }
  };

  const refusedHere = refused[month];

  return (
    <section aria-labelledby="month-end-pack-heading" className="mt-8" data-pack={pack?.status ?? 'none'}>
      <h2 id="month-end-pack-heading" className="text-lg font-semibold text-ink">{PACK.name}</h2>
      <p className="mt-0.5 max-w-prose text-sm text-ink-muted">{PACK.legacy}</p>
      <Sheet className="mt-3 max-w-4xl gap-3 px-5 py-4">
        <p className="max-w-prose text-base text-ink">
          Both workbooks, made from the month’s statistics: counts only, laid out as SASBO’s are.
          {open && ' The open month’s pack is worked out as the month stands now, and afresh each time it is asked for.'}
        </p>

        <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />

        <div className="flex flex-wrap items-center gap-3">
          <Button
            variant="primary"
            onClick={ask}
            loading={asking}
            disabled={busy || !month || Boolean(refusedHere)}
            aria-describedby={refusedHere ? 'month-end-pack-refused' : undefined}
          >
            {month ? `Prepare the pack for ${monthLabel(month)}` : 'Prepare the pack'}
          </Button>
          {notice?.check && pack && (
            <Button onClick={() => { setNotice(null); watch(pack); }} disabled={watching}>Check on it</Button>
          )}
        </div>
        {refusedHere && <p id="month-end-pack-refused" className="text-sm text-ink-muted">{refusedHere}</p>}

        {preparing && (
          <p role="status" className="flex items-center gap-2 text-sm text-ink-muted">
            <Loader2 aria-hidden="true" className="size-4 shrink-0 animate-spin motion-reduce:animate-none" />
            Preparing the pack for {monthLabel(pack.month)}. This page checks on it by itself.
          </p>
        )}

        {pack?.status === PACK_STATUS.READY && (
          <div className="grid gap-2">
            <p role="status" className="text-sm text-ink">
              The pack for {monthLabel(pack.month)} is ready
              {pack.finishedAt ? ` — prepared on ${instantLabel(pack.finishedAt)}` : ''}.
            </p>
            <ul className="flex flex-wrap gap-2">
              {(pack.workbooks ?? []).map((workbook) => (
                <li key={workbook}>
                  <Button href={packWorkbookUrl(pack.id, workbook)} download>
                    <Download aria-hidden="true" className="size-4 shrink-0" />
                    Download {workbookLabel(workbook)} (.xlsx)
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </Sheet>
    </section>
  );
}
