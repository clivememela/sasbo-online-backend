import { Building2, HandCoins, LucideInfo } from 'lucide-react';
import { NavigationList } from '../../components/common/NavigationCard';
import { Page } from '../../components/common/Sheet.jsx';
import PageTitle from '../../components/common/PageTitle';

const cards = [
  { title: 'Affiliations/Other Trade Unions', icon: Building2, href: '/archives/affiliations-other-trade-unions' },
  { title: 'Finance Houses', icon: HandCoins, href: '/archives/finance-houses' },
  { title: 'Information & Research', icon: LucideInfo, href: '/archives/research-information' },
];

export default function ArchiveSearch() {
  return (
    <Page>
      <PageTitle title="Search Archives" subtitle="Select options below" />
      <NavigationList cards={cards} />
    </Page>
  );
}
