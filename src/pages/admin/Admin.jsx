import {
    LucideMenu, LucideFile, LucideConstruction, LucideCalendarRange
} from 'lucide-react';
import { NavigationList } from '../../components/common/NavigationCard';
import { Page } from '../../components/common/Sheet.jsx';
import PageTitle from '../../components/common/PageTitle';

const cards = [
    { title: 'Daily', desc: 'Daily Menu' ,icon: LucideMenu, href: '/admin/daily-menu' },
    // Month end `04.1`: the Monthly Menu renamed, at its own address (MD38), between Daily and Debit Order Deductions.
    { title: 'Month end', desc: 'The month’s figures, its statistics and its pack of workbooks', icon: LucideCalendarRange, href: '/admin/monthly-menu' },
    // Once "A.C.B." -- SASBO's name for it since the 5 Oct Show & Tell; why, beside its rail entry in `navigation.js`.
    { title: 'Debit Order Deductions', desc: 'The monthly debit order run, re-debits, unpaids and letters', icon: LucideFile, href: '/admin/acb' },
    // { title: 'BankBranch', icon: Landmark, href:'/banks' },
    //{ title: 'Utilities', icon: LucideToolbox, color: 'text-red-600 dark:text-red-400' },
    //{ title: 'Regional Branches', icon: LucideGitBranch, color: 'text-red-600 dark:text-red-400' },
    //{ title: 'Tape Menu', icon: LucideCassetteTape, color: 'text-red-600 dark:text-red-400' },
    // { title: 'D.B.F Menu', icon: LucideFile,color: 'text-red-600 dark:text-red-400' },
    //{ title: 'Selective Print Menu', icon: TextSelect, color: 'text-red-600 dark:text-red-400'},
    //{ title: 'Committee Menu', icon: LucideChartNoAxesGantt , color: 'text-red-600 dark:text-red-400'},
    //{ title: 'Ad-Hoc Label', icon: LucideTag, color: 'text-red-600 dark:text-red-400'},
    //{ title: 'Bank Transfer', icon: ArrowRightLeft, href: '/admin/bank-transfer' },
    //{ title: 'Letters for Return Debits', icon: Mailbox, color: 'text-red-600 dark:text-red-400'},
    { title: "Maintenance", desc: 'Maintenance Functions',href:'/admin/maintenance', icon: LucideConstruction},
    //{ title: 'Exit System', icon: LucideExternalLink , color: 'text-red-600 dark:text-red-400'}
];

export default function Admin() {
    return (
        <Page>
            <PageTitle title="Admin Membership" subtitle="Select options below" />
            <NavigationList cards={cards} />
        </Page>
    );
}
