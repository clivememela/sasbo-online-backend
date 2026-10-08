import { lazy, Suspense, useEffect, useMemo, useState, useRef, useCallback } from "react";
import { flushSync } from "react-dom";
import { useNavigate, useLocation, useOutletContext, useSearchParams } from "react-router-dom";
import {
    LucideUserRoundPlus, AlertCircle, CircleCheck, Printer, SlidersHorizontal,
    Pencil, Check,
} from "lucide-react";
import { memberService } from "@/services/memberService.js";
import { jobGradeService } from "@/services/jobGradeService.js";
import { bankService } from "@/services/bankService.js";
import { recruiterService } from "@/services/recruiterService.js";
import { getCommittees } from "@/services/referenceService.js";
import { recruitedArea } from "@/utils/recruitment.js";
import { committeeOptions, inOrder, isCosatuMember, structureErrors, withStructureAnswer } from "@/utils/structures.js";
import useAsyncOptions from "../../hooks/useAsyncOptions.js";
import { TextInput, SearchableSelect, SelectInput, Button, CheckboxGroup, DateField } from "../../components/form";
import StatusChip, { CANCELLED_STATUSES } from "../../components/common/StatusChip.jsx";
import Table from "../../components/table/Table.jsx";
import { ConfirmDialog, closeOn } from "../../components/overlay/Modal.jsx";
import {
    TITLE_OPTIONS, RACE_OPTIONS, PAYMENT_TYPE_OPTIONS,
    STATUS_OPTIONS, IDENTITY_TYPE_OPTIONS, GENDER_OPTIONS,
    BRANCH_OPTIONS, MEMBER_FIELD_LABEL_MAP, EMPLOYMENT_TYPE_OPTIONS, IDENTITY_TYPE_PASSPORT,
    DEBIT_DAY_OPTIONS, PAYMENT_TYPE_PAYROLL, PAYMENT_TYPE_PAYROLL_LEGACY,
    ACCOUNT_TYPE_OPTIONS, BEC_STRUCTURES_REPRESENTED, BEC_EXECUTIVE_POSITIONS, COSATU_POSITIONS, COSATU_POSITION_LABELS,
    MEMBER_FIELD_TABS as FIELD_TO_TAB_MAP,
} from "@/config/memberConstants.js";
import {
    validateSAID, validateEmail, extractDateOfBirthFromSAID, extractGenderFromSAID
} from "@/utils/validateSAID.js";
import PageTitle from "../../components/common/PageTitle.jsx";
import AddressAutocomplete from "../../components/form/AddressAutocomplete";
import DataState from "../../components/common/DataState.jsx";
import ErrorBoundary from "../../components/common/ErrorBoundary.jsx";
import FigureStrip from "../../components/members/FigureStrip.jsx";
import MemberPanel from "../../components/members/MemberPanel.jsx";
import Criteria, { CRITERION } from "../../components/common/Criteria.jsx";
import Pager from "../../components/common/Pager.jsx";
import KeyHints from "../../components/common/KeyHints.jsx";
import Sheet, { PAGE_GUTTER } from "../../components/common/Sheet.jsx";
import useReferenceData from "../../hooks/useReferenceData.js";
import reportError from "../../lib/reportError.js";
import { ERROR_KINDS, messageOf, validationErrorsOf, writeMayHaveLanded } from "@/services/parseApiError.js";
import useFocusOn from "../../hooks/useFocusOn.js";
import { EMPTY, formatDate, formatZar, toWireDate } from "@/lib/format.js";
import { finishField, sanitiseField, validateField } from "@/utils/inputRules.js";
import { branchNamesForBank, settleBankChange } from "@/utils/bankBranches.js";
import { cn } from "@/lib/utils.js";
import { formalName, personName } from "@/lib/names.js";
import { fieldValue } from "@/lib/fieldValue.js";
import WorkplaceField from "../../components/members/WorkplaceField.jsx";
import useWorkplaceSource from "../../hooks/useWorkplaceSource.js";
import useWorkplaceChoice from "../../hooks/useWorkplaceChoice.js";
import useMemberWorkplace from "../../hooks/useMemberWorkplace.js";
import { MEMBER_CHANGED_SINCE, WORKPLACE_UNKNOWN } from "@/config/monthEnd.js";
import ChangeStatus from "../../components/members/ChangeStatus.jsx";
import { useDebounce } from "../../hooks/useDebounce.js";
import useRegisterSearch from "./useRegisterSearch.js";
import { useAuth } from "../../auth/useAuth";
import RequirePermission from "../../auth/RequirePermission";
import { PERMISSIONS } from "../../auth/permissions";
import {
    REGISTER_PAGE_SIZES, SEARCH_PAUSE_MS, TERM_MAX, historyState, isRegisterPage, isWholeIdentityNumber, nextSort,
    overviewFigures, readView, registerQuery, writeView,
} from "./memberRegister.js";

/*
 * 📜 THE HISTORY TAB IS A CHUNK OF ITS OWN (month end 03.6): it is fetched when the tab is opened -- never at the
 * register's first paint -- and a chunk that will not load, or a tab that throws, is held by a boundary of its own and
 * says so in one line, the record around it untouched (Month end's figures do the same, under a boundary of their own).
 */
const MemberHistory = lazy(() => import("./MemberHistory.jsx"));

const historyFailed = () => (
    <p role="alert" className="text-sm text-ink-muted">The history could not be shown. Reload the page to try again.</p>
);


/** The job-grade list when there is no current answer: stable, so the select is not handed a new array each render. */
const NO_JOB_GRADES = [];

/** A page with nothing on it, while none has answered: stable, so the table is not handed a new array each render. */
const NO_ROWS = [];

/*
 * 🔴 HOW LONG THE RECORD OUTLIVES THE CLICK THAT CLOSED IT. This is `--duration-panel` in
 * `index.css`, and the two are commented at both ends because nothing else keeps them in
 * step: the panel's column animates from 448px to nothing over that time, and if the record
 * is thrown away first the animation plays over an empty white box.
 */
const PANEL_MS = 380;

/*
 * 🔍 THE TWELVE CRITERIA BEHIND "MORE CRITERIA" -- matched by the server since A10, a page at a time, and kept out of
 * the address bar: an identity, account or cellphone number, an e-mail address, are a member's details. What a box
 * matches changed with the move, and each box's placeholder says how: a name or a number by its START, an identity,
 * account or cellphone number WHOLE OR BY ITS LAST FOUR (the four every masked display prints), a pick exactly. The
 * browser used to match any part of any of them, over every member it held.
 *
 * Bank, Branch and Status are the criteria row's three, and live in the address bar with the search and the page.
 */
const NO_CRITERIA = Object.freeze({
    title: "",
    employeeNumber: "",
    membershipNumber: "",
    initials: "",
    surname: "",
    identityType: "",
    identityNumber: "",
    bankAccountNumber: "",
    phone: "",
    personalEmail: "",
    workEmail: "",
    race: "",
});

/** The criteria chosen from a list: asked at once. The typed ones wait for typing to pause. */
const CHOSEN_CRITERIA = ["title", "identityType", "race"];

/** The register's figures where none has been read: nothing, never a zero (A47, K1). */
const NO_FIGURES = Object.freeze({
    total: null, active: null, pending: null, suspended: null, newThisMonth: null, newDebitOrders: null,
    updatedToday: null,
});

/*
 * THE FIVE SECTIONS OF A MEMBER'S RECORD -- this panel's own list, not the capture form's.
 *
 * 🔴 IT IS SEPARATE FROM `MEMBER_TABS` ON PURPOSE. Main removed "Sasbo Structures" from the
 * capture form (client 4a7a67b) and kept it here on the record, so the two screens are meant
 * to list different sections. The design branch had briefly pointed this panel at the capture
 * constant; taking main's shorter list through it would have silently removed the Structures
 * tab from every member's record, and switched `TAB_PLACE` off with it.
 *
 * 📐 `label` is what the strip SHOWS -- short, because the docked panel is a 400px column and
 * the full phrases are 762px of tabs (the owner, 2026-09-10). `full` is the tab's accessible
 * NAME, so a screen reader and the automation suite (`openModalTab('Employment & Bank')`)
 * still address each tab by the phrase that names it. The visible word is always contained in
 * the full phrase, which is what WCAG 2.5.3 asks of a label that differs from its name.
 *
 * 📜 SIX SINCE MONTH END 03.6: History, before Structures -- the owner's approval of the mock on
 * 2026-09-30 (`TAB_PLACE`, below). The order here is the keyboard's, and it follows the eye.
 */
export const RECORD_TABS = [
    { id: "personal", label: "Personal", full: "Personal Information" },
    { id: "employment", label: "Employment", full: "Employment & Bank" },
    { id: "membership", label: "Membership", full: "Membership & Subscriptions" },
    { id: "representatives", label: "Recruitment", full: "Recruitment Channel" },
    { id: "history", label: "History", full: "History of changes" },
    { id: "structures", label: "Structures", full: "Sasbo Structures" },
];
const TABS = RECORD_TABS;

/** A record field's value, or "Not on file" when the record holds none -- never an invented default. */
const notOnFile = (value) => (value === null || value === undefined || String(value).trim() === "" ? "Not on file" : value);

/** An amount on file through the one formatter (00.2, F33), or "Not on file" -- never R 0,00 for nothing. */
const amountOnFile = (value) => {
    const shown = formatZar(value);
    return shown === EMPTY ? notOnFile(null) : shown;
};

/*
 * 🗣️ WHERE EACH OF THE FIVE SECTIONS SITS IN THE RECORD'S TAB STRIP -- the owner's
 * arrangement, settled over three rounds on 2026-09-10:
 *
 *     Personal                      Membership
 *                  Employment
 *     Recruitment                    Structures
 *
 * ① Three columns, so the sections line up under each other whatever the width. Left to
 *    `flex-wrap` the strip broke 4+1 on a 448px panel and 3+2 on the owner's own window,
 *    because the split was whatever the labels happened to measure.
 * ② The last section takes the THIRD column, not the second -- *"so that there's a gap in
 *    the middle between Recruitment and Structures."*
 * ③ That leaves the middle column holding one tab where the outer two hold two, so it spans
 *    both rows and centres in them -- *"right in the center, relative to all the other panel
 *    tabs."*
 * ④ And the strip is SYMMETRICAL about its own centre line: the left column reads from the
 *    left edge, the right column to the right edge, and the middle one is centred.
 *
 * 🗣️ AT SIX -- the owner's approval of month end 03.6's mock, 2026-09-30, the "proposed" strip:
 *
 *     Personal        Employment        Membership
 *     Recruitment      History          Structures
 *
 * A sixth tab fills the 3x2 grid, and the arrangement above would have been switched off whole
 * (it applied at five and at no other number). ④ is kept as ④ reads for a grid with no hole in
 * it: each column keeps its edge -- the left from the left, the right to the right, the middle
 * centred -- so the four corners stay where he put them and Structures stays under Membership
 * (*"Make structures aligned under Membership"*). What gives is Employment, up from between the
 * rows into the top row, because at six the middle column holds two like the others. Placed by
 * the grid's own flow, in `RECORD_TABS` order, so the keyboard follows the eye.
 *
 * Five is kept exactly as it was, and any other count falls back to the plain flow.
 */
export const tabPlace = (count) => {
  if (count === 5) {
    return [
      'justify-self-start',
      'row-span-2 justify-self-center self-center',
      'justify-self-end',
      'justify-self-start',
      'col-start-3 justify-self-end',
    ];
  }
  if (count === 6) {
    return [
      'justify-self-start',
      'justify-self-center',
      'justify-self-end',
      'justify-self-start',
      'justify-self-center',
      'justify-self-end',
    ];
  }
  return [];
};
const TAB_PLACE = tabPlace(TABS.length);

/*
 * 🔴 THE 44 "BEC COMMITTEES" THIS FILE CARRIED WERE INVENTED (CM-14) -- names like "Century City Banking Cluster",
 * grouped by made-up regions, stored against nothing. The committee a BEC member sits on is chosen from SASBO's own
 * list since V55, as their month-end report prints it (`GET /api/reference/committees`, Monthly Menu 01.1).
 *
 * Their export, `SASBO_BEC_BRANCHES`, went in month end 01.3 (3,394 bytes of the minified screen), and the
 * map of each field to its tab with it: capture and the record share `MEMBER_FIELD_TABS` since then, V55's
 * structure fields among them.
 */

/**
 * The stored value, folded onto the name the form offers.
 *
 * 🔑 THE FOLD RUNS THE OTHER WAY NOW (A6). It used to turn the server's "Payroll Deduction" into
 * the browser's "Payroll"; the browser was the one that had drifted, so the browser is what moved.
 * `PaymentType` on the server is `PAYROLL("Payroll Deduction")` and that is what the option says.
 *
 * ⚠️ The fold itself is NOT redundant and must not be deleted with the rename. `MemberDto.paymentType`
 * is a String passed through from storage, not the enum, so members captured under the old spelling
 * still arrive as "Payroll". Without this, opening such a record would show a payment type that is
 * not one of the options, and the first save would report a change the administrator never made.
 */
const normalizePaymentType = (paymentType) =>
    paymentType === PAYMENT_TYPE_PAYROLL_LEGACY ? PAYMENT_TYPE_PAYROLL : paymentType;

/*
 * 🔴 THE SAVE CONFIRMATION SPOKE IN FIELD KEYS, AND PRINTED "No" AS "-" (`06.5`, the `06.3` critique, F-P55). It
 * labelled each change from the shared `MEMBER_FIELD_LABEL_MAP`, which knows none of the record's Structures fields,
 * so an administrator was asked to confirm `isCosatu: Y → N` and `becCommitteeId: - → bec-04`. It named fields
 * differently from the form above it, printed `false` and an empty list as "-" or nothing, and reported an unchanged
 * COSATU positions list as a change, because the record holds it as "PEC,CEC" and the form as an array.
 *
 * The form's own words come first here. The shared map, which the Capture Member arc also reads, is left as it is.
 */
const RECORD_FIELD_LABELS = {
    firstNames: "First Name(s)",
    identityType: "Identity Type",
    identityNumber: "Identity Number",
    phone: "Cellphone Number",
    address: "Residential Address",
    bankName: "Bank Where Employed",
    employmentType: "Employment Type",
    branch: "Bank Branch",
    debitDay: "Debit Day",
    accountType: "Account Type",
    bankAddress: "Bank Work Address",
    // 🗑️ `status` WAS LABELLED HERE, reclaimed in month end 03.3: a status is changed through *Change status* and never
    // edited, so no edit can change it and `getChangedFields` never lists it (`Members.changeStatus.test.jsx`).
    // The structures: every answer of the tab since V55, the committee by its code, read by its name (`readableIn`).
    isShopSteward: "Shop Steward",
    shopStewardElectionDate: "Shop Steward Election Date",
    isBec: "BEC Member",
    isMemnet: "Memnet Member",
    becCommitteeCode: "Branch Executive Committee",
    isBecRecruiter: "BEC Recruiter",
    isBecStructureRep: "BEC Structure Representative",
    becStructures: "BEC Structures Represented",
    isBecExecutive: "BEC Executive",
    becExecutivePosition: "BEC Executive Position",
    isCosatu: "COSATU Member",
    isCosatuLocal: "COSATU Local structure",
    isCosatuRegional: "COSATU Regional structure",
    cosatuLocal: "COSATU Local",
    cosatuRegion: "Region within Local",
    cosatuPositions: "COSATU Positions",
    // 01.3: a workplace is CHANGED when its id is -- two of SASBO's workplaces may share a name -- and is listed by its
    // name and R/A number (`readable`), once, not three times for its three scalars.
    workplaceId: "Workplace",
};

/** Never listed: identifiers, and a name the form keeps beside a field that is listed -- the committee's, beside
 * its code, and the workplace's name and R/A number, beside its id (month end 01.3). */
const UNLISTED_KEYS = new Set(["id", "membershipNumber", "memberId", "becCommitteeName", "workplaceName", "workplaceRaNumber"]);

/**
 * A stored yes/no as a person reads it. `null` is its own answer on the read side -- Shop Steward
 * and BEC had no column until V29 (CM-05), so for every earlier member nobody ever answered.
 */
const yesNo = (flag, unanswered = "No") => (flag === true ? "Yes" : flag === false ? "No" : unanswered);

/** The errors a Structures answer settles besides its own: a Yes/No's follow-ups, which a No takes away. */
const STRUCTURE_ERRORS_SETTLED_BY = {
    isShopSteward: ["shopStewardElectionDate"],
    isBec: ["becCommitteeCode", "becStructures", "becExecutivePosition"],
    isBecStructureRep: ["becStructures"],
    isBecExecutive: ["becExecutivePosition"],
};

/** A list the record holds, as a person reads it. */
const listText = (list, none = "None") => (Array.isArray(list) && list.length > 0 ? list.join(", ") : none);

/**
 * A value as the form holds it, for comparing: a yes/no flag as a boolean, a list as its joined text, a date as its
 * calendar day -- the record's `[y, m, d]` and the picker's "yyyy-mm-dd" are one day in two spellings.
 */
const comparable = (key, value) => {
    if (key === "paymentType") return normalizePaymentType(value);
    if (DATE_KEYS.has(key)) return toWireDate(value);
    if (/^is[A-Z]/.test(key)) return value === true || value === "Y";
    if (Array.isArray(value)) return value.join(",");
    if (value === null || value === undefined) return "";
    return value;
};

/**
 * A record date as the panel shows it (R3-F1), and the one rule the deleted `formatBackendDate` used to carry:
 * EMPTY stays empty. `formatDate` answers the em dash for a missing value and `Detail` prints its own for an empty
 * one, so calling it unguarded put a literal "—" inside a disabled edit FIELD -- which a capture caught and no test
 * did. Written once because it was written three times.
 */
const recordDate = (value) => (value ? formatDate(value) : "");

/** The record's date fields, by name. conventions §15 gives them all one shape: `yyyy/mm/dd`. */
const DATE_KEYS = new Set(["dateOfBirth", "joinDate", "electionDate", "shopStewardElectionDate"]);

/** A value as a person reads it. */
const readable = (key, value, record) => {
    // 01.3: a workplace is read by its name and R/A number, never by the id the save sends.
    if (key === "workplaceId") return value == null ? "No workplace on record" : `${record.workplaceName} (R/A ${record.workplaceRaNumber})`;
    const shown = comparable(key, value);
    if (typeof shown === "boolean") return shown ? "Yes" : "No";
    if (key === "becStructures" || key === "cosatuPositions") return shown ? String(shown).split(",").join(", ") : "none";
    // R3-F1: a LocalDate arrives as [y, m, d], and a date was compared as its array joined with commas -- so an
    // untouched date read "1988,6,15", and a changed one "1988,6,15 → 1988-06-15". Dates are NAMED, never sniffed: a
    // value that merely looks like one is not one, and the record holds exactly these four.
    if (DATE_KEYS.has(key)) return formatDate(value);
    return shown === "" ? "—" : String(shown);
};

/** A field of a member as a person reads it -- the committee by its name, which the form keeps beside its code. */
const readableIn = (member, key) =>
    key === "becCommitteeCode" && member.becCommitteeName ? member.becCommitteeName : readable(key, member[key], member);

/** A field of the record by the name the record gives it -- the save's confirmation, and since 03.6 the History tab. */
export const fieldLabel = (key) =>
    RECORD_FIELD_LABELS[key] || MEMBER_FIELD_LABEL_MAP[key] || key.replace(/([A-Z])/g, " $1").replace(/^./, (c) => c.toUpperCase());

const getChangedFields = (draft, original) => Object.keys(draft)
    .filter((key) => !UNLISTED_KEYS.has(key))
    .filter((key) => comparable(key, draft[key]) !== comparable(key, original[key]))
    .map((key) => `${fieldLabel(key)}: ${readableIn(original, key)} → ${readableIn(draft, key)}`);


/**
 * THE TWO REFERENCE LISTS BEHIND THIS SCREEN'S EDIT PANEL. `06.1b`, EH-D9.
 *
 * 🔴 BOTH LOADED WITH `.catch(err => console.error(...))`. The member register itself has
 * had a proper error state since `04.2` (that was A27), but the two lists that fill the edit
 * panel's Bank and Lead Recruiter selectors did not -- so a half-down backend rendered a
 * perfectly good register whose edit panel silently offered no banks. An administrator
 * editing a member would conclude the bank list was empty, not that the server had failed.
 *
 * 🔑 At module scope because `useReferenceData` needs a stable object; rebuilt per render
 * it re-fetches forever, which looks exactly like a slow backend.
 */
const REFERENCE_LOADERS = {
    banks: () => bankService.getBankNames(),
    'bank branches': () => bankService.getBankListing(),
};

/**
 * The record's save notice, by kind. `unconfirmed` and `stale` are R3-F2's: a save whose answer never arrived is not
 * "not saved", and a save that landed before the register failed to refresh is saved.
 */
const SAVE_NOTICE_TITLES = {
    invalid: "Fix these before saving",
    unchanged: "Nothing to save",
    failed: "The changes were not saved",
    unconfirmed: "The save could not be confirmed",
    stale: "The changes were saved",
    changed: "Someone else changed this record",
};

export default function Members() {
    const navigate = useNavigate();
    const location = useLocation();
    const [params, setParams] = useSearchParams();

    /*
     * 📄 THE REGISTER'S VIEW IS THE ADDRESS BAR'S (A10) -- the search, the page, its size and order, and the criteria
     * row's three -- read whole on every render, so a refresh, a link or Back opens the register where it was.
     * `memberRegister.js` holds what each may be; anything unreadable is the register's default, never a refusal.
     */
    const view = readView(params, location.state, STATUS_OPTIONS);
    // The view as the handlers that change it read it: they run after the render that showed it.
    const viewRef = useRef(view);
    useEffect(() => {
        viewRef.current = view;
    });

    /** A new view, in the address bar: pushed, so Back returns to the one before -- or replaced, for what typing asks. */
    const showView = useCallback((changes, { replace = false } = {}) => {
        const next = { ...viewRef.current, ...changes };
        viewRef.current = next;
        setParams(writeView(next), { replace, state: historyState(next) });
    }, [setParams]);

    /*
     * 🔍 "MORE CRITERIA" -- a member's details, never the address bar's (`NO_CRITERIA`). The typed ones are asked once
     * typing pauses -- each would otherwise be a request a keystroke -- and the picked ones at once.
     */
    const [criteria, setCriteria] = useState(NO_CRITERIA);
    const typedCriteria = useDebounce(criteria, SEARCH_PAUSE_MS);
    const askedCriteria = useMemo(() => {
        const asked = {};
        for (const field of Object.keys(NO_CRITERIA)) {
            const value = CHOSEN_CRITERIA.includes(field) ? criteria[field] : typedCriteria[field];
            const trimmed = String(value ?? "").trim().slice(0, TERM_MAX);
            if (trimmed) asked[field] = trimmed;
        }
        return asked;
    }, [criteria, typedCriteria]);

    const [selectedMember, setSelectedMember] = useState(null);

    /*
     * 📂 A RECORD IS READ WHEN IT IS OPENED (A10). A row of the register is its six columns and the id; the record --
     * every field, and the workplace's lineage -- is `GET /api/members/{id}`, read for the one member chosen. `opening`
     * holds the row while that read is out, and why it failed if it did: the panel shows the row's card, says it is
     * opening, and offers no edit until the record itself is in -- an edit of a row would save a member with every
     * other field blank.
     */
    const [opening, setOpening] = useState(null);
    const openTicket = useRef(0);

    /*
     * 🔴 CLOSING IS TWO THINGS, AND THEY DO NOT HAPPEN AT THE SAME MOMENT: the column starts
     * to collapse now, and the record is thrown away when it has finished. `panelClosing`
     * carries the gap. `open` is the column's business, `selectedMember` is the record's, and
     * `MemberPanel` renders its body off the record rather than off `open` for this reason.
     *
     * ⚠️ CHOOSING ANOTHER MEMBER MID-CLOSE MUST CANCEL THE PENDING CLEAR, or the timer lands
     * a few hundred milliseconds later and throws away the member you just picked -- which is
     * exactly the shape of bug a delayed teardown introduces if nobody owns it.
     */
    const [panelClosing, setPanelClosing] = useState(false);
    const closeTimer = useRef(null);
    // The guarded close, for the window-level Escape listener, which is registered once. Kept current by an effect.
    const leaveRef = useRef(null);

    /** The column stays: a close that was under way is called off. */
    const keepOpen = useCallback(() => {
        if (closeTimer.current) {
            clearTimeout(closeTimer.current);
            closeTimer.current = null;
        }
        setPanelClosing(false);
    }, []);

    /**
     * Reads the record of the row chosen, and opens it. The newest choice wins: the answer for a member chosen before
     * another -- or before the panel closed -- is dropped (the ticket).
     */
    const loadRecord = useCallback((row) => {
        keepOpen();
        const ticket = ++openTicket.current;
        setOpening({ row, error: null });
        memberService.getMemberById(row.id).then((one) => {
            if (ticket !== openTicket.current) return;
            if (one?.id !== row.id) throw new Error("The server answered with another member's record.");
            setOpening(null);
            // The workplace's lineage the answer for one member carries is the Employment tab's own read and never an
            // input, so it is kept off the record: a later edit's save does not send it back (as `rereadMember` does).
            setSelectedMember({ ...one, workplace: undefined });
        }).catch((error) => {
            if (ticket !== openTicket.current) return;
            reportError("Members.openRecord", error);
            setOpening({ row, error });
        });
    }, [keepOpen]);

    const closeRecord = useCallback(() => {
        if (closeTimer.current) return;
        // A record still being read is not opened into a column that has closed.
        openTicket.current += 1;
        setPanelClosing(true);
        closeTimer.current = setTimeout(() => {
            closeTimer.current = null;
            setPanelClosing(false);
            setSelectedMember(null);
            setOpening(null);
        }, PANEL_MS);
    }, []);

    useEffect(() => () => {
        if (closeTimer.current) clearTimeout(closeTimer.current);
    }, []);
    const [isEditing, setIsEditing] = useState(false);
    const [editForm, setEditForm] = useState({});
    // The form as it stood when editing began, serialised: an edit has changes exactly when the form no longer
    // serialises to it. Compared whole, so a field the member record never carried still counts once it is typed in.
    const [editBaseline, setEditBaseline] = useState(null);
    const [activeTab, setActiveTab] = useState("personal");
    const [fieldErrors, setFieldErrors] = useState({});

    /*
     * ✅ THE TWO SILENT `.catch(console.error)` LOADS, REPLACED BY ONE SET (EH-D9).
     *
     * Note what is NOT here: the register's page. The register's own fetch keeps its own
     * `listState`, because a register that failed IS this screen failing -- there is nothing
     * left to look at. A missing bank list is not; the register still renders and the edit
     * panel says which list is missing. That is the line between `error` and `partial`.
     */
    const refs = useReferenceData(REFERENCE_LOADERS);
    const bankNames = refs.data.banks;

    /*
     * 🗣️ THE AREA THE MEMBER WAS RECRUITED IN, AS THE CAPTURE SCREEN NOW SHOWS IT (SASBO, 18 Sep 2026): read off the
     * recruiter -- their demarcation letter -- rather than chosen (`utils/recruitment.js`).
     *
     * 🔑 A STORED AREA WINS. It is a historical fact recorded at capture (V24) and is shown exactly as held. Only a
     * record with none -- every member captured from now on, and the 160 the seed carries -- has it read off their
     * recruiter, with a note saying so.
     *
     * ⚠️ FETCHED ONLY WHILE THE RECRUITMENT TAB IS OPEN, and only when it is needed. Not in `REFERENCE_LOADERS`:
     * that set's failure is the whole page's `partial` notice, and a recruiter list that failed is a fact about one
     * read-only line on one tab -- which is where it is said.
     */
    const recordStoredArea = String(editForm.areaRecruited ?? "").trim();
    const recordRecruiterName = String(editForm.recruitmentRepresentativeName ?? "").trim();
    const recordRecruiters = useAsyncOptions(
        () => recruiterService.getRecruiters(),
        [],
        { enabled: activeTab === "representatives" && !recordStoredArea && Boolean(recordRecruiterName) },
    );
    const recordArea = recordStoredArea
        ? { text: recordStoredArea, note: undefined }
        : recruitedArea({
            channel: editForm.recruitedHow,
            recruiterName: recordRecruiterName,
            recruiters: recordRecruiters.options,
            listState: recordRecruiters.loading ? "loading" : recordRecruiters.error ? "failed" : "ready",
        });

    /*
     * 🗣️ SASBO'S COMMITTEES, as their month-end report prints them -- the Branch Executive Committee a BEC member sits
     * on (V55). Fetched only while one can be chosen: editing, on the Structures tab, with BEC answered Yes. The read
     * side needs no list: the record carries the committee's name.
     */
    const committeeList = useAsyncOptions(
        () => getCommittees().then(committeeOptions),
        [],
        { enabled: isEditing && activeTab === "structures" && editForm.isBec === true },
    );

    /*
     * 🔒 WHO MAY READ THE REGISTER (A10, contracts/permissions.md) -- the server's own rule, said here so the screen asks
     * nothing it would be refused: anyone while sign-in is open and nobody is signed in, and a signed-in user who holds
     * MEMBERS_VIEW. Anyone else is told what is needed (`RequirePermission`), and no request is sent.
     */
    const auth = useAuth();
    const mayRead = auth.can(PERMISSIONS.MEMBERS_VIEW) || (auth.mode === "open" && !auth.user);

    /* The twelve criteria the mock keeps behind "More criteria". Closed at rest. */
    const [moreCriteria, setMoreCriteria] = useState(false);

    /*
     * 🔎 THE TOPBAR'S SEARCH IS THE SHELL'S (card.html's `.search`), and since A10 the register's question is the address
     * bar's `q`: `useRegisterSearch` keeps the two in step. Mounted without the shell -- every test but a few -- there is
     * no box, and the address bar alone asks.
     */
    const { search = "", setSearch } = useOutletContext() || {};
    useRegisterSearch(search, setSearch, view.q, showView);
    const query = view.q;

    /*
     * 📊 THE FIGURE STRIP AND THE BRANCHES, READ FROM THE SERVER SINCE A10 (`GET /api/members/register/overview`).
     *
     * They were counted here from every member the register held, and the decision recorded was not to fetch them: a
     * summary of its own would be one more thing to fail, and a number that could disagree with the rows. A register read
     * a page at a time holds one page, so the server counts them -- over the same members the pages are drawn from (the
     * caller's own region or area), as of SASBO's day -- and they are read when the page opens and again after every save
     * and change of status, so they move with the rows. A figure that was never read is "not available", never a zero.
     */
    const [overview, setOverview] = useState({ state: "loading", figures: NO_FIGURES, branches: [] });
    const loadOverview = useCallback(() => memberService.getRegisterOverview().then(
        (read) => {
            const figures = overviewFigures(read);
            setOverview(figures
                ? { state: "ready", figures, branches: Array.isArray(read.branches) ? read.branches : [] }
                : { state: "error", figures: NO_FIGURES, branches: [] });
        },
        (error) => {
            reportError("Members.overview", error);
            // A refresh that fails keeps the figures it had: they were true when they were read.
            setOverview((shown) => (shown.state === "ready" ? shown : { state: "error", figures: NO_FIGURES, branches: [] }));
        },
    ), []);

    useEffect(() => {
        if (mayRead) loadOverview();
    }, [mayRead, loadOverview]);

    /**
     * A27 — THE THREE STATES, AND A10 KEEPS THEM: a page that has not answered is LOADING, a page that failed is a
     * FAILURE with a retry, and only a page that answered with no rows is EMPTY. A backend that is down never renders as
     * "no members match", which an administrator reads as a fact about the union.
     *
     * 🔑 ONE ANSWER, KEYED BY THE QUESTION IT ANSWERS, as the workplaces' and the job grades' are: a page for another
     * question -- another search, criterion, page, size or order -- is simply not current, so it is not shown and nothing
     * has to be cleared for it to disappear. `attempt` is a retry's: the same question, asked again.
     */
    const question = JSON.stringify(registerQuery(view, askedCriteria));
    const [attempt, setAttempt] = useState(0);
    const [answer, setAnswer] = useState({});
    // The answer and the question as the promises and the save read them, after the render that showed them.
    const answerRef = useRef(answer);
    const questionRef = useRef(question);
    const showViewRef = useRef(showView);
    useEffect(() => {
        questionRef.current = question;
        showViewRef.current = showView;
    });

    /** An answer, kept: a page past the last is answered as the last, and the address bar follows it there. */
    const keepAnswer = useCallback((asking, data, attemptNo) => {
        const answered = JSON.stringify({ ...asking, page: data.page });
        answerRef.current = { question: answered, attempt: attemptNo, data };
        setAnswer(answerRef.current);
        if (data.page !== asking.page) showViewRef.current({ page: data.page }, { replace: true });
    }, []);

    useEffect(() => {
        if (!mayRead) return undefined;
        // Answered already: the page a page past the last landed on is the answer this question now asks.
        if (answerRef.current.question === question && answerRef.current.attempt === attempt) return undefined;
        // THE TICKET: a newer question -- or leaving the page -- retires this one, and its answer is dropped.
        let current = true;
        const asking = JSON.parse(question);
        memberService.searchRegister(asking).then((data) => {
            if (!current) return;
            if (!isRegisterPage(data)) throw new Error("The register answered with something that is not a page of it.");
            keepAnswer(asking, data, attempt);
        }).catch((error) => {
            if (!current) return;
            // Still logged -- the log is what a developer reads. What changed is that the SCREEN knows too.
            reportError("Members.loadRegister", error);
            answerRef.current = { question, attempt, error };
            setAnswer(answerRef.current);
        });
        return () => {
            current = false;
        };
    }, [question, attempt, mayRead, keepAnswer]);

    // A request that has not answered is not an answer (SCREEN-CONTRACT §2): nothing is counted until it has.
    const shownAnswer = answer.question === question ? answer : null;
    const listState = !mayRead ? "refused" : shownAnswer === null ? "loading" : shownAnswer.error ? "error" : "ready";
    const registerPage = shownAnswer?.data ?? null;
    const visibleMembers = registerPage?.content ?? NO_ROWS;

    const retryMembers = useCallback(() => {
        answerRef.current = {};
        setAnswer({});
        setAttempt((n) => n + 1);
        // The figures most likely failed with the page: asked again too. A strip that had its figures keeps them.
        loadOverview();
    }, [loadOverview]);

    /**
     * 🔁 THE PAGE ON SCREEN, ASKED AGAIN -- after a save and after a change of status, ONLY THAT PAGE (A10): its rows
     * stay up while it is asked, the open record and the page are kept, and a member who has left a filter leaves the
     * page. Resolves when the page is in; rejects where it could not be read, for the save to say so (R3-F2).
     */
    const refreshPage = useCallback(() => {
        const asking = JSON.parse(questionRef.current);
        return memberService.searchRegister(asking).then((data) => {
            if (!isRegisterPage(data)) throw new Error("The register answered with something that is not a page of it.");
            keepAnswer(asking, data, answerRef.current.attempt ?? 0);
        });
    }, [keepAnswer]);


    /* Tab changes move in the direction of travel (card.js `enterTab`). */
    const [tabDirection, setTabDirection] = useState("fwd");
    const changeTab = (next) => {
        setTabDirection((TABS.findIndex(t => t.id === next) > TABS.findIndex(t => t.id === activeTab)) ? "fwd" : "back");
        setActiveTab(next);
    };

    /*
     * card.css `.toast`: what a write did, low and centred, gone in 2.6s -- the record's save, and since month end 03.3 a
     * change of status. The sentence is captured when it is said, so it survives the panel closing, and a toast's timer
     * clears only its own sentence, never one said after it.
     */
    const [toast, setToast] = useState("");
    const say = useCallback((sentence) => {
        setToast(sentence);
        window.setTimeout(() => setToast((shown) => (shown === sentence ? "" : shown)), 2600);
    }, []);

    /*
     * 🔁 CHANGE STATUS (month end 03.3) -- the record's one way to move a status, a dialog of its own; the edit form only
     * shows it. `changingStatus` is whether the dialog is open.
     */
    const [changingStatus, setChangingStatus] = useState(false);

    /*
     * 🖨️ WHICH OF THE TWO THINGS ON THIS SCREEN IS BEING PRINTED.
     *
     * Print is live (the owner, 2026-09-09) and this page carries two printable surfaces at
     * once: the register, from Export, and the open member's record, from the panel's Print.
     * `index.css`'s print rules keep everything inside `.print-area` and remove the rest --
     * so exactly one of them may wear the class, and which one depends on the button that
     * was pressed. Without this the register printed the record beside it and the record
     * printed the register behind it.
     *
     * 🔑 `flushSync` BECAUSE `window.print()` DOES NOT WAIT FOR REACT. The dialog opens on
     * the DOM as it is at the moment of the call; a state change scheduled normally lands
     * after the snapshot, so the class would arrive one print too late, every time.
     */
    const [printScope, setPrintScope] = useState(null);
    const printWith = useCallback((scope) => {
        flushSync(() => setPrintScope(scope));
        window.print();
        setPrintScope(null);
    }, []);

    /**
     * 🔁 THE RECORD'S OWN RELOAD (month end 03.3): the answer for this one member replaces the record and its row in the
     * register -- after a change of status, and before the *Change status* dialog says anything of a change whose answer
     * never came (ME-3). Resolves with the member as they now stand, or `null` where the read failed or answered with
     * someone else; the record then shows `status`, when the caller knows one -- the change's own answer, or its refusal's
     * `currentStatus`.
     *
     * The workplace's lineage the answer for one member carries is the Employment tab's own read (`useMemberWorkplace`)
     * and never an input, so it is kept off the record: a later edit's save does not send it back.
     */
    const rereadMember = useCallback((id, status) => memberService.getMemberById(id).then(
        (one) => (one?.id === id ? { ...one, workplace: undefined } : null),
        (error) => {
            reportError("Members.reread", error);
            return null;
        },
    ).then((fresh) => {
        const now = fresh ?? (status ? { status } : null);
        if (now) {
            setSelectedMember((member) => (member?.id === id ? { ...member, ...now } : member));
            // A10: the page on screen and the figures, asked again -- a member who changed status may have left a filter.
            refreshPage().catch((error) => reportError("Members.refreshAfterStatus", error));
            loadOverview();
        }
        return fresh;
    }), [refreshPage, loadOverview]);

    const tabsWithErrors = useMemo(() => {
        const errorTabs = new Set();
        Object.keys(fieldErrors).forEach((field) => {
            const mappedTab = FIELD_TO_TAB_MAP[field];
            if (mappedTab) errorTabs.add(mappedTab);
        });
        return errorTabs;
    }, [fieldErrors]);

    const titleOptions = TITLE_OPTIONS;
    const idTypeOptions = IDENTITY_TYPE_OPTIONS;
    const raceOptions = RACE_OPTIONS;
    const paymentTypeOptions = PAYMENT_TYPE_OPTIONS;
    const statusOptions = STATUS_OPTIONS;
    const bankOptions = bankNames;
    // The same branch rule as Capture Member: the bank's own branches together with the fourteen names. One rule on
    // both screens -- two screens holding one member with two vocabularies is how A6 and A7 happened.
    const bankListing = refs.data['bank branches'];
    const branchOptionsFor = useCallback(
        (bankName) => (bankName ? [...new Set([...branchNamesForBank(bankListing, bankName), ...BRANCH_OPTIONS])] : BRANCH_OPTIONS),
        [bankListing],
    );
    const branchOptions = branchOptionsFor(editForm.bankName);

    /*
     * A new bank and what goes with the old one -- capture's rule (`settleBankChange`), whether a person chose it
     * (`handleEditChange`) or accepted the Workplace control's offer (01.3), here where it can be told apart from a
     * record loading. The fourteen names stay for every bank (`branchOptionsFor`).
     */
    const settleBank = useCallback((prev, next) => settleBankChange(prev, next, branchOptionsFor), [branchOptionsFor]);

    /*
     * 🏢 THE MEMBER'S WORKPLACE (month end 01.3), on the Employment tab: read from the answer for this one member, which
     * alone carries the area and the committee (`useMemberWorkplace`), and changed while editing with capture's control
     * and capture's rule (`useWorkplaceChoice`). SASBO's lists are asked for when the tab first shows the control --
     * to edit, or to say where a workplace on record came from -- and kept for the visit (MD69).
     */
    const workplaceSource = useWorkplaceSource(activeTab === "employment" && (isEditing || selectedMember?.workplaceId != null));
    const workplace = useWorkplaceChoice({
        form: editForm,
        setForm: setEditForm,
        setFieldErrors,
        banks: bankOptions,
        branchesFor: branchOptionsFor,
        named: true,
        settle: settleBank,
    });
    const memberWorkplace = useMemberWorkplace(selectedMember, activeTab === "employment" && !workplace.workplace);
    const genderOptions = GENDER_OPTIONS;
    /*
     * ✅ A25's SECOND CLAUSE, `06.1b` -- the other of the two callers. Same reasoning as the
     * one written out at `CaptureMember.jsx`: `getByBank` no longer swallows a failure into
     * the (empty) static list, so this panel renders the failure with a retry instead of
     * telling an administrator that the member's bank has no job grades on file.
     */
    const [jobGradeAttempt, setJobGradeAttempt] = useState(0);
    /*
     * 🔴 ONE RESULT, KEYED BY THE QUESTION IT ANSWERS, AND SHOWN ONLY WHILE IT IS THE CURRENT ONE (`10.4`).
     *
     * The grades and their failure used to be two pieces of state that the fetch effect RESET on the way in
     * (`setJobGradeOptions([])`, `setJobGradeError(null)`) -- which `react-hooks/set-state-in-effect` makes an
     * error this phase. Keying the result by `[bank, attempt]` removes every one of those resets, because a
     * result for another bank or another attempt is simply not current, so it is not shown. Nothing has to be
     * cleared for it to disappear.
     *
     * 🔑 AND IT CLOSES A GAP R3-F4 LEFT OPEN. The resets only ran when the NEXT answer landed, so switching
     * ABSA -> Capitec while editing left ABSA's grades on offer under "Capitec" for as long as Capitec took to
     * answer -- one click from saving a grade against the wrong bank, which is the very thing R3-F4's ticket
     * was added to prevent, reached by waiting rather than by racing. Here the old bank's list stops being
     * current the instant the bank changes. `Members.jobGradeSequencing.test.jsx` ③ was red on that before
     * this change.
     *
     * Not editing, or no bank: no key, no grades -- grades are per bank, so an empty list is the honest answer.
     */
    const [jobGradeResult, setJobGradeResult] = useState(null);
    const jobGradeKey = isEditing && editForm.bankName
        ? JSON.stringify([editForm.bankName, jobGradeAttempt])
        : null;
    const currentJobGrades = jobGradeResult?.key === jobGradeKey ? jobGradeResult : null;
    const jobGrade = currentJobGrades?.options ?? NO_JOB_GRADES;
    const jobGradeError = currentJobGrades?.error ?? null;

    useEffect(() => {
        const onKeyDown = (e) => {
            if (e.key !== "Escape") return;
            // An Escape something nearer has already answered -- the date picker, a
            // dialog, a listbox -- is not a request to close the whole record. Without this
            // guard, Escape in the calendar of the edit form threw the member away.
            if (e.defaultPrevented || e.target?.closest?.('[role="dialog"], [role="listbox"]')) return;
            // `06.5`: through the same guard as every other way out -- an edit with changes asks first.
            // `leaveRef` is assigned on every commit, before any key can be pressed, so there is no second path.
            leaveRef.current?.();
        };
        window.addEventListener("keydown", onKeyDown);
        return () => window.removeEventListener("keydown", onKeyDown);
    }, []);

    /**
     * 🔴 R4-F1, the edit twin of Capture Member's: Initials are derived from First Name(s) until the administrator
     * types them. Reset whenever an edit BEGINS (`handleEditToggle`), so one member's typed initials do not stop the
     * next member's from deriving.
     *
     * ♻️ IT USED TO BE RESET ON RECORD LOAD AS WELL, AND THAT RESET HAD NO CONSEQUENCE. The flag is read only while
     * editing, every way into editing passes through `handleEditToggle`, and no path opens a different member in the
     * middle of an edit -- opening one ends the edit first, and a save ends it itself. A deletion proof in `10.4`
     * confirmed it (removed, `Members.recordSeed.test.jsx` ③ stayed green), and P10's refactor pass removed it.
     */
    const initialsTyped = useRef(false);

    /*
     * 🔴 THE RECORD RESEEDS DURING RENDER NOW, AND THE REF THAT REMEMBERED THE LAST MEMBER IS STATE (`10.4`).
     *
     * This was one effect keyed on `selectedMember` -- an error under `react-hooks/set-state-in-effect` this
     * phase -- and it could not simply be lifted into render, because it also WROTE two refs, and a ref written
     * during render is its own violation. So it splits:
     *
     *   - the state it seeds (form, section, errors, edit mode) moves into a render-phase update guarded by
     *     the last member seen, React's documented form, which also means a newly opened record never paints
     *     one frame with the previous member's form;
     *   - `prevMemberIdRef` is not moved but REPLACED: the last member seen is exactly what it held;
     *   - the `initialsTyped` reset that was also here turned out to be dead weight -- see `initialsTyped`.
     *
     * ⚠️ THE SECTION RESETS BY MEMBERSHIP NUMBER, NOT BY OBJECT, and that has to survive: a save replaces the
     * record with a new object carrying the same number, and comparing identities would throw the user back to
     * Personal after every save. `Members.recordSeed.test.jsx` ② is the must-pass that says so.
     */
    const [seenMember, setSeenMember] = useState(null);
    if (selectedMember !== seenMember) {
        const previousNumber = seenMember?.membershipNumber ?? null;
        setSeenMember(selectedMember);
        if (selectedMember) {
            // List fields arrive as lists: memberService.normalizeMember unfolds the server's
            // "PEC,CEC" once, for every consumer (A37 / A38).
            setEditForm({ ...selectedMember, paymentType: normalizePaymentType(selectedMember.paymentType) });
            if (previousNumber !== selectedMember.membershipNumber) setActiveTab("personal");
            setFieldErrors({});
        } else {
            setIsEditing(false);
            setEditForm({});
            setFieldErrors({});
        }
    }

    useEffect(() => {
        /*
         * 🔴 THE GRADE IS NOT CLEARED HERE, AND MAIN'S e56dbe4 CLEARED IT HERE. An effect on
         * `editForm.bankName` cannot tell a user changing the bank from the form being LOADED
         * with a member -- opening a record copies the member in, `bankName` goes from nothing
         * to their bank, and the reset blanked the grade the record panel then displayed as
         * "—" for every member. The clear lives in `handleEditChange` now, on the bank field's
         * own change, which only a person can cause. Proved by `Members.jobGrade.test.jsx`:
         * ① reddened on the merged code, and ③ holds that a real change of bank still clears it.
         */
        /*
         * 🔴 R3-F4: ONLY WHILE EDITING, AND ONLY THE LATEST ANSWER COUNTS.
         *
         * The list is offered by the edit form alone -- the read view prints `editForm.jobGrade` directly and never
         * touches these options -- so fetching on every OPEN spent two requests per member looked at, on the screen
         * used most, for something nobody could see. And the fetch had no ticket: switching bank A → B with A's
         * answer arriving last put A's grades under bank B, where one of them could then be saved.
         */
        if (jobGradeKey === null) return undefined;

        let superseded = false;
        jobGradeService.getByBank(editForm.bankName)
            .then((grades) => {
                if (superseded) return;
                setJobGradeResult({ key: jobGradeKey, options: Array.isArray(grades) ? grades : [], error: null });
            })
            .catch((error) => {
                // An abort is the caller superseding itself, not a failure (§16, EH-D3).
                if (superseded || error?.kind === ERROR_KINDS.ABORT) return;
                setJobGradeResult({ key: jobGradeKey, options: [], error });
            });
        return () => {
            superseded = true;
        };
    }, [jobGradeKey, editForm.bankName]);


    const handleEditToggle = () => {
        if (isEditing) {
            // Cancel is the one way out that is itself the discard, so it does not ask; it does put the record's own
            // values back, so the page read after it is the record rather than the edit that was thrown away.
            endEdit();
            return;
        }
        const start = { ...selectedMember, paymentType: normalizePaymentType(selectedMember?.paymentType) };
        initialsTyped.current = false;
        // Nothing in a new edit was filled by a workplace -- a mark left from another member's edit must not follow.
        workplace.reset();
        setEditForm(start);
        setEditBaseline(JSON.stringify(start));
        setFieldErrors({});
        setIsEditing(true);
    };

    /**
     * 🗣️ A NAME STARTS WITH A CAPITAL (SASBO, 18 Sep 2026) -- tidied as the field is LEFT, where the change can be seen,
     * by the rule both member screens share (`finishField`, utils/inputRules.js). Only first letters are ever raised.
     * No new object when nothing changed, so leaving an untouched field does not count as an edit.
     */
    const handleEditBlur = useCallback((field) => () => {
        setEditForm((prev) => {
            const finished = finishField(field, prev[field]);
            return finished === prev[field] ? prev : { ...prev, [field]: finished };
        });
    }, []);

    const handleEditChange = useCallback((field) => (event) => {
        let rawValue = fieldValue(event);
        if (field === "initials") initialsTyped.current = true;

        const hasNonNumericInput =
            field === "identityNumber" && editForm.identityType === "I.D. Number" &&
            /\D/.test(rawValue);
        // One typing rule for both member screens (utils/inputRules.js).
        const value = sanitiseField(field, rawValue, { identityType: editForm.identityType });

        setEditForm((prev) => {
            let next = { ...prev, [field]: value };

            // A grade belongs to a bank, and so may a branch -- `settleBank`, the rule the workplace's offer shares.
            if (field === "bankName") next = settleBank(prev, next);

            // R4-F1: derived until typed -- see `initialsTyped`.
            if (field === "firstNames" && !initialsTyped.current) {
                next.initials = value
                    .trim()
                    .split(/\s+/)
                    .map(name => name.charAt(0).toUpperCase())
                    .join("");
            }
            if (field === "identityNumber" && value.length === 13 && prev.identityType === "I.D. Number") {
                next.dateOfBirth = extractDateOfBirthFromSAID(value) || next.dateOfBirth;
                next.gender = extractGenderFromSAID(value) || next.gender;
            }
            if (field === "identityType") {
                next.dateOfBirth = "";
                next.gender = "";
            }
            return next;
        });

        setFieldErrors(prev => {
            const newErrors = { ...prev };
            delete newErrors[field];

            if (field === "identityType") {
                delete newErrors.identityNumber;
                delete newErrors.dateOfBirth;
            }
            if (field === "paymentType" && value !== "Debit Order") {
                delete newErrors.bankAccountNumber;
                delete newErrors.accountType;
                delete newErrors.bankName;
                delete newErrors.branch;
            }
            if (field === "identityNumber" && editForm.identityType === "I.D. Number") {
                const error = hasNonNumericInput ? "Only numbers are allowed for I.D. Number" : validateSAID(value);
                if (error) newErrors[field] = error;
            }
            if (field === "personalEmail" || field === "workEmail") {
                const error = validateEmail(value);
                if (error) newErrors[field] = error;
            }
            return newErrors;
        });
    }, [editForm.identityType, settleBank]);

    /**
     * One Structures answer changed. `withStructureAnswer` clears what a No leaves behind -- the rule the server holds
     * too -- and the errors that answer settles go with it. `extra` rides along: the committee's name beside its code.
     */
    const setStructure = useCallback((field, value, extra = null) => {
        setEditForm((prev) => ({ ...withStructureAnswer(prev, field, value), ...extra }));
        setFieldErrors((prev) => {
            const next = { ...prev };
            for (const key of [field, ...(STRUCTURE_ERRORS_SETTLED_BY[field] ?? [])]) delete next[key];
            return next;
        });
    }, []);

    const validateEditForm = () => {
        const errors = {};
        if (!editForm.firstNames?.trim()) errors.firstNames = "First Name(s) field is required";
        if (!editForm.surname?.trim()) errors.surname = "Surname field is required";
        if (!editForm.identityType) errors.identityType = "Identity Type selection is required";

        if (!editForm.identityNumber?.trim()) {
            errors.identityNumber = "Identity Number field is required";
        } else if (editForm.identityType === "I.D. Number" && editForm.identityNumber.trim().length !== 13) {
            errors.identityNumber = "Identity Number must be exactly 13 digits when I.D. Number is selected";
        }

        if (editForm.identityType === IDENTITY_TYPE_PASSPORT && !editForm.dateOfBirth) {
            errors.dateOfBirth = "Date of Birth is strictly required when Passport is selected";
        }

        if (editForm.paymentType === "Debit Order") {
            if (!editForm.bankAccountNumber?.trim()) errors.bankAccountNumber = "Bank Account Number is required for Debit Orders";
            if (!editForm.bankName?.trim()) errors.bankName = "Bank Employer alignment name is required for Debit Orders";
            if (!editForm.branch?.trim()) errors.branch = "Bank Branch selection is required for Debit Orders";
        }

        // Shape checks on what the administrator CHANGED in this edit, and only on fields this screen shows. A stored
        // value nobody touched is left alone: rules written today would otherwise make older members unsaveable over a
        // field on another tab. The emails have no control on Edit Member, and Bank Account Number is hidden under
        // Payroll, so neither can block a save the administrator has no way to fix.
        const original = editBaseline ? JSON.parse(editBaseline) : {};
        const shapeChecked = ["initials", "phone", "workTel", "homeTel", "ibtNo", "dateOfBirth"];
        if (editForm.paymentType === "Debit Order") shapeChecked.push("bankAccountNumber");
        for (const key of shapeChecked) {
            if (errors[key] || String(editForm[key] ?? "") === String(original[key] ?? "")) continue;
            const message = validateField(key, editForm[key], { identityType: editForm.identityType });
            if (message) errors[key] = message;
        }

        // V55: what each structure's Yes needs beside it, by the rule the server holds (`utils/structures.js`) -- a
        // legacy Yes with nothing beside it stays saveable, against the record as it stood when editing began.
        // No COSATU-requires-BEC check here: the form cannot express the contradiction (COSATU is only
        // offered under BEC = Yes, and BEC = No clears it), and a legacy member stored as COSATU with no BEC
        // answer must stay saveable unchanged -- the server's validateStructures owns both halves.
        Object.assign(errors, structureErrors(editForm, original));
        return errors;
    };

    /**
     * What the last save attempt said, when it was not a validation failure.
     *
     * 🔴 IT WAS THREE `alert()` CALLS. A native modal cannot be styled, blocks the thread,
     * names no field, and is SUPPRESSIBLE — a member who ticks "don't show me these again"
     * silently loses every confirmation in the application. It also arrives and leaves,
     * so after dismissing "please fix the following validation errors" the form looked
     * exactly as it had, with nothing on screen saying what was wrong.
     */
    const [saveNotice, setSaveNotice] = useState(null);

    const handleSaveChanges = async () => {
        const clientSideErrors = validateEditForm();
        const mergedErrors = { ...fieldErrors, ...clientSideErrors };

        if (Object.keys(mergedErrors).length > 0) {
            setFieldErrors(mergedErrors);
            const dynamicFields = Object.keys(mergedErrors);
            setActiveTab(FIELD_TO_TAB_MAP[dynamicFields[0]] || "personal");
            // The summary is rendered in the dialog and focused, so the member is told
            // where the problem is AND left looking at it. The tab switch above is what
            // makes that true for a form whose fields are behind five tabs.
            setSaveNotice({ kind: "invalid", messages: Object.values(mergedErrors) });
            return;
        }

        const changes = getChangedFields(editForm, selectedMember);
        if (changes.length === 0) {
            setSaveNotice({ kind: "unchanged", messages: ["Nothing on this record has changed, so there is nothing to save."] });
            setIsEditing(false);
            return;
        }

        setSaveNotice(null);

        // 🔴 `03.4`: was `window.confirm()`. It listed the changes in a browser dialog that
        // cannot be styled, cannot be scrolled sensibly, blocks the whole thread, and is
        // SUPPRESSIBLE -- a user who ticks "don't show me these again" silently loses this
        // confirmation and every other one in the application.
        setPendingSave(changes);
    };

    /*
     * 🔴 THE CONFIRMATION CLOSED BEFORE THE WRITE, AND NOTHING SHOWED WHILE IT RAN (`06.5`, F-P55). "Save changes"
     * dismissed the dialog first and awaited the request after, so for the length of a slow save the record looked
     * exactly as if nothing had been pressed -- and could be pressed again. The dialog stays open, its button busy,
     * until the write has answered either way.
     */
    const [savingRecord, setSavingRecord] = useState(false);

    const commitSave = async () => {
        if (savingRecord) return;
        setSavingRecord(true);
        let saved = null;
        // ONE exit, whatever happens below: the write's own `catch` returns early, and the refresh that follows
        // has a `try` of its own, so a third path added later would otherwise leave the record busy for ever.
        try {
            try {
                /*
                 * 🏢 01.3: THE SAVE NAMES A WORKPLACE BY ITS ID, AND ONLY WHEN THIS EDIT CHANGED IT. A save that names none
                 * leaves the member's workplace as it was (MD58), so an edit of a telephone number cannot be refused over a
                 * workplace nobody touched -- say, where the deployment's switch was set back since the register loaded.
                 * The two scalars the list carries are answers, not inputs, and are not sent back.
                 *
                 * 🔁 03.3: AND IT NEVER NAMES THE STATUS. A status is changed through *Change status*, with SASBO's code
                 * for it; here it is only shown. A save that names none leaves it as it is on the server, so an edit of a
                 * telephone number can neither put back a status somebody changed since the record was read, nor be
                 * refused over one nobody touched once the `PUT` stops changing status (03.4).
                 */
                /*
                 * 🔁 V68: AND IT NAMES THE VERSION OF THE RECORD IT READ (`editForm.version`, from the record's own read),
                 * so the server refuses it -- MEMBER_CHANGED_SINCE -- where somebody has saved the record since. The later
                 * of two edits used to undo the earlier one without a word.
                 */
                saved = await memberService.updateMember({
                    ...editForm,
                    status: undefined,
                    workplaceName: undefined,
                    workplaceRaNumber: undefined,
                    workplaceId: editForm.workplaceId === selectedMember.workplaceId ? undefined : editForm.workplaceId,
                });
            } catch (error) {
                reportError("Members.save", error);
                // 06.1 owns the shared error CONTRACT; what this wave owes the screen is that
                // a failed save is visible on the screen the member is looking at, rather than
                // in a dialog they dismiss and a console they never open.
                // 🔴 R3-F2: "Nothing was written" only after a refusal. A timeout, a lost connection or a 5xx may have saved
                // the change, and a clerk told otherwise redoes it.
                setPendingSave(null);
                if (writeMayHaveLanded(error)) {
                    setSaveNotice({
                        kind: "unconfirmed",
                        messages: ["We did not get an answer about these changes, so they may have been saved. Reload the register and check this member before trying again."],
                    });
                    return;
                }
                /*
                 * 🔴 A REFUSED EDIT HID ITS REASON (the manual stream, 7 Oct): every refusal but one was "We could not save
                 * these changes. Nothing was written — please try again." -- while the server had said why, and trying
                 * again only met the same refusal. The server's sentence is what the clerk reads now (ME-13), the fields
                 * it names are marked, and the edit is kept exactly as typed.
                 */
                const code = error?.body?.code;
                const reason = messageOf(error);
                if (code === MEMBER_CHANGED_SINCE) {
                    // V68: somebody saved the record after it was read -- said, and the record offered again.
                    setSaveNotice({ kind: "changed", messages: [reason] });
                    return;
                }
                // 01.3: a workplace that is not on record here is said AT the Workplace field, in the server's words.
                const named = code === WORKPLACE_UNKNOWN ? { workplaceId: reason } : validationErrorsOf(error);
                const fields = Object.keys(named);
                if (fields.length > 0) {
                    setFieldErrors((prev) => ({ ...prev, ...named }));
                    setActiveTab(FIELD_TO_TAB_MAP[fields[0]] || (code === WORKPLACE_UNKNOWN ? "employment" : activeTab));
                }
                setSaveNotice({ kind: "failed", messages: [`${reason} Nothing was written.`] });
                return;
            }

            // The save landed. What follows is the register catching up, and its failure is not the save's (R3-F2): it
            // shared the write's `try`, so a good save followed by a failed refresh was reported as not written.
            setPendingSave(null);
            // V68: the record holds the version this save wrote, so the next edit is not refused over this one.
            setSelectedMember({ ...editForm, version: saved?.id === editForm.id ? saved.version : editForm.version });
            setEditBaseline(null);
            setIsEditing(false);
            const savedName = personName(editForm);
            say(savedName ? `${savedName}’s record saved` : "Changes saved");
            // A10: the page on screen alone is asked again, and the figures with it -- the record stays open on it.
            loadOverview();
            try {
                await refreshPage();
            } catch (error) {
                reportError("Members.refreshAfterSave", error);
                setSaveNotice({
                    kind: "stale",
                    messages: ["The changes were saved, but the register could not be refreshed, so its rows may show the old values until the page is reloaded."],
                });
            }
        } finally {
            setSavingRecord(false);
        }
    };

    const [pendingSave, setPendingSave] = useState(null);

    /*
     * 🔴 AN EDIT WAS THROWN AWAY BY ANYTHING THAT CHANGED THE RECORD (`06.5`, the `06.3` critique, F-P55). Choosing
     * another member, the close button, the scrim and Escape each replaced or cleared the record while the form held
     * unsaved changes -- without a word -- and the next member opened already in edit mode, because nothing reset
     * `isEditing`. Every one of those ways out comes through here now: an edit with changes asks first, an edit
     * without them simply ends, and the record that opens next opens to be read.
     */
    const [pendingLeave, setPendingLeave] = useState(null);
    // The ways out read the record as of the last commit, and ask whether it changed only when one is taken --
    // `recordIsDirty`, below the component. Serialising the whole form on every render to fill a ref nothing
    // renders was work every keystroke paid for.
    const guard = useRef({ isEditing: false, selectedId: null, baseline: null, editForm: null });

    const endEdit = useCallback(() => {
        const { baseline } = guard.current;
        if (baseline) setEditForm(JSON.parse(baseline));
        setEditBaseline(null);
        setIsEditing(false);
        setSaveNotice(null);
        setFieldErrors({});
    }, []);

    /**
     * 🔁 V68: THE RECORD SOMEBODY SAVED SINCE, READ AGAIN -- the refused edit set aside (nothing of it was written) and
     * the record shown as it now stands, to be edited again; the page and the figures follow (`rereadMember`).
     */
    const reloadChangedRecord = useCallback(() => {
        const id = guard.current.selectedId;
        endEdit();
        if (id != null) rereadMember(id);
    }, [endEdit, rereadMember]);

    const requestOpenRecord = useCallback((row) => {
        const { isEditing: editing, selectedId } = guard.current;
        // The member already open, chosen again: nothing to read and nothing to throw away -- and a record another
        // click was still reading is not opened over the one chosen last.
        if (row?.id != null && row.id === selectedId) {
            if (!editing) {
                openTicket.current += 1;
                setOpening(null);
                keepOpen();
            }
            return;
        }
        if (recordIsDirty(guard.current)) {
            setPendingLeave({ member: row });
            return;
        }
        endEdit();
        loadRecord(row);
    }, [keepOpen, loadRecord, endEdit, setPendingLeave]);

    const requestCloseRecord = useCallback(() => {
        if (recordIsDirty(guard.current)) {
            setPendingLeave({ close: true });
            return;
        }
        endEdit();
        closeRecord();
    }, [closeRecord, endEdit, setPendingLeave]);

    useEffect(() => {
        guard.current = { isEditing, selectedId: selectedMember?.id ?? null, baseline: editBaseline, editForm };
        leaveRef.current = requestCloseRecord;
    });

    const discardAndLeave = () => {
        const leave = pendingLeave;
        setPendingLeave(null);
        endEdit();
        if (leave?.member) loadRecord(leave.member);
        else closeRecord();
    };

    // The save summary takes focus once it exists, and again on every fresh notice -- `useFocusOn` says why.
    const errorSummaryRef = useFocusOn(saveNotice);

    /** A criterion of the criteria row -- the bank, the branch, the status -- in the address bar, from the first page. */
    const handleViewCriterion = useCallback((field) => (event) => {
        showView({ [field]: fieldValue(event), page: 0 });
    }, [showView]);

    /**
     * A criterion behind "More criteria": asked once typing pauses, or at once where it is picked from a list -- and from
     * the first page, as every change of question is.
     */
    const handleInputChange = useCallback((field) => (event) => {
        setCriteria((prev) => ({ ...prev, [field]: fieldValue(event) }));
        if (viewRef.current.page !== 0) showView({ page: 0 }, { replace: true });
    }, [showView]);

    /** A heading pressed: its column, A to Z first and Z to A on a second press -- from the first page. */
    const handleSort = useCallback((key) => {
        showView({ sort: nextSort(viewRef.current.sort, key), page: 0 });
    }, [showView]);

    /**
     * 🔴 THE PAGE IS THE SERVER'S ANSWER, NEVER A GUESS (A10). The client clamped a page it had computed from every
     * member it held -- once with an effect that drew "No member matches" over members for a frame (`10.4`), then
     * during render. The server answers the page asked for, or its last page where the list has shrunk past it, and
     * says which: `keepAnswer` moves the address bar there. So no frame shows a page out of range, and a page asked
     * past the end lands where the list ends.
     */
    const pageSize = registerPage?.size ?? view.size;
    const page = (registerPage?.page ?? view.page) + 1;
    const totalPages = Math.max(registerPage?.totalPages ?? 1, 1);
    const totalMembers = registerPage?.totalElements ?? 0;
    const startIndex = (page - 1) * pageSize;
    const endIndex = startIndex + visibleMembers.length;

    const handlePageChange = useCallback((next) => {
        showView({ page: next - 1 });
    }, [showView]);

    const handlePageSize = useCallback((size) => {
        showView({ size: Number(size), page: 0 });
    }, [showView]);

    /* The rows re-enter when the question changes: a new search, a new criterion, a new page, size or order. */
    const enterKey = question;

    /*
     * How many of the COLLAPSED criteria are actually filtering.
     *
     * 🔴 WITHOUT THIS THE DISCLOSURE IS A TRAP. Type a surname, collapse the panel, and the
     * register goes on showing a filtered list with nothing on screen to say why -- which is
     * the same class of defect as a failed fetch rendering as an empty state: the user reads
     * a consequence as a fact. The count on the control is what makes a hidden criterion
     * visible.
     */
    const activeExtraCriteria = useMemo(
        () => Object.values(criteria).filter((value) => String(value ?? "").trim() !== "").length,
        [criteria],
    );

    /*
     * The Branch criterion's choices: every branch the register's members are at, once, in order -- the server's list
     * (`overview.branches`), so it never offers a branch with no members; and the one in the address bar, should a link
     * name one the list does not hold.
     */
    const registerBranches = useMemo(
        () => [...new Set([...overview.branches, view.branch].filter(Boolean))].sort((a, b) => a.localeCompare(b)),
        [overview.branches, view.branch],
    );

    // 🗣️ SASBO, 18 Sep 2026: the month's new members, and how many of them pay by debit order -- counted by the server
    // now, over the register (`overview`), by the member's own Date Joined in SASBO's calendar month.
    const registerFigures = overview.figures;

    /** Is anything narrowing the register -- the search, a criterion of the row, or one behind "More criteria"? */
    const narrowed = Boolean(query || view.status || view.bank || view.branch || activeExtraCriteria > 0);

    const handleClear = () => {
        setCriteria(NO_CRITERIA);
        setSearch?.("");
        showView({ q: "", status: "", bank: "", branch: "", page: 0 });
    };

    /**
     * The register's columns. R-RESP-K: every column declares what it is worth, so a phone
     * shows three real columns and puts the rest in the row's detail panel instead of
     * crushing nine into 390px.
     *
     * 🔴 THE ROW IS NOT CLICKABLE ANY MORE, AND THAT WAS A REAL DEFECT. It was
     * `<tr onClick>` — no tab stop, no accessible name, no Enter, so the only way to open a
     * member record was a mouse. `Table` offers no `onRowClick` at all, deliberately, so
     * the action is a real `<button>` in the row: the membership number itself, which is
     * the thing a user is looking at when they decide to open it.
     */
    /* card.html's six columns, in its order and its words, with its column ladder: the
       employee number yields first, then the branch, then the bank; the number, the member
       and the status never do. */
    const columns = useMemo(() => [
        {
            key: "membershipNumber",
            header: "Membership no.",
            sortKey: "membershipNumber",
            priority: "essential",
            cell: (member) => {
                const number = member.membershipNumber || "—";
                const who = formalName(member);
                const cancelled = CANCELLED_STATUSES.some((s) => s.toLowerCase() === String(member.status ?? "").toLowerCase());
                return (
                    <button
                        type="button"
                        data-row-action=""
                        onClick={() => requestOpenRecord(member)}
                        // The number alone is not a name a screen-reader user can act on:
                        // "1002" says nothing about what pressing it does, and every row
                        // has one.
                        //
                        // ⚠️ `aria-label` RATHER THAN A VISUALLY-HIDDEN SPAN, and the
                        // difference is not cosmetic. A hidden span is real text content,
                        // so the member's surname then appears TWICE in the row -- once in
                        // the name column and once inside this button -- and every
                        // `getByText(surname)` in the suite starts finding two nodes. It
                        // begins as a test annoyance and ends as a page where "find the
                        // name" is ambiguous. `aria-label` is not text content.
                        //
                        // It OPENS with the visible number, which WCAG 2.5.3 requires: an
                        // accessible name that does not contain the visible label breaks
                        // speech control ("press 1002").
                        aria-label={who ? `${number} — open the record for ${who}` : `${number} — open this member's record`}
                        className={cn(
                            "touch-target rounded-sm tabular-nums focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring",
                            // card.css: "nothing disappears, it cancels" -- a resigned or retired
                            // member's number is struck through, not hidden.
                            cancelled
                                ? "font-medium text-ink-muted line-through decoration-status-resigned-dot decoration-[1.5px]"
                                : "font-semibold tracking-[0.06em] text-brand-ink",
                        )}
                    >
                        {number}
                    </button>
                );
            },
        },
        {
            key: "employeeNumber",
            header: "Employee no.",
            priority: "secondary",
            fit: "wide",
            cell: (m) => <span className="tabular-nums text-ink-muted">{m.employeeNumber || "—"}</span>,
        },
        {
            key: "member",
            header: "Member",
            // Sorted by surname, then first names: the register's own order (A10).
            sortKey: "surname",
            priority: "essential",
            // The two-line member cell -- `Ms NT Mokoena` over `Nomvula Thandeka`. Both lines
            // truncate at the same width, and the full names are one click away on the card.
            // 13rem, not 14: the cap is what the column ladder must fit (index.css `.register`),
            // and at 14rem a real long name put Status past the sheet with the record docked at 1440.
            cell: (m) => (
                <span className="block max-w-[13rem]">
                    <span className="block truncate text-base font-semibold leading-tight text-ink">
                        {formalName(m) || "—"}
                    </span>
                    {m.firstNames && <span className="block truncate text-xs text-ink-muted">{m.firstNames}</span>}
                </span>
            ),
        },
        {
            key: "bankName",
            header: "Bank",
            sortKey: "bank",
            priority: "secondary",
            fit: "narrow",
            // Real bank names run to "The Standard Bank of South Africa Limited"; the mock's
            // fit with "Standard Bank". Truncated here, whole in the cell's title, the
            // detail panel and the card.
            truncate: true,
            // 🔴 THE CAP IS FLUID, IN THE REGISTER'S OWN UNITS. A fixed 10rem was measured at
            // 1349 (the register 605px, four columns fitting in 598) and then conceded that
            // width everywhere else: at 1440 the register is 680 and four names still
            // truncated with ~20px going spare. `cqw` reads the `.register` container the
            // column ladder already measures, so the cap grows with the space.
            // ⚠️ 9rem / 21cqw / 16rem since `06.3`, measured on a row at every cap: at 10rem /
            // 26cqw a real long name and bank put Status past the sheet with the record docked
            // at 1440, where the owner's ruling keeps four columns (index.css `.register`).
            className: "max-w-[clamp(9rem,21cqw,16rem)]",
            cell: (m) => m.bankName || "—",
        },
        {
            key: "branch",
            header: "Branch",
            sortKey: "branch",
            priority: "secondary",
            fit: "medium",
            // Capped like Bank, and for Bank's reason: an uncapped branch sets the width the column
            // ladder has to fit, so one real branch longer than the synthetic data's longest would
            // move the width at which Status is cut. Truncated here and whole in the cell's title --
            // which Table writes only for a STRING cell, so the muted ink moves to the cell's class.
            truncate: true,
            className: "text-ink-muted max-w-[clamp(9rem,22cqw,15rem)]",
            cell: (m) => m.branch || "—",
        },
        {
            key: "status",
            header: "Status",
            sortKey: "status",
            priority: "essential",
            cell: (member) => <StatusChip status={member.status} />,
        },
    // `requestOpenRecord` reads the edit through a ref and depends only on stable callbacks, so this list is still
    // stable and the columns are still built once -- it is named because the rule is right: a closure that is not
    // declared is a closure nobody will remember to check when it stops being stable.
    ], [requestOpenRecord]);

    /*
     * 🔒 A SIGNED-IN USER WITHOUT MEMBERS_VIEW, or nobody while sign-in is required (A10): the page says what is needed
     * and asks the server nothing -- `RequirePermission`'s notice, with the way to sign in where nobody is.
     */
    if (!mayRead) {
        return (
            <div className="flex min-h-0 flex-1 flex-col text-ink">
                <div className={cn(PAGE_GUTTER, "pt-[26px]")}>
                    <PageTitle title="Members" subtitle="The membership register across every bank." />
                    <div className="mt-5">
                        <RequirePermission permission={PERMISSIONS.MEMBERS_VIEW} />
                    </div>
                </div>
            </div>
        );
    }

    return (
        /* 📐 card.html's `.main`, section by section: the page head, the ruled figure strip,
           the criteria row, and the register as a sheet that fills whatever height is left so
           its pager sits at the foot. Each carries the mock's 28px gutter. The record panel
           is the SHELL's third column -- `MemberPanel` portals into it -- so nothing here has
           to make room for it, and below `xl` it is a drawer over this. */
        <div className="flex min-h-0 flex-1 flex-col text-ink">
            <div className={cn(PAGE_GUTTER, "pt-[26px]")}>
                <PageTitle
                    title="Members"
                    subtitle="The membership register across every bank. Select a member to open their card."
                    actions={
                        <>
                            {/* 🖨️ Export prints the register -- and the print dialog is where a
                                PDF comes from. Print is live (the owner, 2026-09-09); an
                                endpoint that writes a spreadsheet does not exist yet, and a
                                control that promised one would be a lie in a primary position. */}
                            <Button onClick={() => printWith("register")} title="Print the register, or save it as a PDF">
                                <Printer size={16} strokeWidth={1.75} aria-hidden="true" /> Print
                            </Button>
                            <Button variant="primary" onClick={() => navigate("/members/capture")}>
                                <LucideUserRoundPlus size={16} strokeWidth={1.75} aria-hidden="true" /> Capture Member
                            </Button>
                        </>
                    }
                />
            </div>

            {/* 📊 THE RULED FIGURE STRIP -- "ledger line, not tiles", and the contract
                refuses the alternative by name ("hero-metric tiles, dashboards of same-size
                cards"). Counted by the server over the caller's register since A10, read with
                the page and again after every save; "not available" until it has answered. */}
            <FigureStrip className="mx-7 mt-5 max-lg:mx-4" {...registerFigures} state={overview.state} />

            {/* 🔍 THE CRITERIA ROW -- card.css `.criteria`. Three labelled fields printed on
                the page, "More criteria" beside them, Clear at the end. This page had FIFTEEN
                in a grid, which is a search form rather than a register's criteria. The other
                twelve are all still here, one disclosure away -- nothing was removed. */}
            <div className={cn("no-print", PAGE_GUTTER, "pt-4")}>
                <Criteria>
                    <SearchableSelect
                        className={CRITERION}
                        label="Bank"
                        value={view.bank}
                        onChange={handleViewCriterion("bank")}
                        placeholder="All banks"
                        {...refs.field('banks')}
                        options={[{ value: '', label: 'All banks' }, ...bankOptions.map(b => ({ value: b, label: b }))]}
                    />
                    {/* A picker, as the mock's `.crit` is -- fed by the branches the register's
                        members are at (the server's list since A10), so it never offers a branch
                        with no members. */}
                    <SearchableSelect
                        className={CRITERION}
                        label="Branch"
                        value={view.branch}
                        onChange={handleViewCriterion("branch")}
                        placeholder="Any"
                        options={[{ value: "", label: "Any" }, ...registerBranches.map(b => ({ value: b, label: b }))]}
                    />
                    <SearchableSelect
                        className={CRITERION}
                        label="Status"
                        value={view.status}
                        onChange={handleViewCriterion("status")}
                        placeholder="Any"
                        options={[{ value: "", label: "Any" }, ...statusOptions.map(o => ({ value: o, label: o }))]}
                    />
                    {/* One flex item, so the two controls share a baseline on a phone instead
                        of Clear orphaning onto a line of its own. */}
                    <div className="flex shrink-0 items-center gap-1">
                        <Button
                            variant="ghost"
                            onClick={() => setMoreCriteria((open) => !open)}
                            aria-expanded={moreCriteria}
                            aria-controls="more-criteria"
                            className="pl-1.5"
                        >
                            <SlidersHorizontal size={16} strokeWidth={1.75} aria-hidden="true" />
                            More criteria
                            {activeExtraCriteria > 0 && (
                                <span className="ml-0.5 rounded-full bg-brand-soft px-1.5 text-xs font-semibold tabular-nums text-brand-ink">
                                    {activeExtraCriteria}
                                </span>
                            )}
                        </Button>
                        <Button variant="ghost" onClick={handleClear}>Clear</Button>
                    </div>
                </Criteria>

                {/* ⚠️ THE OTHER TWELVE ARE ALL STILL HERE. The mock shows three criteria and a
                    "More criteria" control; it does not show a register with twelve fewer
                    ways to search it. Collapsing is a change to what is on screen at rest,
                    never to what the register can do -- and the count on the control above is
                    what stops a collapsed criterion filtering the list invisibly. */}
                <div
                    id="more-criteria"
                    hidden={!moreCriteria}
                    className="mt-3 grid grid-cols-1 items-end gap-x-3 gap-y-3 rounded-lg bg-surface p-4 shadow-(--shadow-1) sm:grid-cols-2 lg:grid-cols-4"
                >
                    <SearchableSelect label="Title" value={criteria.title} onChange={handleInputChange("title")} placeholder="All Titles" options={[{ value: "", label: "All Titles" }, ...titleOptions.map(o => ({ value: o, label: o }))]} />
                    <TextInput label="Employee Number" kind="identifier" value={criteria.employeeNumber} onChange={handleInputChange("employeeNumber")} placeholder="The start of the number" maxLength={TERM_MAX} />
                    <TextInput label="Membership Number" kind="identifier" value={criteria.membershipNumber} onChange={handleInputChange("membershipNumber")} placeholder="The start of the number" maxLength={TERM_MAX} />
                    <TextInput label="Initials" value={criteria.initials} onChange={handleInputChange("initials")} placeholder="The start of the initials" maxLength={TERM_MAX} />
                    <TextInput label="Surname" kind="name" value={criteria.surname} onChange={handleInputChange("surname")} placeholder="The start of the surname" maxLength={TERM_MAX} />
                    {/* 🔴 A28, CLOSED. This was a bare <select> under a <span>Identity Type</span>
                        — a span is not a label, so nothing associated the two and a
                        screen-reader user reached a dropdown announced only as "combo box",
                        with no way to know what it filtered. axe rated it CRITICAL, it was
                        the only violation the Members list had, and every neighbouring
                        filter was already a primitive. `SelectInput` carries the real
                        <label htmlFor>. The pin in `a11y.test.jsx` is flipped in this same
                        commit — never repaired. */}
                    <SelectInput
                        label="Identity Type"
                        value={criteria.identityType}
                        onChange={handleInputChange("identityType")}
                        placeholder="All Types"
                        options={[{ value: "", label: "All Types" }, ...idTypeOptions.map(o => ({ value: o, label: o }))]}
                    />
                    {/* 🔒 Whole, or by the last four the masked displays print -- never by a part: a number's
                        middle is no way to find a member, and every part of every member's was once matched here. */}
                    <TextInput label="Identity Number" kind="identifier" value={criteria.identityNumber} onChange={handleInputChange("identityNumber")} placeholder="Whole, or its last four digits" maxLength={TERM_MAX} />
                    <TextInput label="Bank Account Number" kind="identifier" value={criteria.bankAccountNumber} onChange={handleInputChange("bankAccountNumber")} placeholder="Whole, or its last four digits" maxLength={TERM_MAX} />
                    <TextInput label="Cellphone Number" kind="tel" value={criteria.phone} onChange={handleInputChange("phone")} placeholder="Whole, or its last four digits" maxLength={TERM_MAX} />
                    <TextInput label="Personal Email" kind="email" value={criteria.personalEmail} onChange={handleInputChange("personalEmail")} placeholder="The start of the address" maxLength={TERM_MAX} />
                    <TextInput label="Work Email" kind="email" value={criteria.workEmail} onChange={handleInputChange("workEmail")} placeholder="The start of the address" maxLength={TERM_MAX} />
                    <SearchableSelect label="Race" value={criteria.race || ""} onChange={handleInputChange("race")} placeholder="All Races" options={[{ value: "", label: "All Races" }, ...raceOptions.map(o => ({ value: o, label: o }))]} />
                </div>
            </div>

            {/* ✅ THE FOURTH STATE (EH-D9). The register below has its own error state and
                its own retry; this is about the two lists that fill the EDIT panel. A
                missing bank list must not blank a register that loaded perfectly well,
                and it must not be silent either -- an administrator who opens the edit
                panel and finds no banks would otherwise conclude there are none. */}
            {(refs.state === 'partial' || refs.state === 'error') && (
                <div className="mx-7 mt-4 rounded-lg bg-surface shadow-(--shadow-1) max-lg:mx-4">
                    <DataState
                        state={refs.state}
                        layout="row"
                        compact
                        onRetry={refs.retry}
                        // 🔴 "THE REGISTER BELOW IS UP TO DATE" WAS SAID WHATEVER THE REGISTER HAD DONE (`06.4`) -- while it was
                        // loading, and when it had failed as well -- and the sentence began in lowercase, "banks did not load".
                        title={refs.state === "error" ? "None of the edit panel’s option lists could be loaded." : "Some option lists are missing."}
                        message={`${refs.failed.join(" and ").replace(/^./, (c) => c.toUpperCase())} did not load, so the edit panel cannot offer them.${listState === "ready" ? " The register below is complete." : ""}`}
                    />
                </div>
            )}

            {/* 🔴 A27, CLOSED. `Table` renders loading, error and empty as three separate
                branches, and `error` NEVER renders as `empty` — which is the whole
                defect: a backend that is down used to say "no members match", and an
                administrator reads that as a fact about the union. The retry re-runs the
                same fetch the mount effect does. The copy is card.html's own. */}
            {/* 📐 card.css `.register` + `.card-surface`: the sheet fills the remaining
                height, and the pager is its foot. The `register` class is the container the
                column ladder measures (index.css). */}
            <section
                aria-label="Member register"
                className={cn(
                    "register flex flex-1 flex-col pb-7 pt-4",
                    PAGE_GUTTER,
                    printScope === "register" && "print-area",
                )}
            >
                <Sheet>
                    <Table
                        caption="Sasbo members matching the current search"
                        columns={columns}
                        rows={visibleMembers}
                        rowKey="id"
                        selectedKey={selectedMember?.id ?? opening?.row?.id}
                        onRowSelect={requestOpenRecord}
                        enterKey={enterKey}
                        // ↕️ A10: the headings sort, on the server -- `aria-sort` on the heading the rows are in.
                        sort={view.sort}
                        onSortChange={handleSort}
                        loading={listState === "loading"}
                        error={listState === "error" ? "The register could not be reached" : null}
                        errorMessage="The server did not answer, so this is not an empty result. Nothing you typed has been lost."
                        onRetry={retryMembers}
                        emptyTitle={query ? `No member matches “${query}”` : "No members match these criteria"}
                        emptyMessage={
                            isWholeIdentityNumber(query)
                                ? "No member on record has that identity number. Check it, or search by the member's surname or membership number."
                                : query
                                    ? "A search finds the start of a surname, a first name, a membership or employee number, or an identity number whole or by its last four digits."
                                    : "Clear the criteria to see the whole register, or capture a new member."
                        }
                        // 🔴 "Clear" WAS OFFERED WHEN NOTHING WAS SET, AND DID NOTHING (`06.4`). A register that is empty with no
                        // search and no criteria has nothing to clear; what there is to do is capture a member.
                        emptyAction={
                            narrowed
                                ? <Button onClick={handleClear}>Clear</Button>
                                : <Button variant="primary" onClick={() => navigate("/members/capture")}>Capture member</Button>
                        }
                    />

                    {/* card.css `.pager`: "Showing 1–14 of 2 418 · Page 1 of 173", Previous and Next -- the server's
                        page and count since A10, with the page size beside them (REG-25 is SASBO's to answer). */}
                    <Pager
                        page={page}
                        totalPages={totalPages}
                        onPageChange={handlePageChange}
                        count={totalMembers}
                        start={startIndex + 1}
                        end={endIndex}
                        noun="members"
                        error={listState === "error"}
                        loading={listState === "loading"}
                        /* 🔴 THE KEYBOARD LEGEND MOVED HERE FROM THE RECORD PANEL'S EMPTY
                           STATE. It always described THIS surface -- ↑ and ↓ move the
                           selection, Enter opens the record -- and it was only ever on screen
                           while the panel had nothing in it, so when the owner asked for that
                           empty column to close it would have taken the one place the
                           application teaches its own keyboard model with it. */
                        hint={visibleMembers.length > 0 ? (
                            <KeyHints
                                hints={[
                                    [['↑', '↓'], 'move'],
                                    [['Enter'], 'open'],
                                    [['⌘K'], 'search'],
                                ]}
                            />
                        ) : null}
                    >
                        <label className="mr-2 inline-flex items-center gap-2 text-sm text-ink-muted">
                            <span>Per page</span>
                            <select
                                value={view.size}
                                onChange={(event) => handlePageSize(event.target.value)}
                                className="touch-target h-8 rounded-md border border-line-strong bg-surface px-2 text-sm text-ink focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring"
                            >
                                {REGISTER_PAGE_SIZES.map((size) => <option key={size} value={size}>{size}</option>)}
                            </select>
                        </label>
                    </Pager>
                </Sheet>
            </section>

            {/* card.css `.toast`: the save's confirmation, low and centred, gone in 2.6s. */}
            {/* card.css `.toast`: the save's confirmation, low and centred, gone in 2.6s.
                `<output>` NOT `<div role="status">`: its implicit role IS `status`, so the
                live region is native semantics rather than a generic box wearing an ARIA hat --
                consistent across every device and screen reader. */}
            {toast && (
                <output
                    className="kind-in fixed bottom-[84px] left-1/2 z-50 flex -translate-x-1/2 items-center gap-2.5 rounded-[10px] bg-ink px-3.5 py-2.5 text-[13.5px] font-medium text-bg shadow-(--shadow-2)"
                >
                    <CircleCheck size={16} strokeWidth={2} aria-hidden="true" className="text-brand" />
                    <span>{toast}</span>
                </output>
            )}

            {/* ✅ THE RECORD IS A DOCKED PANEL, NOT A MODAL.
                directionContract.md refuses "a modal for tasks that need neither
                interruption nor protected focus: the record edits in place", and this is the
                surface it names. `03.4` had routed it through the shared Modal with the
                other seven overlays -- correct for those, wrong for this one, because that
                unit was scoped by "things that are dialogs today" rather than by "things the
                design says should be dialogs".

                ⚠️ STILL GUARDED BY `selectedMember &&`, and that is not belt-and-braces:
                JSX children are CONSTRUCTED whether or not they render, so the body's
                `selectedMember.address` runs on every render of this page. Passing only
                `open={Boolean(selectedMember)}` threw `Cannot read properties of null` the
                moment the page mounted -- caught by the route smoke, not by reading. */}
            <MemberPanel
                // 📂 A10: while a record is read, the panel shows the row's card -- name, number, status, bank -- and
                // says it is opening; the record replaces it when it arrives.
                member={opening ? opening.row : selectedMember}
                open={Boolean(selectedMember || opening) && !panelClosing}
                onClose={requestCloseRecord}
                footer={
                    opening ? null : isEditing ? (
                        <>
                            <Button onClick={handleEditToggle}>Cancel</Button>
                            <Button variant="primary" onClick={handleSaveChanges}>
                                <Check size={16} strokeWidth={2} aria-hidden="true" /> Save changes
                            </Button>
                        </>
                    ) : (
                        <>
                            <Button size="icon" onClick={() => printWith("record")} aria-label="Print this member's record">
                                <Printer size={16} strokeWidth={1.75} aria-hidden="true" />
                            </Button>
                            <Button variant="primary" onClick={handleEditToggle}>
                                <Pencil size={16} strokeWidth={1.75} aria-hidden="true" /> Edit Member
                            </Button>
                        </>
                    )
                }
            >
                {/* ⚠️ THE `selectedMember &&` GUARD IS LOAD-BEARING AND I REMOVED IT ONCE.
                    JSX children are CONSTRUCTED whether or not the parent renders them, so
                    the body's `selectedMember.address` runs on every render of this page.
                    Swapping the Modal for the panel dropped this guard, and the register
                    went to the error boundary with `Cannot read properties of null (reading
                    'address')` the moment it mounted -- the same failure the Modal's own
                    comment recorded, reintroduced by the commit that removed the Modal. */}
                {opening && (
                    <div className="mt-6">
                        <DataState
                            state={opening.error ? "error" : "loading"}
                            title={opening.error ? "This member's record could not be opened." : "Opening the record…"}
                            message={opening.error ? "The register is as it was; nothing has changed." : null}
                            error={opening.error}
                            onRetry={() => loadRecord(opening.row)}
                        />
                    </div>
                )}
                {!opening && selectedMember && (
                    <div className={cn("member-record", printScope === "record" && "print-area")}>
                        {/* THE ERROR SUMMARY that replaced three `alert()` calls. Focused
                            rather than `role="alert"`: `Field` already gives every inline
                            message that role, so a form failing four validations would
                            queue five announcements at once. F-P23. */}
                        {saveNotice && (
                            <div
                                ref={errorSummaryRef}
                                tabIndex={-1}
                                aria-labelledby="member-save-notice-title"
                                className={`mt-4 rounded-lg border p-3 text-sm no-print focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring ${
                                    saveNotice.kind === "unchanged" || saveNotice.kind === "stale"
                                        ? "border-line-strong bg-surface-2 text-ink-muted"
                                        : "border-status-suspended-dot bg-status-suspended-bg text-status-suspended"
                                }`}
                            >
                                <p id="member-save-notice-title" className="flex items-center gap-2 font-medium">
                                    <AlertCircle aria-hidden="true" className="h-4 w-4 shrink-0" strokeWidth={1.5} />
                                    {SAVE_NOTICE_TITLES[saveNotice.kind]}
                                </p>
                                <ul className="mt-1 list-disc pl-6">
                                    {saveNotice.messages.map((message) => <li key={message}>{message}</li>)}
                                </ul>
                                {saveNotice.kind === "changed" && (
                                    <Button size="sm" className="mt-2" onClick={reloadChangedRecord}>Reload the record</Button>
                                )}
                            </div>
                        )}

                        {/* card.css `.tabs`: a wrapping strip under a hairline, the current
                            section underlined in the brand's teal.

                            ⚠️ NOT `role="tab"`, AND THAT IS DELIBERATE. It looks like a tab
                            strip and the first draft said so — but the ARIA tabs pattern is
                            a CONTRACT: arrow keys move between tabs, one tab stop covers the
                            set, and each tab owns a `tabpanel`. Declaring the role without
                            implementing the keyboard behaviour tells a screen-reader user
                            "tab 3 of 5" and then does nothing when they press the arrow key,
                            which is worse than a plain group of buttons — those simply work.
                            `aria-current` says which section is showing without promising
                            anything that is not here.

                            ⏳ The full pattern needs roving focus, and a shared
                            `useRovingFocus` is already on RF's list precisely because this
                            application has three places that want it. */}
                        {/* 🔑 IT WRAPS. IT DOES NOT SCROLL — AND THE MOCK SETTLED THAT.
                            Short labels at the mock's own 13px fit a three-column composition
                            in the docked `clamp(424px, 32vw, 448px)` panel. The rule sits on
                            the wrapper so it runs under the whole block. */}
                        <div className="no-print mt-4 border-b border-line">
                            <div className="grid grid-cols-3 gap-x-0.5">
                                {TABS.map((tab, index) => (
                                    <button
                                        key={tab.id}
                                        type="button"
                                        aria-current={activeTab === tab.id ? "true" : undefined}
                                        onClick={() => changeTab(tab.id)}
                                        className={cn(
                                            // card.css:358 -- 13px, not 14: a quiet index of the record, not a second row of headings.
                                            "touch-target inline-flex items-center gap-1 whitespace-nowrap px-[9px] pb-[11px] pt-[9px] text-[13px] font-medium",
                                            TAB_PLACE[index],
                                            "transition-[color,box-shadow] duration-(--duration-instant) focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring",
                                            activeTab === tab.id
                                                ? "font-semibold text-brand-ink shadow-[inset_0_-2px_0_var(--color-brand)]"
                                                : "text-ink-muted shadow-[inset_0_-2px_0_transparent] hover-hover:hover:text-ink",
                                        )}
                                >
                                    {/* Shown short, named in full -- see `RECORD_TABS`. Not `aria-label`: that would
                                        also silence the "has errors" text a screen reader gets below. */}
                                    <span aria-hidden="true">{tab.label}</span>
                                    <span className="sr-only">{tab.full}</span>
                                    {isEditing && tabsWithErrors.has(tab.id) && (
                                        <>
                                            <span aria-hidden="true" className="text-status-suspended font-extrabold text-sm">*</span>
                                            {/* The asterisk was the only signal, and it is
                                                invisible to a screen reader and to anyone
                                                who cannot distinguish the colour. */}
                                            <span className="sr-only">(has errors)</span>
                                        </>
                                    )}
                                </button>
                            ))}
                        </div>
                        </div>

                        {/* card.css `.remarks`: the slip of gold paper under the tabs, only when
                            the record carries a remark. */}
                        {selectedMember.remarks && !isEditing && (
                            <p className="record-rise mt-[18px] rounded-lg bg-accent-soft px-3 py-2.5 text-sm text-ink">
                                <span className="mb-0.5 block text-[11px] font-semibold uppercase tracking-[0.08em] text-accent-ink">Remarks</span>
                                {selectedMember.remarks}
                            </p>
                        )}

                        <div
                            key={activeTab}
                            className={cn("pt-[18px] text-sm", tabDirection === "fwd" ? "tab-enter-fwd" : "tab-enter-back")}
                        >
                            {activeTab === "personal" && (
                                <div className="fieldgroup grid grid-cols-2 gap-x-4 gap-y-3.5 max-sm:grid-cols-1">
                                    <Detail label="Title" value={editForm.title} isEditing={isEditing} options={titleOptions} onChange={handleEditChange("title")} />
                                    <Detail label="Initials" value={editForm.initials} isEditing={isEditing} onChange={handleEditChange("initials")} onBlur={handleEditBlur("initials")} error={fieldErrors.initials} />
                                    <Detail label="First Name(s)" value={editForm.firstNames} isEditing={isEditing} onChange={handleEditChange("firstNames")} onBlur={handleEditBlur("firstNames")} error={fieldErrors.firstNames} required kind="name" />
                                    <Detail label="Surname" value={editForm.surname} isEditing={isEditing} onChange={handleEditChange("surname")} onBlur={handleEditBlur("surname")} error={fieldErrors.surname} required kind="name" />
                                    <Detail label="Identity Type" value={editForm.identityType} isEditing={isEditing} options={idTypeOptions} onChange={handleEditChange("identityType")} error={fieldErrors.identityType} required />
                                    <Detail
                                        label={editForm.identityType || "Identity Number"}
                                        value={editForm.identityNumber}
                                        isEditing={isEditing}
                                        onChange={handleEditChange("identityNumber")}
                                        error={fieldErrors.identityNumber}
                                        disabled={!editForm.identityType}
                                        kind={editForm.identityType === IDENTITY_TYPE_PASSPORT ? "reference" : "identifier"}
                                        required
                                    />
                                    {/* 🔴 `04.2`: was a native <input type="date"> reached through
                                        `Detail`'s `type` prop -- one of the three the P3 box
                                        could not close, because BR-10 owned this file until
                                        now. The I.D.-derived case stays read-only text: it is
                                        computed from the identity number, not typed. */}
                                    {editForm.identityType === "I.D. Number" ? (
                                        <Detail label="Date of Birth" value={recordDate(editForm.dateOfBirth)} isEditing={isEditing} disabled onChange={handleEditChange("dateOfBirth")} />
                                    ) : isEditing ? (
                                        <DateField
                                            label="Date of Birth"
                                            value={editForm.dateOfBirth || ""}
                                            required={editForm.identityType === IDENTITY_TYPE_PASSPORT}
                                            error={fieldErrors.dateOfBirth || undefined}
                                            max="today"
                                            onChange={(e) => setEditForm(prev => ({ ...prev, dateOfBirth: e.target.value }))}
                                        />
                                    ) : (
                                        <Detail label="Date of Birth" value={recordDate(editForm.dateOfBirth)} isEditing={false} />
                                    )}
                                    <Detail label="Gender" value={editForm.gender} isEditing={isEditing} options={editForm.identityType === "I.D. Number" ? null : genderOptions} onChange={handleEditChange("gender")} disabled={editForm.identityType === "I.D. Number"} />
                                    <Detail label="Race" value={editForm.race} isEditing={isEditing} options={raceOptions} onChange={handleEditChange("race")} />
                                    <Detail label="Cellphone Number" value={editForm.phone} isEditing={isEditing} onChange={handleEditChange("phone")} kind="tel" error={fieldErrors.phone} />
                                    <Detail label="Work Tel" value={editForm.workTel} isEditing={isEditing} onChange={handleEditChange("workTel")} kind="tel" error={fieldErrors.workTel} />
                                    <Detail label="Home Tel" value={editForm.homeTel} isEditing={isEditing} onChange={handleEditChange("homeTel")} kind="tel" error={fieldErrors.homeTel} />
                                    <div className="col-span-full">
                                        {isEditing ? (
                                            <AddressAutocomplete label="Residential Address" value={editForm.address} onChange={handleEditChange("address")} placeholder="Update residential location address strings..." />
                                        ) : (
                                            <Detail label="Residential Address" value={selectedMember.address} isEditing={false} />
                                        )}
                                    </div>
                                </div>
                            )}

                            {activeTab === "employment" && (
                                <div className="fieldgroup grid grid-cols-2 gap-x-4 gap-y-3.5 max-sm:grid-cols-1">
                                    {/* 🏢 01.3 -- read, the member's workplace and its lineage; editing, capture's control. It
                                        offers to fill the two bank fields below and never writes them itself. */}
                                    <WorkplaceField
                                        className="col-span-full"
                                        stacked
                                        source={isEditing ? workplaceSource : undefined}
                                        choice={workplace}
                                        current={{
                                            ...(workplace.workplace ? { workplace: workplace.workplace } : memberWorkplace),
                                            from: workplaceSource.workplacesFrom,
                                        }}
                                        error={fieldErrors.workplaceId}
                                    />
                                    <Detail label="Employee Number" value={editForm.employeeNumber} isEditing={isEditing} onChange={handleEditChange("employeeNumber")} kind="reference" />
                                    <Detail label="Bank Where Employed" value={editForm.bankName} isEditing={isEditing} {...refs.field('banks')} options={bankOptions} error={fieldErrors.bankName} required={editForm.paymentType === "Debit Order"} onChange={handleEditChange("bankName")} note={isEditing && workplace.note("bankName")} />
                                    <Detail label="Employment Type" value={editForm.employmentType} isEditing={isEditing} options={EMPLOYMENT_TYPE_OPTIONS} onChange={handleEditChange("employmentType")} />
                                    <Detail label="Job Grade" value={editForm.jobGrade} isEditing={isEditing} options={jobGrade} onChange={handleEditChange("jobGrade")} disabled={!editForm.bankName} loadError={jobGradeError} onRetryLoad={() => setJobGradeAttempt((n) => n + 1)} />
                                    <Detail label="Bank Branch" value={editForm.branch} isEditing={isEditing} options={branchOptions} error={fieldErrors.branch} required={editForm.paymentType === "Debit Order"} onChange={handleEditChange("branch")} note={isEditing && workplace.note("branch")} />
                                    <Detail label="Payment Type" value={editForm.paymentType} isEditing={isEditing} options={paymentTypeOptions} onChange={handleEditChange("paymentType")} />

                                    {editForm.paymentType === "Debit Order" && (
                                        <>
                                            <Detail label="Bank Account Number" value={editForm.bankAccountNumber} isEditing={isEditing} error={fieldErrors.bankAccountNumber} required onChange={handleEditChange("bankAccountNumber")} kind="identifier" />
                                            {/* 00.5 -- never required here: every member captured before it has none, and an edit of their
                                                phone number must not be refused for it (R-ROB-I). The run names them instead. */}
                                            <Detail label="Account Type" value={editForm.accountType} isEditing={isEditing} options={ACCOUNT_TYPE_OPTIONS} onChange={handleEditChange("accountType")} />
                                            <Detail
                                                label="Debit Day"
                                                value={editForm.debitDay}
                                                isEditing={isEditing}
                                                options={DEBIT_DAY_OPTIONS}
                                                error={fieldErrors.debitDay}
                                                onChange={handleEditChange("debitDay")}
                                            />
                                        </>
                                    )}
                                    <Detail label="IBT No." value={editForm.ibtNo} isEditing={isEditing} onChange={handleEditChange("ibtNo")} kind="identifier" error={fieldErrors.ibtNo} />
                                    <div className="col-span-full">
                                        {isEditing ? (
                                            <AddressAutocomplete label="Bank Work Address" value={editForm.bankAddress} onChange={handleEditChange("bankAddress")} placeholder="Search coordinates..." />
                                        ) : (
                                            <Detail label="Bank Address" value={selectedMember.bankAddress} isEditing={false} />
                                        )}
                                    </div>
                                </div>
                            )}

                            {activeTab === "membership" && (
                                <div className="fieldgroup grid grid-cols-2 gap-x-4 gap-y-3.5 max-sm:grid-cols-1">
                                    <Detail label="Date Joined Sasbo" value={recordDate(selectedMember.joinDate)} isEditing={false} />
                                    {/* 🔁 03.3: A STATUS IS CHANGED, NEVER EDITED -- with SASBO's code for it, through
                                        *Change status*, and the change is kept. Shown here as the card shows it, and
                                        read-only while editing too, where the note says the way to change it. */}
                                    <Detail
                                        label="Account Membership Status"
                                        value={(
                                            <span className="flex flex-wrap items-center justify-between gap-2">
                                                <StatusChip status={editForm.status} />
                                                {!isEditing && (
                                                    <Button size="sm" className="no-print" onClick={() => setChangingStatus(true)}>
                                                        Change status
                                                    </Button>
                                                )}
                                            </span>
                                        )}
                                        isEditing={false}
                                        note={isEditing ? "Changed with Change status, once this edit is saved or cancelled." : null}
                                    />
                                    <h4 className="col-span-full mt-1.5 border-t border-line pt-[18px] text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Sasbo Subscription Fee Details</h4>
                                    {/* 🔴 A MEMBER WITH NO SUBSCRIPTION ON FILE WAS SHOWN R182.77, MONTHLY (`06.4`) -- two invented
                                        facts about their money, printed as if the record held them. The absence is said instead. */}
                                    <Detail label="Monthly Subscription (ZAR)" value={amountOnFile(selectedMember.monthlySubscription)} isEditing={false} />
                                    <Detail label="Billing Frequency" value={notOnFile(selectedMember.subscriptionFrequency)} isEditing={false} />
                                </div>
                            )}

                            {activeTab === "representatives" && (
                                <div className="fieldgroup grid grid-cols-2 gap-x-4 gap-y-3.5 max-sm:grid-cols-1">
                                    {/* 🗣️ 03.9 -- ALL THREE, AND NONE OF THEM EDITABLE FROM HERE. The stakeholder:
                                        recruitment "should only show ... should not be editable from there". It showed
                                        TWO, and not because the third was hidden: "Recruiter Type" had no field
                                        anywhere until 03.8 gave the capture screen one.

                                        🔑 "Area's Recruiter" is GONE rather than relabelled (R2/RE-3). It read
                                        `areasRep`, which held a PERSON chosen from the recruiter list, under an
                                        area-shaped name. `areaRecruited` is a different field carrying a different
                                        kind of thing; showing the old value under the new label would have been the
                                        same defect with a corrected word on it.

                                        Read-only here, editable on capture: a recruiter is assigned where the member
                                        is captured, and these three are a historical fact about that moment. */}
                                    <Detail label="Recruiter Type" value={editForm.recruitedHow} isEditing={false} />
                                    {/* 🗣️ SASBO, 18 Sep 2026: the area follows the recruiter. A stored area is shown as
                                        held; otherwise it is read off the recruiter, and the note says whose it is --
                                        or why there is none (`recordArea`, above). */}
                                    <Detail label="Area Recruited" value={recordArea.text} note={recordArea.note} isEditing={false} />
                                    <Detail label="Recruiter" value={editForm.recruitmentRepresentativeName} isEditing={false} />
                                </div>
                            )}

                            {activeTab === "structures" && (
                                <div className="space-y-5">
                                    <div className="border-b border-line pb-3">
                                        <h3 className="text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">SASBO structures</h3>
                                    </div>

                                    {/* 🗣️ SASBO'S STRUCTURES TREE, THROUGH THE OWNER, 2026-09-30 -- "we need those
                                        functionalities back":
                                          Shop Steward? → Election Date
                                          BEC Member? → Branch Executive Committee · BEC Recruiter? ·
                                                        BEC Structure Representative? → the structures represented ·
                                                        BEC Executive? → the one position held ·
                                                        COSATU Member? → Local? · Regional? · COSATU positions
                                        This tab asked all of it until 2026-09-17 and was cut to Shop Steward, BEC and
                                        COSATU then, because nothing below them had a column (CM-05). V55 gives each one,
                                        so the tree is back; the rules are `utils/structures.js`, held by the server too.
                                        🔑 EDIT MEMBER ONLY: SASBO's spec took the structures off Capture Member, "because
                                        a newly captured member cannot sit on a structure" (2.4).
                                        🔴 COSATU STAYS NESTED UNDER BEC -- the stakeholder, 2026-09-17: "for a member to
                                        participate in COSATU structures, they have to be within the BEC structure", which
                                        the server enforces (`validateStructures`). A COSATU Local and a COSATU Region are
                                        structures a member SITS ON, so each stays a yes/no; SASBO's tree lists them with
                                        the positions. */}
                                    {isEditing ? (
                                        <div className="space-y-6">
                                            <div className="space-y-4">
                                                <Detail label="Shop Steward?" value={yesNo(editForm.isShopSteward)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isShopSteward", e.target.value === "Yes")} />
                                                {editForm.isShopSteward === true && (
                                                    <div className="border-l border-line pl-4">
                                                        <DateField
                                                            label="Election Date"
                                                            required
                                                            value={editForm.shopStewardElectionDate || ""}
                                                            error={fieldErrors.shopStewardElectionDate || undefined}
                                                            max="today"
                                                            hint="ago"
                                                            onChange={(e) => setStructure("shopStewardElectionDate", e.target.value || null)}
                                                        />
                                                    </div>
                                                )}
                                            </div>

                                            <div className="border-t border-line pt-4">
                                                <Detail label="BEC (Branch Executive Committee) Member?" value={yesNo(editForm.isBec)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isBec", e.target.value === "Yes")} />

                                                {editForm.isBec === true && (
                                                    <div className="mt-4 space-y-4 border-l border-line pl-4">
                                                        {/* SASBO's own committees, alphabetical (18 Sep 2026); UNATTACHED is not one. */}
                                                        <SearchableSelect
                                                            label="Branch Executive Committee"
                                                            value={editForm.becCommitteeCode || ""}
                                                            options={committeeList.options}
                                                            loading={committeeList.loading}
                                                            loadError={committeeList.error}
                                                            onRetryLoad={committeeList.refetch}
                                                            emptyLabel="SASBO's committees are not on this system yet"
                                                            placeholder="Choose a committee…"
                                                            required
                                                            error={fieldErrors.becCommitteeCode || undefined}
                                                            onChange={(code, option) => setStructure("becCommitteeCode", code || "", { becCommitteeName: option?.label ?? "" })}
                                                        />
                                                        <Detail label="BEC Recruiter?" value={yesNo(editForm.isBecRecruiter)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isBecRecruiter", e.target.value === "Yes")} />

                                                        <Detail label="BEC Structure Representative?" value={yesNo(editForm.isBecStructureRep)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isBecStructureRep", e.target.value === "Yes")} />
                                                        {editForm.isBecStructureRep === true && (
                                                            <CheckboxGroup
                                                                legend="Structures represented"
                                                                required
                                                                options={BEC_STRUCTURES_REPRESENTED}
                                                                selected={editForm.becStructures || []}
                                                                error={fieldErrors.becStructures}
                                                                onToggle={(next) => setStructure("becStructures", inOrder(next, BEC_STRUCTURES_REPRESENTED))}
                                                            />
                                                        )}

                                                        <Detail label="BEC Executive?" value={yesNo(editForm.isBecExecutive)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isBecExecutive", e.target.value === "Yes")} />
                                                        {editForm.isBecExecutive === true && (
                                                            <Detail label="Executive Position" value={editForm.becExecutivePosition} isEditing={true} options={BEC_EXECUTIVE_POSITIONS} required error={fieldErrors.becExecutivePosition} onChange={(e) => setStructure("becExecutivePosition", e.target.value)} />
                                                        )}

                                                        <div className="border-t border-line pt-4">
                                                            <Detail label="COSATU Member?" value={isCosatuMember(editForm) ? "Yes" : "No"} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isCosatu", e.target.value === "Yes" ? "Y" : "N")} />

                                                            {isCosatuMember(editForm) && (
                                                                <div className="mt-4 space-y-4 border-l border-line pl-4">
                                                                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                                                                        <Detail label="Sits on a COSATU Local structure?" value={yesNo(editForm.isCosatuLocal)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isCosatuLocal", e.target.value === "Yes")} />
                                                                        <Detail label="Sits on a COSATU Regional structure?" value={yesNo(editForm.isCosatuRegional)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isCosatuRegional", e.target.value === "Yes")} />
                                                                    </div>
                                                                    <CheckboxGroup
                                                                        legend="COSATU positions"
                                                                        options={COSATU_POSITIONS}
                                                                        labelFor={(position) => COSATU_POSITION_LABELS[position] ?? position}
                                                                        selected={editForm.cosatuPositions || []}
                                                                        onToggle={(next) => setStructure("cosatuPositions", inOrder(next, COSATU_POSITIONS))}
                                                                    />
                                                                </div>
                                                            )}
                                                        </div>
                                                    </div>
                                                )}
                                            </div>

                                            {/* 🧷 Month end 04.8, MM80 -- SASBO, 2026-10-01: a Memnet member is "a contact member, it get
                                                updated or captured as a mnet member when we get the nomination firm". Asked here, on the
                                                record, as every structure answer is; never a default (the sixteen hidden keys capture once
                                                posted had `memnetMember: "N"` among them). Month end counts it by area and table code. */}
                                            <div className="border-t border-line pt-4">
                                                <Detail label="Memnet Member?" value={yesNo(editForm.isMemnet)} isEditing={true} options={["Yes", "No"]} onChange={(e) => setStructure("isMemnet", e.target.value === "Yes")} />
                                            </div>
                                        </div>
                                    ) : (
                                        <div className="space-y-3">
                                            <h4 className="text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Core Appointments</h4>
                                            <Detail label="Shop Steward" value={yesNo(selectedMember.isShopSteward, "Not recorded")} isEditing={false} />
                                            {selectedMember.isShopSteward === true && (
                                                <div className="border-l border-line pl-4">
                                                    <Detail label="Election Date" value={recordDate(selectedMember.shopStewardElectionDate) || "Not recorded"} isEditing={false} />
                                                </div>
                                            )}
                                            <Detail label="BEC Member" value={yesNo(selectedMember.isBec, "Not recorded")} isEditing={false} />
                                            {(selectedMember.isBec === true || isCosatuMember(selectedMember)) && (
                                                <div className="space-y-3 border-l border-line pl-4">
                                                    {selectedMember.isBec === true && (
                                                        <>
                                                            <Detail label="Branch Executive Committee" value={selectedMember.becCommitteeName || "Not recorded"} isEditing={false} />
                                                            <Detail label="BEC Recruiter" value={yesNo(selectedMember.isBecRecruiter, "Not recorded")} isEditing={false} />
                                                            <Detail label="BEC Structure Representative" value={yesNo(selectedMember.isBecStructureRep, "Not recorded")} isEditing={false} />
                                                            {selectedMember.isBecStructureRep === true && (
                                                                <Detail label="Structures Represented" value={listText(selectedMember.becStructures, "Not recorded")} isEditing={false} />
                                                            )}
                                                            <Detail label="BEC Executive" value={yesNo(selectedMember.isBecExecutive, "Not recorded")} isEditing={false} />
                                                            {selectedMember.isBecExecutive === true && (
                                                                <Detail label="Executive Position" value={selectedMember.becExecutivePosition || "Not recorded"} isEditing={false} />
                                                            )}
                                                        </>
                                                    )}
                                                    <Detail label="COSATU Member" value={isCosatuMember(selectedMember) ? "Yes" : "No"} isEditing={false} />
                                                    {isCosatuMember(selectedMember) && (
                                                        <>
                                                            <Detail label="COSATU Local structure" value={yesNo(selectedMember.isCosatuLocal, "Not recorded")} isEditing={false} />
                                                            <Detail label="COSATU Regional structure" value={yesNo(selectedMember.isCosatuRegional, "Not recorded")} isEditing={false} />
                                                            <Detail label="COSATU Positions" value={listText(selectedMember.cosatuPositions)} isEditing={false} />
                                                        </>
                                                    )}
                                                </div>
                                            )}
                                            <Detail label="Memnet Member" value={yesNo(selectedMember.isMemnet, "Not recorded")} isEditing={false} />
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* 📜 03.6: the record's changes, newest first -- its own chunk, asked for now that the tab
                                is open, and keyed by the member so another member's record starts on its first page. */}
                            {activeTab === "history" && (
                                <ErrorBoundary fallback={historyFailed}>
                                    <Suspense fallback={null}>
                                        <MemberHistory key={selectedMember.id} member={selectedMember} labelOf={fieldLabel} />
                                    </Suspense>
                                </ErrorBoundary>
                            )}
                        </div>

                    </div>
                )}
            </MemberPanel>

            <ConfirmDialog
                open={Boolean(pendingSave)}
                onOpenChange={closeOn(() => setPendingSave(null))}
                title="Save these changes?"
                confirmLabel="Save changes"
                cancelLabel="Keep editing"
                destructive={false}
                onConfirm={commitSave}
                loading={savingRecord}
            >
                {/* 🔑 The changes are a LIST now, not a string of bullets concatenated into
                    a browser dialog. A long list scrolls inside the modal body rather than
                    running off the bottom of an unscrollable native alert.
                    (`getChangedFields` emits plain `Label: a → b` strings — the leading-bullet
                    strip this used to carry removed nothing, so it is gone.) */}
                <ul className="space-y-1 text-sm text-ink">
                    {(pendingSave ?? []).map((change) => (
                        <li key={change} className="flex gap-2">
                            <span aria-hidden="true" className="text-ink-faint">•</span>
                            <span>{change}</span>
                        </li>
                    ))}
                </ul>
            </ConfirmDialog>

            <ConfirmDialog
                open={Boolean(pendingLeave)}
                onOpenChange={closeOn(() => setPendingLeave(null))}
                title="Discard your changes?"
                description={`The changes to ${personName(selectedMember) || "this record"} have not been saved.`}
                confirmLabel="Discard changes"
                cancelLabel="Keep editing"
                onConfirm={discardAndLeave}
            />

            {/* 🔁 03.3: the record read again before a word is said, and what happened said as the save's is. */}
            <ChangeStatus
                member={selectedMember}
                open={changingStatus}
                onOpenChange={closeOn(() => setChangingStatus(false))}
                reread={rereadMember}
                onDone={(sentence) => {
                    setChangingStatus(false);
                    say(sentence);
                }}
            />
        </div>
    );
}

/**
 * One field of a member record — a value when reading, a control when editing.
 *
 * 🔴 IT WAS A FIFTH ANSWER TO "WHAT WRAPS A FORM CONTROL" (BR-4 counted four). It built its
 * own id from the label text — `detail-${label.toLowerCase().replace(/\s+/g, "-")}` — which
 * collides the moment two fields share a label, and this dialog renders "COSATU Local" in
 * two tabs. It also hand-rolled the border, the focus ring and the error colour, restating
 * decisions the token layer already owns.
 *
 * It now delegates to the primitives: `SelectInput` when there are options, `TextInput`
 * when there are not, and a definition-list entry when reading. `useId` inside `Field`
 * makes the collision impossible rather than unlikely.
 */
/** An edit with changes: the form no longer serialises to the record it started from. */
function recordIsDirty({ isEditing, baseline, editForm }) {
    return isEditing && baseline !== null && JSON.stringify(editForm) !== baseline;
}

function Detail({ label, value, isEditing, onChange, onBlur, className = "", options = null, kind = "text", error = null, disabled = false, required = false, loadError = null, onRetryLoad, note = null }) {
    if (!isEditing) {
        /* card.css `.field`: the label in small capitals, the value in ink, no box around
           either -- a record reads as a record, not as a form with its inputs disabled.
           `note` is a DERIVED value's provenance (`utils/derivation.js`): where it came from, or why
           it is empty -- in the muted small type every other note on these screens uses. */
        return (
            <div className={cn("field record-rise min-w-0", className)}>
                <p className="mb-1 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">{label}</p>
                <p className="min-h-[21px] text-base font-medium text-ink [overflow-wrap:anywhere]">{value || "—"}</p>
                {note && <p className="mt-1 text-xs text-ink-muted">{note}</p>}
            </div>
        );
    }

    /*
     * 🔑 `loadError` IS NOT `error` -- `error` says the VALUE is wrong, `loadError` that the CHOICES never
     * arrived. `SelectInput` renders a failed list since `06.4`; this adapter carried its own copy of that
     * state, as `CaptureMember`'s `Field` did, and now passes the pair on. (`Detail` is the read/edit twin
     * of that `Field`; the props are deliberately named the same on both.)
     */
    if (options) {
        return (
            <SelectInput
                label={label}
                value={value}
                onChange={(next) => onChange?.({ target: { value: next } })}
                options={options}
                placeholder="Select..."
                error={error || undefined}
                hint={note || undefined}
                disabled={disabled}
                required={required}
                loadError={loadError}
                onRetryLoad={onRetryLoad}
                className={className}
            />
        );
    }

    return (
        <TextInput
            label={label}
            value={value}
            onChange={onChange}
            onBlur={onBlur}
            kind={kind}
            error={error || undefined}
            disabled={disabled}
            required={required}
            className={className}
        />
    );
}
