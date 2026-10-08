import { useEffect, useState } from 'react';

import reportError from '../../lib/reportError.js';
import { getMonths } from '../../services/monthEndService.js';
import { failureOf, monthsOf } from './monthEndRules.js';

/** An answer that is not the months' -- the wrong shape, or a server built before the route (MV-36, MR-ROB-31). */
const NOT_AVAILABLE = 'The months are not available from this server yet.';

/**
 * THE SERVER'S MONTHS (MD55) -- the open month and every month taken, as the Month end page and a statistic's screen
 * both choose from them. Read once, and again only when a person asks (`retry`): nothing here asks by itself.
 *
 * `months` is `null` while they are coming (`pending`) and where they could not be read; `failure` then says why -- a
 * refusal or a lost connection as it came, an answer of the wrong shape as *not available*, never as no months.
 *
 * @param {string} context what the failure is reported as (`reportError`)
 */
export default function useMonths(context) {
  const [attempt, setAttempt] = useState(0);
  const [read, setRead] = useState({ attempt: -1 });
  useEffect(() => {
    let current = true;
    getMonths()
      .then((body) => {
        const months = monthsOf(body);
        if (current) setRead({ attempt, months, failure: months ? null : failureOf(null, NOT_AVAILABLE) });
      })
      .catch((error) => {
        reportError(context, error);
        if (current) setRead({ attempt, months: null, failure: failureOf(error, NOT_AVAILABLE) });
      });
    return () => {
      current = false;
    };
  }, [attempt, context]);

  const pending = read.attempt !== attempt;
  return {
    pending,
    months: pending ? null : read.months,
    failure: pending ? null : read.failure,
    retry: () => setAttempt((n) => n + 1),
  };
}
