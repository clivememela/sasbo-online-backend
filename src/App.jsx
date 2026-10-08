import { lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import AuthLayout from './layouts/AuthLayout';

/**
 * ROUTE-LEVEL CODE SPLITTING -- R-PERF-A/C, `03.1`.
 *
 * Every page below was a STATIC import, so the first paint of /login downloaded the
 * member register, the capture form, the ACB debits screen and every archive page.
 * The bundle was 502 KiB before this phase, already past Vite's own warn threshold,
 * and it grew with every screen regardless of whether anyone visited it.
 *
 * The layouts stay STATIC and that is deliberate: they are on every route, so
 * splitting them would buy nothing and cost a waterfall -- the shell would have to
 * arrive before the page could even start loading.
 *
 * ⚠️ Splitting interacts with the route smoke test, and not in its favour. See
 * `RoutePending` for what had to change so the guard did not quietly weaken.
 *
 * The <Suspense> and the <ErrorBoundary> are NOT here. They sit beside the <Outlet> in
 * `Main` and `AuthLayout`, because a boundary wrapped around <Routes> would take the
 * header, the navigation and the footer down with the page it caught -- and A11's
 * standard is that the shell survives.
 */
// 🔴 LAZY, and the measurement is why. Moving the Google Maps `APIProvider` out of
// `main.jsx` was supposed to take the library off the 24 routes that do not use it -- and
// a STATIC import here put it straight back into the entry chunk, which is where it
// already was. The entry did not move by a single byte, which is what said so. Lazy, it
// rides the three route chunks that actually render an address field.
// It resolves inside the <Suspense> beside the <Outlet> in `Main`, like every route.
const MapsScope = lazy(() => import('./components/form/MapsScope'));

const HomePage = lazy(() => import('./pages/Home'));
const BanksPage = lazy(() => import('./pages/bank_branch/BankBranch.jsx'));
const MembersPage = lazy(() => import('./pages/members/Members.jsx'));
const MembershipApplication = lazy(() => import('./pages/forms/MembershipApplication.jsx'));
const ReportsPage = lazy(() => import('./pages/ReportOptions.jsx'));
const ArchivesPage = lazy(() => import('./pages/archives/ArchiveSearch.jsx'));
const AffiliationsOtherTradeUnions = lazy(() => import('./pages/archives/AffiliationsOtherTradeUnions.jsx'));
const FinanceHouses = lazy(() => import('./pages/archives/FinanceHouses.jsx'));
const ResearchInformation = lazy(() => import('./pages/archives/ResearchInformation.jsx'));
const AuthPage = lazy(() => import('./pages/auth/Auth.jsx'));
const Admin = lazy(() => import('./pages/admin/Admin.jsx'));
const Acb = lazy(() => import('./pages/admin/Acb.jsx'));
const AcbDebits = lazy(() => import('./pages/acb/AcbDebits.jsx'));
const AcbRuns = lazy(() => import('./pages/acb/AcbRuns.jsx'));
const DailyMenu = lazy(() => import('./pages/admin/DailyMenu.jsx'));
// Month end `04.1`: the page is renamed "Month end" and its address is not (MD38) -- the Monthly Menu's address is
// written in five places, two of them tools' route lists (BR47). `04.3`-`04.8`: ONE route, and one chunk, for the six
// statistics; a name the server has no statistic for is the page's not-found state, never a blank.
const MonthlyMenu = lazy(() => import('./pages/admin/MonthlyMenu.jsx'));
const MonthEndStatistic = lazy(() => import('./pages/admin/MonthEndStatistic.jsx'));
const CaptureMember = lazy(() => import('./pages/members/CaptureMember.jsx'));
const Maintenance = lazy(() => import('./pages/admin/Maintenance.jsx'));
const BankListing = lazy(() => import('./pages/bank_branch/BankListing.jsx'));
const RecruitmentChannel = lazy(() => import('./pages/maintainance/RecruitmentChannel.jsx'));
const Subscriptions = lazy(() => import('./pages/maintainance/Subscriptions.jsx'));
// Month end `01.5`: SASBO's workplaces -- a leaf of Maintenance, beside the other tables it maintains.
const Workplaces = lazy(() => import('./pages/maintainance/Workplaces.jsx'));
// Sign-in (contracts/signin.md): a person's own password, and Maintenance's users, positions, roles and the record of
// who did what. Lazy like every page, so the first load carries none of the administration screens.
const ChangePassword = lazy(() => import('./pages/auth/ChangePassword.jsx'));
const Users = lazy(() => import('./pages/users/Users.jsx'));
const Positions = lazy(() => import('./pages/users/Positions.jsx'));
const Roles = lazy(() => import('./pages/users/Roles.jsx'));
const Audit = lazy(() => import('./pages/users/Audit.jsx'));

// NAMED exports, so the dynamic import has to be adapted to lazy()'s default-export
// contract. Written out rather than hidden in a helper: the shape of the mistake --
// `lazy(() => import(x))` on a module with no default -- fails at RUNTIME, on that
// route only, and this is the file where someone would copy the wrong line.
const OrganizerReport = lazy(() =>
  import('./pages/reports/OrganizerReport.jsx').then((m) => ({ default: m.OrganizerReport })));
const Forms = lazy(() =>
  import('./pages/forms/Forms.jsx').then((m) => ({ default: m.Forms })));
const Locations = lazy(() =>
  import('./pages/reports/Locations.jsx').then((m) => ({ default: m.Locations })));
const BatchPrint = lazy(() =>
  import('./pages/reports/BatchPrint.jsx').then((m) => ({ default: m.BatchPrint })));
const Recruiters = lazy(() =>
  import('./pages/members/Recruiters.jsx').then((m) => ({ default: m.Recruiters })));

function App() {
  return (
    <Routes>
      <Route element={<AuthLayout />}>
        <Route path="login" element={<AuthPage />} />
        <Route path="change-password" element={<ChangePassword />} />
        <Route path="signup" element={<Navigate to="/login" replace />} />
      </Route>
      <Route path="/" element={<AppLayout />}>
        <Route index element={<Navigate to="/login" replace />} />
        <Route path="home" element={<HomePage />} />
        <Route path="admin" element={<Admin />} />
        <Route path="admin/acb" element={<Acb />} />
        <Route path="admin/acb/debits" element={<AcbDebits />} />
        <Route path="admin/acb/runs" element={<AcbRuns />} />
        <Route path="admin/daily-menu" element={<DailyMenu />} />
        <Route path="admin/monthly-menu" element={<MonthlyMenu />} />
        <Route path="admin/monthly-menu/:statistic" element={<MonthEndStatistic />} />
        <Route path="recruiters" element={<Recruiters />} />
        <Route path="banks" element={<MapsScope><BanksPage /></MapsScope>} />
        <Route path="banks/listing" element={<BankListing />} />
        <Route path="members" element={<MapsScope><MembersPage /></MapsScope>} />
        <Route path="members/apply" element={<MembershipApplication />} />
        <Route path="members/capture" element={<MapsScope><CaptureMember /></MapsScope>} />
        <Route path="members/recruiters" element={<Recruiters />} />
        <Route path="admin/maintenance/subscriptions" element={<Subscriptions />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="reports/organizer-report" element={<OrganizerReport />} />
        <Route path="reports/forms" element={<Forms />} />
        <Route path="reports/locations" element={<Locations />} />
        <Route path="reports/locations/print" element={<BatchPrint />} />
        <Route path="archives" element={<ArchivesPage />} />
        <Route path="archives/affiliations-other-trade-unions" element={<AffiliationsOtherTradeUnions />} />
        <Route path="archives/finance-houses" element={<FinanceHouses />} />
        <Route path="archives/research-information" element={<ResearchInformation />} />
        <Route path="admin/maintenance" element={<Maintenance/>} />
        <Route path="admin/maintenance/recruitment-channel" element={<RecruitmentChannel />} />
        <Route path="admin/maintenance/workplaces" element={<Workplaces />} />
        <Route path="admin/maintenance/users" element={<Users />} />
        <Route path="admin/maintenance/positions" element={<Positions />} />
        <Route path="admin/maintenance/roles" element={<Roles />} />
        <Route path="admin/maintenance/audit" element={<Audit />} />
      </Route>
    </Routes>
  )
}

export default App
