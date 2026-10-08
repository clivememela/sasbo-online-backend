import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';

import { NavigationList } from '../components/common/NavigationCard';
import { Page } from '../components/common/Sheet.jsx';
import PageTitle from '../components/common/PageTitle';
import ViewSwitch from '../components/common/ViewSwitch.jsx';
import ModuleTable from './home/ModuleTable.jsx';
import { HOME_MODULES, readHomeView, storeHomeView } from './home/homeModules.js';

/*
 * 🔴 THE LANDING PAGE STATED A DIFFERENT INVENTORY OF THE APPLICATION FROM THE RAIL BESIDE
 * IT. Its list was hand-typed: five of the nine top-level areas, in an order of their own,
 * silently missing three that the rail two inches to the left listed. So it is not a list
 * any more -- it is the rail, read across instead of down (`homeModules.js`).
 *
 * 🗣️ AND SINCE THE 5 OCT SHOW & TELL IT IS ONLY THAT (handover §A). The month's figures --
 * paying, suspended, on record, joined, left, penetration -- moved to Month end, where SASBO
 * looked for them, and Home keeps the modules, shown as cards, a list or a table. Natalie
 * liked the cards, so they are what a browser that has chosen nothing opens on; the choice
 * is remembered per browser, and a browser that cannot remember it still shows it.
 */
// Named in words, with no icons: the three words are the whole choice, and three icons were half a kilobyte the bundle
// did not have (bundle-budget.mjs).
const VIEWS = [
  { value: 'cards', label: 'Cards' },
  { value: 'list', label: 'List' },
  { value: 'table', label: 'Table' },
];

export default function Home() {
  const { setSelectedCardHref } = useOutletContext() || {};
  const [view, setView] = useState(readHomeView);
  const choose = (next) => {
    setView(next);
    storeHomeView(next);
  };
  const onSelect = (card) => setSelectedCardHref?.(card.href);

  return (
    <Page>
      <PageTitle
        title="Welcome to SASBO Online"
        subtitle="Every module of the system. Choose one to begin."
        actions={<ViewSwitch label="Show the modules as" options={VIEWS} value={view} onChange={choose} />}
      />
      <div data-home-view={view}>
        {view === 'table' && <ModuleTable modules={HOME_MODULES} onSelect={onSelect} />}
        {view === 'list' && <NavigationList wide cards={HOME_MODULES} onSelect={onSelect} />}
        {view === 'cards' && <NavigationList tiles cards={HOME_MODULES} onSelect={onSelect} />}
      </div>
    </Page>
  );
}
