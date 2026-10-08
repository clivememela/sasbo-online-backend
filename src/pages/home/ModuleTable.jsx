import { Link } from 'react-router-dom';

import Sheet from '../../components/common/Sheet.jsx';
import Table from '../../components/table/Table.jsx';

/**
 * HOME'S TABLE VIEW (the 5 Oct Show & Tell): the modules the cards and the list show, one row each -- its name, what it
 * is for, and where it sits in the menu with what is under it there. The name is the row's control, a link in the tab
 * order, as every table here keeps its action inside the row; nothing is fetched, so the table has no state but rows.
 */
const columnsFor = (onSelect) => [
  {
    key: 'title',
    header: 'Module',
    cell: (row) => (
      <Link
        to={row.href}
        onClick={() => onSelect?.(row)}
        className="rounded-sm font-semibold text-brand-ink underline-offset-2 hover-hover:hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring"
      >
        {row.title}
      </Link>
    ),
  },
  { key: 'desc', header: 'What it is for', nowrap: false, cell: (row) => row.desc },
  {
    key: 'menu',
    header: 'In the menu',
    priority: 'secondary',
    nowrap: false,
    cell: (row) => (
      <>
        {row.places.map((place) => <span key={place} className="block">{place}</span>)}
        {row.under.length > 0 && (
          <span className="mt-0.5 block text-sm text-ink-muted">Under it: {row.under.join(', ')}</span>
        )}
      </>
    ),
  },
];

export default function ModuleTable({ modules, onSelect }) {
  return (
    <Sheet className="mt-6">
      <Table caption="The modules of SASBO Online" columns={columnsFor(onSelect)} rows={modules} rowKey="href" />
    </Sheet>
  );
}
