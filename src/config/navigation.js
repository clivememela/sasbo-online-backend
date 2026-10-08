import {
  HandCoins,
  Home,
  Landmark,
  LucideInfo,
  Building2,
  Users,
  ChartNoAxesCombined,
  Archive,
  NotebookPen,
  Form,
  MapPinnedIcon,
  LucideDatabase, LucideMails, LucideMenu,
  LucideUserRoundPlus, LucideFile, LucideTags,
  LucidePiggyBank, LucidePresentation, LucideCalendarRange,
  LucideConstruction, LucideCopyCheck, UserRoundPen
} from "lucide-react";

/**
 * Sign-in's four Maintenance entries (the 5 Oct Show & Tell: "an administrator creating users with a position and an
 * area", roles kept as data, "a record of who did what"). Written once, for the rail here and for `Maintenance.jsx`'s
 * cards, and `Maintenance.test.jsx` holds the two together. Each names the `permission` its screen needs -- the key the
 * menu rule (`navAccess.js`, OB18) reads to mark an entry for a person who may not use it; the screen itself says what
 * is needed either way.
 *
 * 🔑 NO ICONS, NO DESCRIPTIONS AND NO IMPORT OF `permissions.js`, BECAUSE THIS FILE IS IN THE FIRST LOAD (OB17a). The
 * rail shows neither an icon nor a description at its third level; `Maintenance.jsx` (lazy) gives its cards both. The
 * names are `PERMISSIONS`' own, held to it by `Maintenance.test.jsx` -- importing the catalogue here put all of it,
 * labels and all, into every first paint.
 */
export const SIGN_IN_ENTRIES = [
  { title: 'Users', href: '/admin/maintenance/users', permission: 'USERS_ADMIN' },
  { title: 'Positions', href: '/admin/maintenance/positions', permission: 'USERS_ADMIN' },
  { title: 'Roles', href: '/admin/maintenance/roles', permission: 'USERS_ADMIN' },
  // Short enough for the rail's third level, which truncates; SASBO's own words ("a record of who did what", 5 Oct).
  { title: 'Who did what', href: '/admin/maintenance/audit', permission: 'AUDIT_VIEW' },
];

export const navItems = [
  {
    title: "Home",
    icon: Home,
    href: "/home" },
  {
    title: "Admin Menu",
    icon: LucideDatabase,
    href: "/admin",
    subItems: [
      {
        title: "Daily Menu",
        icon: LucideMenu,
        href: "/admin/daily-menu",
        subItems: [
          {
            title: "Members",
            icon: Users,
            href: "/members",
            subItems: [
              // { title: "Member Application", icon: UsersRoundIcon, href: "/members/apply" },
              { title: "Capture Member", icon: LucideUserRoundPlus, href: "/members/capture" },
              // { title: 'Members', icon: LucideDoorOpen, href: '/archives', desc: 'Extract Members that have resigned Society' },
              // { title: 'Members', icon: LucideFile, href: '/archives', desc: 'BPS Paid Up Members' },
              //{ title: 'Labels', icon: LucideTags, href: '/member/label', desc: 'Print Member Labels - Selection' }
            ]
          },
          {
            title: 'Recruiters',
            icon: LucidePresentation,
            href: '/recruiters',
            desc: 'Capture/Print Recruiters'
          },
          // {
          //   title: 'Print Members Audit Trail',
          //   icon: LucideUserCheck2,
          //   desc: 'Member Detail Enquiry',
          //   color: 'text-red-600 dark:text-red-400'
          // },
          /*{
            title: 'Death Benefit/Bene. Fund',
            icon: LucidePenBox,
            color: 'text-red-600 dark:text-red-400'
          },
          {
            title: 'Endowment Fund',
            icon: LucidePiggyBank,
            //href: '/endowment-fund',
            color: 'text-red-600 dark:text-red-400'
          },
          {
            title: 'Suspension/Rejection Table',
            icon: LucideTable,
            desc: 'Suspension/Rejection Table',
            color: 'text-red-600 dark:text-red-400'
          },
          {
            title: 'Recruiter Table Maintenance',
            icon: LucideTable,
            desc: 'Recruiter Table Maintenance',
            color: 'text-red-600 dark:text-red-400'
          },
          {
            title: 'Area Table Maintenance',
            icon: LucideLocate,
            href: '/area',
            desc: 'Area Table Maintenance',
            color: 'text-red-600 dark:text-red-400'
          },
          {
            title: 'Duplicate Employee Numbers',
            icon: LucideCopyCheck,
            href: '/archives',
            desc: 'Print Duplicate Employee Numbers',
            color: 'text-red-600 dark:text-red-400'
          },*/
          //{title: 'Area & Table Code', icon: LucidePrinter, href: '/archives', desc: 'Print Members by Area & Table code', color: 'text-red-600 dark:text-red-400'},
        ]
      },
      // Month end `04.1`: the Monthly Menu, renamed, at the address it always had (MD38) -- a LEAF, with nothing under
      // it (BR8): its nine legacy rows went with the page's. Written three times -- here, as Admin Membership's card and
      // as the route -- and `MonthlyMenu.test.jsx` holds the three together.
      {
        title: "Month end",
        icon: LucideCalendarRange,
        href: "/admin/monthly-menu",
      },
      // 🗣️ THE MODULE ONCE CALLED "A.C.B." -- "Debit Order Deductions" is SASBO's name for it (the 5 Oct Show & Tell,
      // handover §A; the status report SASBO saw uses it), and Stephne's DOD answer settles the rest: "ACB" becomes
      // "Debit Order" everywhere, and the screens keep their names for now (Runs, Debits). 📐 The rail and the crumbs
      // print "Debit Orders": a 256px rail row cuts the whole name off mid-word, and this file ships on every page's
      // first load. The whole name is Home's (`homeModules.js`) and Admin Membership's. The address stays `/admin/acb`
      // -- deep links and the decks use it. Written twice, here and on the rail's top level below.
      // ⏳ "Debit Orders" IS PROVISIONAL: the wording of the shorter labels is asked of SASBO at Friday's meeting (DOD
      // item 16, `answers/DOD.md`), so both entries change on her answer.
      {
        title: "Debit Orders",
        href:'/admin/acb',
        icon: LucideFile,
        subItems: [
          {
            title: "Debits",
            desc: "Manage Re-Debits",
            href: "/admin/acb/debits",
            icon: LucidePiggyBank,
          },
          {
            title: "Runs",
            desc: "Debit Order Runs",
            href: "/admin/acb/runs",
            icon: LucideCalendarRange,
          },
        ]
      },
      {
        title: "Maintenance",
        desc: "Manage System",
        href:'/admin/maintenance',
        icon: LucideConstruction,
        // 🗣️ SASBO, 18 Sep 2026: "in maintenance there was an option where we could edit the member"
        // -- it went when the menus were rebuilt, and Bank/Branch was "not linking". Both now go where
        // that work is actually done: the member register (open a member, then Edit) and /banks, whose
        // add and edit have been live all along. Mirrors `Maintenance.jsx`'s cards.
        subItems: [
          {
            title: 'Edit Member',
            icon: UserRoundPen,
            href: '/members',
            desc: 'Find a member and edit their record'
          },
          {
            title: 'Bank/Branch',
            icon: Landmark,
            href: '/banks',
            desc: 'Maintain Bank/Branch'
          },
          {
            title: 'Recruitment Channel',
            icon: LucideCopyCheck,
            desc: 'Maintain Recruitment Channels',
            href: '/admin/maintenance/recruitment-channel'
          },
          {
            title: 'Subscriptions',
            icon: LucideMails,
            href: '/admin/maintenance/subscriptions',
            desc: 'Maintain Subscription Table',
          },
          // Month end `01.5` (MD61): SASBO's workplaces. Written twice, here and as `Maintenance.jsx`'s card, and
          // `Maintenance.test.jsx` holds the two together (MR-ROB-37).
          {
            title: 'Workplaces',
            icon: Building2,
            href: '/admin/maintenance/workplaces',
            desc: 'Find a workplace by its name or R/A number',
          },
          ...SIGN_IN_ENTRIES,
        ],
      },
      // { title: "BankBranch", icon: Landmark, color: 'text-red-600 dark:text-red-400'},
      //{ title: "Utilities", icon: LucideToolbox, color: 'text-red-600 dark:text-red-400'},
      //{ title: "Regional Branches", icon: LucideGitBranch, color: 'text-red-600 dark:text-red-400'  },
      //{ title: "Tape", icon: LucideCassetteTape, color: 'text-red-600 dark:text-red-400' },
      //{ title: "D.B.F", icon: LucideFile, color: 'text-red-600 dark:text-red-400' },
      //{ title: "Selective Print", icon: TextSelect, color: 'text-red-600 dark:text-red-400' },
      //{ title: "Committee", icon: LucideChartNoAxesGantt, color: 'text-red-600 dark:text-red-400'},
      //{ title: "Ad-Hoc Label", icon: LucideTag, color: 'text-red-600 dark:text-red-400'},
      //{ title: "Bank Transfer", icon: ArrowRightLeft, color: 'text-red-600 dark:text-red-400'},
      //{ title: "Letters for Return Debits", icon: Mailbox, color: 'text-red-600 dark:text-red-400'},

    ]
  },
  {
    title: "Debit Orders",
    href:'/admin/acb',
    icon: LucideFile,
    subItems: [
      {
        title: "Debits",
        desc: "Manage Re-Debits",
        href: "/admin/acb/debits",
        icon: LucidePiggyBank,
      },
      {
        title: "Runs",
        desc: "Debit Order Runs",
        href: "/admin/acb/runs",
        icon: LucideCalendarRange,
      },
      /*{
        title: "Credits",
        desc: "Manage Credits",
        //href: "/admin/membership/roles",
        icon: LucidePiggyBank,
        color: 'text-red-600 dark:text-red-400'
      },
      {
        title: "Subscriptions",
        desc: "Manage Subscriptions",
        //href: "/admin/membership/permissions",
        icon: LucideMails,
        color: 'text-red-600 dark:text-red-400'
      },
      {
        title: "Benevolent Funds",
        desc: "Manage Benevolent Funds",
        //href: "/admin/membership/users",
        icon: LucideCalendar,
        color: 'text-red-600 dark:text-red-400'
      },
      {
        title: "Death Benefit Funds",
        desc: "Manage Death Benefit Funds",
        //href: "/admin/membership/roles",
        icon: LucideCalendar,
        color: 'text-red-600 dark:text-red-400'
      },
      {
        title: "Endowment Funds",
        desc: "Manage Endowment Funds",
        //href: "/admin/membership/permissions",
        icon: LucideCalendar,
        color: 'text-red-600 dark:text-red-400'
      },
      {
        title: "Criticare",
        desc: "Manage Criticare",
        //href: "/admin/membership/permissions",
        icon: LucideCross,
        color: 'text-red-600 dark:text-red-400'
      },
      {
        title: "Accounts",
        desc: "Manage Accounts",
        //href: "/admin/membership/permissions",
        icon: LucideBuilding,
        color: 'text-red-600 dark:text-red-400'
      },*/
    ]
  },
  {
    title: "Bank/Branch",
    icon: Landmark,
    href: "/banks",
    subItems: [
      // { title: "New Bank", icon: LucideFile, href: "/banks/new" },
      {
        title: "Bank Listing",
        icon: MapPinnedIcon,
        href: "/banks/listing"
      },
      //{ title: 'Tb Code/Lab Ind (Bank)', icon: Archive, desc: 'Extract by Tb Code/Lab Ind (Bank)' , color: 'text-red-600 dark:text-red-400'},
      //{ title: 'Tb Code/Lab Ind (Mail)', icon: Archive, desc: 'Extract by Tb Code/Lab Ind (Mail)', color: 'text-red-600 dark:text-red-400' }
    ]
  },
  {
    title:"Document Archives",
    icon: Archive,
    href: "/archives",
    subItems: [
      { title: "Affiliations/Other Trade Unions", icon: Building2, href: "/archives/affiliations-other-trade-unions" },
      { title: "Finance Houses", icon: HandCoins, href: "/archives/finance-houses" },
      { title: "Information & Research", icon: LucideInfo, href: "/archives/research-information" }
    ]
  },
  // {
  //   title: "Maintenance",
  //   desc: "Manage System",
  //   href:'/admin/maintenance',
  //   icon: LucideConstruction,
  //   subItems: [
  //     {
  //       title: 'Bank/Branch',
  //       icon: Landmark,
  //       desc: 'Maintain Bank/Branch'
  //     },
  //     {
  //       title: 'Recruitment Channel',
  //       icon: LucideCopyCheck,
  //       desc: 'Maintain Recruitment Channels',
  //       href: '/admin/maintenance/recruitment-channel'
  //     },
  //   ],
  // },
  { 
    title: "Members", 
    icon: Users, 
    href: "/members",
    subItems: [
      // { title: "Member Application", icon: UsersRoundIcon, href: "/members/apply" },
      { title: "Capture Member", icon: LucideUserRoundPlus, href: "/members/capture" },
      // { title: 'Members', icon: LucideDoorOpen, href: '/archives', desc: 'Extract Members that have resigned Society' },
      // { title: 'Members', icon: LucideFile, href: '/archives', desc: 'BPS Paid Up Members' },
      // 🔴 NO `href`, AND THAT IS THE FIX RATHER THAN A REMOVAL. `/member/label` has never
      // been a route: App.jsx declares none, there is no catch-all, and clicking this in the
      // rail rendered a BLANK PAGE -- the shell with nothing in it, no error, no message.
      // card.html's rail shows Labels under Members, so the entry belongs; what it must not
      // do is promise a screen that does not exist. `SidebarItem` renders an entry with no
      // href as a quiet, non-navigating label (Maintenance > Bank/Branch was one too, until it
      // was pointed at /banks on 18 Sep 2026). It becomes a link again the day the route lands.
      { title: 'Labels', icon: LucideTags, desc: 'Print Member Labels - Selection' }
    ]
  },
  {
    title: 'Recruiters',
    icon: LucidePresentation,
    href: '/recruiters',
    desc: 'Capture/Print Recruiters'
  },
  // 🗣️ SUBSCRIPTIONS IS NOT ON THIS LEVEL ANY MORE -- the 5 Oct Show & Tell (handover §A): only staff change the
  // amounts, so it lives under Admin Menu > Maintenance alone, and it came off Home's quick view with this entry (Home
  // is this list read across). Its address is unchanged; `Maintenance.test.jsx` holds where it is reached from.
  {
    title: "Reports", 
    icon: ChartNoAxesCombined, 
    href: "/reports",
    subItems: [
      // 🗣️ "Location Visit", not "Organizer Report" -- SASBO, 18 Sep 2026 ("change only"): the name
      // confused the people filling it in. The route keeps its old name so no bookmark or tool breaks.
      { title: "Location Visit", icon: NotebookPen, href: "/reports/organizer-report" },
      { title: "Forms", icon: Form, href: "/reports/forms" },
      { title: "Location Listing", icon: MapPinnedIcon, href: "/reports/locations" },
      // October build (5 Oct Show & Tell): the organisers' batch print -- every R/A number at once, a bank a page. An
      // icon the rail already draws: this file is in the first load, and a new icon there is weight on every screen.
      { title: "Batch print", icon: LucideFile, href: "/reports/locations/print" }
    ]
  }
];
