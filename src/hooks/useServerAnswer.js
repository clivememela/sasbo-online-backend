import { useCallback, useEffect, useRef, useState } from 'react';

import { ERROR_KINDS } from '../services/parseApiError.js';

const NO_ANSWER = { request: -1, data: null, error: null };

const sameInputs = (a, b) => a.length === b.length && a.every((value, index) => Object.is(value, b[index]));

/**
 * One server answer that follows its inputs -- a page of a list, a picker's rows -- kept WHOLE, where
 * `useAsyncOptions` keeps an array: a page's total and a picker's "more" are part of what the screen says.
 *
 * The mechanism is `useAsyncOptions`'s, deliberately (its file carries the reasons): a request is counted when the
 * inputs change and answered once, `loading` is derived from whether the current request has its answer, a superseded
 * request is aborted and its answer dropped, an abort is never an error, and nothing sets state inside the effect.
 * Disabled -- nothing to ask, or the user may not ask it -- empties the answer, so re-enabling never flashes an old one.
 *
 * @returns {{ data: unknown, loading: boolean, error: Error|null, refetch: () => void }}
 */
export default function useServerAnswer(fetcher, deps = [], { enabled = true } = {}) {
  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const inputs = [enabled, ...deps];
  const [seenInputs, setSeenInputs] = useState(inputs);
  const [request, setRequest] = useState(0);
  const [answer, setAnswer] = useState(NO_ANSWER);
  if (!sameInputs(inputs, seenInputs)) {
    setSeenInputs(inputs);
    setRequest((n) => n + 1);
    if (!enabled) setAnswer(NO_ANSWER);
  }

  useEffect(() => {
    if (!enabled) return undefined;
    let superseded = false;
    const controller = typeof AbortController === 'undefined' ? null : new AbortController();
    new Promise((resolve) => resolve(fetcherRef.current(controller?.signal)))
      .then((data) => {
        if (!superseded) setAnswer({ request, data, error: null });
      })
      .catch((error) => {
        if (superseded || error?.kind === ERROR_KINDS.ABORT) return;
        setAnswer({ request, data: null, error });
      });
    return () => {
      superseded = true;
      controller?.abort();
    };
  }, [enabled, request]);

  const refetch = useCallback(() => setRequest((n) => n + 1), []);
  const current = answer.request === request;
  return {
    data: enabled ? answer.data : null,
    loading: enabled && !current,
    error: enabled && current ? answer.error : null,
    refetch,
  };
}
