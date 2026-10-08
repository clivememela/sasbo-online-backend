import {
    LucideCalendarRange,
    LucideHandCoins,
    LucideMails,
    LucidePiggyBank
} from 'lucide-react';
import PageTitle from "../../components/common/PageTitle.jsx";
import { NavigationList } from "../../components/common/NavigationCard.jsx";
import { Page } from '../../components/common/Sheet.jsx';

/**
 * The A.C.B. menu (`04.2`): Runs, Debits, Letters and Credits.
 *
 * Rows without a route stay HONEST -- `NavigationCard` renders them as "Not yet available", never as a
 * link to nowhere. Credits is kept as an option because SASBO asked to keep it, though they no longer
 * run credits (Q10, 2026-09-18). The fund-list rows -- benevolent, death benefit, endowment, Criticare,
 * accounts, subscriptions -- are GONE, not commented out: SASBO does not keep those lists here (Q12).
 */
const cards = [
    {
        title: "Runs",
        desc: "Debit Order Runs",
        href: "/admin/acb/runs",
        icon: LucideCalendarRange,
    },
    {
        title: "Debits",
        desc: "Manage Debits",
        href: "/admin/acb/debits",
        icon: LucidePiggyBank,
    },
    {
        title: "Letters",
        desc: "The union's letters to members, on its letterhead once SASBO has agreed it",
        icon: LucideMails,
    },
    {
        title: "Credits",
        desc: "No longer run by SASBO — kept as an option, as they asked",
        icon: LucideHandCoins,
    },
];

// 🔴 `console.log("Rendering Acb")` WAS HERE, AT MODULE SCOPE -- so it fired once per
// import rather than per render, which is not what its own text claimed, and it fired in
// production. Debug output that outlived the debugging session.
export default function Acb() {
    return (
        <Page>
            <PageTitle title="A.C.B Menu" subtitle="Select options below" />
            <NavigationList cards={cards} />
        </Page>
    );
}
