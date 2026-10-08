import { useCallback, useEffect, useRef, useState } from 'react';
import Pager from '../../components/common/Pager.jsx';
import reportError from '../../lib/reportError.js';
import { asPage } from './acbRunRules.js';

/**
 * A server-paged sheet: loading on the way in, and back to loading on every page and retry.
 *
 * Shared by the Runs panel's sheets and the Debits screen's (`04.4`: it was the panel's own until the
 * Debits screen needed the same three states and the same ticket). `read(page, size)` must be stable --
 * a `useCallback` -- or every render reads again.
 */
export function usePagedRead(read, { size = 50, label = 'acb.read' } = {}) {
  const [page, setPage] = useState(1);
  const [state, setState] = useState('loading');
  const [data, setData] = useState(() => asPage(null));
  const left = useRef(false);
  useEffect(() => () => { left.current = true; }, []);

  // Only the newest read may paint: a page's answer arriving after the next page's must not
  // put its rows under the next page's number (R3-F4).
  const ticket = useRef(0);
  const load = useCallback((pageNumber) => {
    const mine = ++ticket.current;
    const current = () => !left.current && mine === ticket.current;
    return read(pageNumber - 1, size)
      .then((result) => {
        if (!current()) return;
        setData(asPage(result));
        setState('ready');
      })
      .catch((err) => {
        reportError(label, err);
        if (current()) setState('error');
      });
  }, [read, size, label]);

  useEffect(() => {
    load(page);
  }, [load, page]);

  const goTo = (pageNumber) => {
    setState('loading');
    if (pageNumber === page) load(pageNumber);
    else setPage(pageNumber);
  };
  return { data, state, goTo, retry: () => goTo(page) };
}

/** The foot of a server-paged sheet: what it shows, of how many, and the way to the next page. */
export function PagerFor({ data, state, goTo, noun, size = 50 }) {
  const start = data.page * size + 1;
  return (
    <Pager
      page={data.page + 1}
      totalPages={Math.max(data.totalPages, 1)}
      onPageChange={goTo}
      count={data.totalElements}
      start={start}
      end={data.page * size + data.content.length}
      noun={noun}
      loading={state === 'loading'}
      error={state === 'error'}
    />
  );
}
