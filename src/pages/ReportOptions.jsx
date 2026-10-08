import { NotebookPen, Form, MapPinnedIcon, Printer } from 'lucide-react';
import { NavigationList } from '../components/common/NavigationCard';
import { Page } from '../components/common/Sheet.jsx';
import PageTitle from '../components/common/PageTitle';

const cards = [
  { title: 'Location Visit', icon: NotebookPen, href: '/reports/organizer-report', desc: 'Record a visit to a branch or department' },
  { title: 'Forms', icon: Form, href: '/reports/forms', desc: 'Complete Branch Form (Online/Offline), Membership/Non-Membership' },
  { title: 'Location Listing', icon: MapPinnedIcon, href: '/reports/locations', desc: 'Location Listings/Bulk Location Listings' },
  // October build (5 Oct Show & Tell): "put all 20 numbers in at once, click print, and then it prints everything".
  { title: 'Batch print', icon: Printer, href: '/reports/locations/print', desc: 'Print the location listings of many branches at once, each bank on a new page' },
];

export default function ReportOptions() {
  return (
    <Page>
      <PageTitle title="Reports" subtitle="Select options below" />
      <NavigationList cards={cards} />
    </Page>
  );
}
