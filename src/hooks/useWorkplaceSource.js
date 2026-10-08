import { useState } from 'react';

import { getEmployers, getStatus } from '../services/referenceService';
import { answerFailed, failedRead, workplaceSource } from '../utils/workplaces';
import useAsyncOptions from './useAsyncOptions';

/**
 * WHAT THE WORKPLACE CONTROL CAN OFFER -- SASBO's employers, and whether any workplace is on record here (`01.3`).
 *
 * 🔑 ASKED FOR WHEN THE CONTROL IS FIRST OPENED, AND KEPT FOR THE VISIT (MD69). The PAGE calls this, with `open` true
 * while its Employment tab shows the control, so the answer outlives the tab: leaving it and coming back asks nothing
 * again. Nothing is asked at first paint -- capture opens on Personal Information.
 *
 * Two reads, settled together, because the control needs both: the status read says whether a workplace is on record
 * HERE (every hosted stack answers NOT_LOADED until SASBO agrees, MD45) and where the workplaces were read from; the
 * employers are the first thing chosen. Asking for the status first means the control says *not available* when it
 * opens, rather than after someone has chosen an employer and typed.
 *
 * FOUR STATES, and none of them is an empty control (`useAsyncOptions` sequences the read and its retry):
 *   `loading`   -- the answer has not arrived;
 *   ready       -- `employers`, `employersFrom`, `workplacesFrom`;
 *   `why`       -- not available: the server's reason, a 404 from a server built before these routes, or an answer
 *                  of the wrong shape;
 *   `failure`   -- a 5xx, a timeout, no connection: said as a failure, with `retry`, never as "none". A failure
 *                  decides over a 404, so a server that is down is never said to be merely "not here yet".
 *
 * @param {boolean} open whether the control is on screen
 */
export default function useWorkplaceSource(open) {
  // Latched during render (React's documented adjustment), never in an effect: once opened, asked for.
  const [asked, setAsked] = useState(false);
  if (open && !asked) setAsked(true);
  const read = useAsyncOptions(
    () => Promise.allSettled([getStatus(), getEmployers()])
      .then(([status, employers]) => {
        const refused = [status.reason, employers.reason].filter(Boolean);
        const failure = refused.find((reason) => !failedRead(reason).why);
        if (failure) return answerFailed('workplaces.source')(failure);
        return [refused.length ? failedRead(refused[0]) : workplaceSource(status.value, employers.value)];
      }),
    [],
    { enabled: asked },
  );
  return {
    ...(read.loading ? null : read.error ? { failure: read.error } : read.options[0]),
    loading: read.loading,
    retry: read.refetch,
  };
}
