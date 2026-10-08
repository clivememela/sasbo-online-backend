import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import TextInput from '../../components/form/TextInput';
import DateField from '../../components/form/DateField';
import Button from '../../components/form/Button';
import PageTitle from '../../components/common/PageTitle';
import { PageNotice } from '../../components/common/DialogNotice.jsx';
import { Page } from '../../components/common/Sheet.jsx';
import { useAuth } from '../../auth/useAuth';
import { PERMISSIONS } from '../../auth/permissions';
import {
    VISIT_ALREADY_SUBMITTED, VISIT_DRAFT_NOT_FOUND, VISIT_DRAFT_STALE, VISIT_INVALID, WORKPLACE_UNKNOWN,
} from '../../config/monthEnd.js';
import useFocusOn from '../../hooks/useFocusOn.js';
import { formatDateLong, formatInt } from '../../lib/format.js';
import reportError from '../../lib/reportError.js';
import { messageOf, writeFailure, writeMayHaveLanded } from '../../services/parseApiError.js';
import { workplaceService } from '../../services/workplaceService.js';
import {
    createDraft, discardDraft, getDraft, recordVisit, submitDraft, updateDraft,
} from '../../services/workplaceVisitService.js';
import MyVisitDrafts from './MyVisitDrafts.jsx';
import { DRAFT_FIELDS, DRAFT_STATUS, draftOf, formFieldsOf } from './visitDraft.js';

/**
 * 🔴 THESE THREE LIVE AT MODULE SCOPE, AND UNTIL NOW THEY DID NOT -- WHICH BROKE TYPING.
 *
 * `TextArea`, `Field` and `Section` were declared INSIDE `OrganizerReport`, so every render
 * produced three brand-new function identities. React compares an element's `type` by
 * identity: a new type is not the same component, so React does not update the old tree, it
 * UNMOUNTS it and mounts a fresh one. This form holds `formData` in state and writes to it
 * on every keystroke -- so each character re-rendered the parent, replaced all three
 * component types, and tore down and rebuilt all 31 fields. **The field being typed in lost
 * focus after every single character**, and an open `DateField` calendar closed with it. On
 * the one form an organiser fills in standing up, in a branch.
 *
 * The values reach them through a context rather than through 31 new pairs of props, because
 * the IDENTITY is what had to stop changing; the data was never the problem.
 * `eslint`'s `react-hooks/static-components` had been reporting this the whole time.
 *
 * October build: `readOnly` reaches them the same way -- a SUBMITTED visit is SASBO's record, and opens read-only, to
 * be printed and not changed.
 */
const FormScope = createContext({ values: {}, errors: {}, onChange: () => {}, readOnly: false });

/**
 * 🔴 BR-4's FOURTH WRAPPER, AND THE LAST ONE. `blastRadius.md` counted four answers
 * to "what wraps a form control": the shared `SearchableSelect`, a private one in
 * `CaptureMember.jsx`, a private `Field` there, and this. `03.2a` built the shared
 * `Field`, `04.2` retired the two in `CaptureMember.jsx`, and this closes the set.
 *
 * 🔑 IT WRAPPED THE LABEL AROUND THE CONTROL, which works and is not what the rest
 * of this application does -- so this textarea was the one control in the tree whose
 * label was not a `<label htmlFor>`, and the difference was invisible until something
 * tried to query it by label. `TextInput multiline` is the same textarea with the
 * shared chrome: one label element, one error path, one focus ring, one 16px rule.
 */
const TextArea = ({ label, name, rows = 3 }) => {
    const { values, errors, onChange, readOnly } = useContext(FormScope);
    return (
    <TextInput
        label={label}
        name={name}
        multiline
        rows={rows}
        value={values[name]}
        onChange={onChange}
        error={errors[name]}
        readOnly={readOnly}
        className="sm:col-span-2"
    />
    );
};

// 🔴 `03.3`: `type="date"` routes to DateField rather than to a native date input.
// ONE edit reaches all FOUR of this form's date fields, which is the point of the
// wrapper existing at all -- and it is why the conversion is done here rather than at
// each site. (This wrapper is one of BR-4's four; replacing it wholesale is `04.3`.)
// October build: read-only, a date is a read-only text -- `DateField` has no read-only mode, and a disabled one prints
// faded.
const Field = ({ label, name, type = 'text', fullWidth = false, textarea = false }) => {
    const { values, errors, onChange, readOnly } = useContext(FormScope);
    return textarea
        ? <TextArea label={label} name={name} />
        : (
            // `sm:col-span-2`, not `col-span-2`: in a one-column grid a span of two
            // is a span past the end, which some engines honour by widening the track.
            <div className={fullWidth ? 'sm:col-span-2' : ''}>
                {type === 'date' && !readOnly
                    ? <DateField label={label} name={name} value={values[name]} onChange={onChange} hint="ago" error={errors[name]} />
                    : <TextInput label={label} name={name} type={type === 'date' ? 'text' : type}
                        value={type === 'date' && values[name] ? formatDateLong(values[name]) : values[name]}
                        onChange={onChange} error={errors[name]} readOnly={readOnly} />}
            </div>
        );
};

/**
 * 💾 WHAT A VISIT KEEPS (month end A.1, MO22) -- the server's name for each, and the form's field it comes from. The
 * day, who visited, when they came and went, and what the organiser found. Everything else on the form -- the
 * branch's addresses and numbers, its manager and representatives with their telephones and e-mails, the signatures --
 * is a person's details or the paper's, printed with the form and not sent: the server stores none of it (B1).
 */
const VISIT_FIELDS = {
    visitedOn: 'dateOfVisit',
    timeIn: 'timeIn',
    timeOut: 'timeOut',
    organiser: 'organiserName',
    complementFound: 'complement',
    membershipFound: 'membership',
    nonMembers: 'noOfNonMembers',
    recruited: 'noOfNewMembersRecruited',
    nonMembersInterviewed: 'noOfNonMembersInterviewed',
};
/** The counts a visit carries -- whole numbers, or nothing. */
const COUNTS = new Set(['complementFound', 'membershipFound', 'nonMembers', 'recruited', 'nonMembersInterviewed']);

/**
 * The visit a filled form records, and the fields it cannot be sent with: a count that is not a whole number, no day,
 * no R/A number to find the workplace by. Said at each field, BEFORE anything is sent; what the server would refuse
 * besides -- a day still to come, a time out before the time in -- it says itself, at its field (VISIT_INVALID).
 */
const visitOf = (form) => {
    const visit = {};
    const errors = {};
    if (!form.raNumber.trim()) errors.raNumber = 'The R/A number says which workplace was visited.';
    if (!form.dateOfVisit) errors.dateOfVisit = 'Choose the day of the visit.';
    for (const [key, field] of Object.entries(VISIT_FIELDS)) {
        const value = String(form[field] ?? '').trim();
        if (!value) continue;
        if (!COUNTS.has(key)) visit[key] = value;
        else if (/^\d+$/.test(value)) visit[key] = Number(value);
        else errors[field] = 'A whole number, or leave it empty.';
    }
    return { visit, errors };
};

/** A page of the workplaces a search by R/A number asks for: the number matches exactly, a name only by containing it. */
const RA_SEARCH = { page: 0, size: 50 };

/** The form as it opens: blank, but for what the Forms screen's criteria name (A60) -- read once, as it mounts. */
const BLANK = {
    organiserName: '', dateOfVisit: '', timeIn: '', timeOut: '', bank: '', branchDepartment: '', raNumber: '', area: '',
    postalAddress: '', physicalAddress: '', branchDepTelNo: '', branchDepFaxNo: '',
    branchDepManager: '', branchRepName: '', cellPhoneEmail1: '', memnetMemName: '',
    cellPhoneEmail2: '', becMemberName: '', cellPhoneEmail3: '', complement: '',
    noOfNonMembers: '', membership: '', noOfNewMembersRecruited: '',
    percentagePenetration: '', noOfNonMembersInterviewed: '', organiserSignature: '',
    organiserSignatureDate: '', snrOrganiserSignature: '', snrOrganiserSignatureDate: '',
    adminDeptSignature: '', dateCaptured: '',
};
const openedWith = (filters) => ({
    ...BLANK,
    bank: filters?.bankName || '',
    branchDepartment: filters?.branch || '',
    raNumber: filters?.randomAccessNumber || '',
    area: filters?.area || '',
});

/** What a submitted visit says it changed, where it found another staff complement. */
const complementChange = (answer) => (answer?.complementBefore == null ? ''
    : ` Staff complement changed from ${formatInt(answer.complementBefore)} to ${formatInt(answer.complementFound)}.`);

/*
 * 🔴 THE SOLID TEAL BAND IS GONE. Each section used to be announced by a filled
 * `bg-brand-hover` bar with white small capitals across it -- three of them down the
 * form. The Card world has exactly one filled brand surface, the primary button, and
 * one place the palette is allowed to be material, the membership card; a form's
 * section head is neither. card.css's `.fieldgroup__title` is what this is: small
 * capitals in muted ink, with a hairline separating one group from the next.
 */
const Section = ({ title, children }) => (
    <div className="mt-5 border-t border-line pt-[18px] first:mt-0 first:border-t-0 first:pt-0">
        <h3 className="mb-3.5 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">{title}</h3>
        {/* 📱 R-RESP-O: PHONE-FIRST, and this form is the reason the rule exists.
            It is an organiser's site-visit report -- filled in standing up, in a
            branch -- and it had NO breakpoint at all: a hard `grid-cols-2`, so every
            field was half of 390px, roughly 170px, on the device it is actually used
            on. One column is the DEFAULT here and two is the enhancement, which is
            what "phone-first" means as opposed to a desktop layout with a phone
            exception bolted on. */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">{children}</div>
    </div>
);

/**
 * 🔴 `embedded` EXISTS BECAUSE THIS REPORT HAS TWO HOMES. It is the route
 * `/reports/organizer-report`, and it is also what `Forms` renders when you pick either
 * Branch Form -- and inside `Forms` it brought a second page gutter and a second `<h1>`
 * with it. Two `<h1>`s on one route is the exact defect `headingContract.test.jsx` guards,
 * and that test cannot see this one because reaching it takes a change of select.
 *
 * Embedded, the report is only its sheet, captioned by the `<h2>` letterhead it already
 * had -- which is the shape `GeneralForm` uses in the same picker.
 *
 * 💾 OCTOBER BUILD -- SAVED AS A DRAFT, AND SUBMITTED (5 Oct Show & Tell). An organiser who may capture visits
 * (`VISITS_CAPTURE`) saves the form as a draft, comes back to it from My visits, and submits it: the visit then goes on
 * SASBO's record and the form is read-only, to be printed. Nobody signed in keeps the one-step Save visit the form had,
 * while sign-in is open -- everything that works today still works (contracts/signin.md) -- with a line saying that
 * signing in brings drafts and submitting; a signed-in user without the permission is told what they need. A draft
 * that is not wanted is discarded (October build review), once the organiser has said so; a submitted visit never is.
 */
export const OrganizerReport = ({ filters, embedded = false }) => {
    /*
     * ✅ A60, CLOSED (MO22, with A.1). The Bank, Branch, Area and R/A No the Forms screen asks for SEED this form, read
     * once as it mounts -- never by an effect, which would overwrite what the user had typed each time a criterion
     * changed. `Forms.jsx` remounts it, with a `key`, only when it opens it for other criteria: another workplace.
     */
    const [formData, setFormData] = useState(() => openedWith(filters));
    const [errors, setErrors] = useState({});
    const [notice, setNotice] = useState(null);
    const [saving, setSaving] = useState(false);
    // The form as it stood when it was saved: Save visit rests until something on it changes, so one visit is one save.
    const [savedAs, setSavedAs] = useState(null);
    // October build: the draft the form holds -- `{ id, version, status, workplaceName, raNumber }` -- or none yet; the
    // form as last saved or opened, against which "changed since" is judged; which action is under way; a draft asked
    // to be opened over unsaved changes; and a nudge for My visits to read itself again.
    const [draft, setDraft] = useState(null);
    const [baseline, setBaseline] = useState(() => formData);
    const [busy, setBusy] = useState(null);
    const [opening, setOpening] = useState(null);
    const [discarding, setDiscarding] = useState(false);
    const [listed, setListed] = useState(0);
    const noticeRef = useFocusOn(notice, notice?.tone === 'error');
    const { status: authStatus, can } = useAuth();
    const location = useLocation();
    const capture = can(PERMISSIONS.VISITS_CAPTURE);
    const signedIn = authStatus === 'signed-in';
    const readOnly = draft?.status === DRAFT_STATUS.SUBMITTED;
    const changed = formData !== baseline;

    // `useCallback` with no dependencies: `setFormData`'s updater form reads the previous
    // state, so this handler never needs to close over `formData` and never has to change.
    const handleChange = useCallback((e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        // A field that is changed is no longer the one a refusal named.
        setErrors((prev) => (prev[name] ? { ...prev, [name]: undefined } : prev));
    }, []);

    /*
     * 💾 SAVE VISIT (A.1): the workplace is found by the form's R/A number -- the workplaces' search, the row whose
     * number IS it -- and the visit is recorded against it. Nothing is sent while a field is wrong, and a refusal is
     * said where it belongs: VISIT_INVALID at the field the server names, WORKPLACE_UNKNOWN at the R/A number, the form
     * kept as typed. A failure that is not a refusal may have landed, and is said so (`writeFailure`) -- never "not
     * saved". Its failure goes to the sink; a success says nothing to any console (the old `console.log` of the whole
     * form is why: `Auth.jsx`'s defect again).
     */
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (saving) return;
        const { visit, errors: invalid } = visitOf(formData);
        setErrors(invalid);
        setNotice(null);
        if (Object.keys(invalid).length) return;
        setSaving(true);
        const ra = formData.raNumber.trim();
        let sent = false;
        try {
            const found = await workplaceService.getWorkplaces({ ...RA_SEARCH, search: ra });
            const workplace = found?.content?.find?.((row) => String(row.raNumber) === ra);
            if (!workplace) {
                setErrors({ raNumber: 'No workplace with that R/A number is on record here.' });
                return;
            }
            sent = true;
            const saved = await recordVisit(workplace.id, visit);
            setSavedAs(formData);
            setNotice({
                tone: 'ok',
                text: `Visit saved for ${workplace.name} (R/A ${workplace.raNumber}).${complementChange(saved)}`,
            });
        } catch (error) {
            reportError('LocationVisit.save', error);
            const { code, details } = error?.body ?? {};
            const field = code === VISIT_INVALID ? VISIT_FIELDS[details?.field] : code === WORKPLACE_UNKNOWN && 'raNumber';
            if (field) setErrors({ [field]: messageOf(error) });
            else {
                setNotice({
                    tone: 'error',
                    text: sent
                        ? writeFailure(error, 'The visit', 'the workplace’s visits on the Workplaces screen')
                        : `The workplace could not be looked up, so nothing was saved. ${messageOf(error)}`,
                });
            }
        } finally {
            setSaving(false);
        }
    };

    /** A saved draft, into the form: every field it keeps, the rest of the form as it opened. */
    const show = (saved, text) => {
        const form = { ...openedWith(filters), ...formFieldsOf(saved) };
        setFormData(form);
        setBaseline(form);
        setDraft(saved);
        setErrors({});
        setOpening(null);
        setDiscarding(false);
        setNotice(text ? { tone: 'ok', text } : null);
    };

    /**
     * A refusal of a draft's save or submission, said where it belongs: at the field it names; at the R/A number for a
     * workplace that is none; as a notice offering the draft as it now stands where it was saved since or submitted
     * already; as a notice for anything else -- "could not be confirmed" where it may have landed, never "not saved".
     */
    const refused = (error, what) => {
        reportError('LocationVisit.draft', error);
        const { code, details } = error?.body ?? {};
        const field = code === VISIT_INVALID ? DRAFT_FIELDS[details?.field] : code === WORKPLACE_UNKNOWN && 'raNumber';
        if (field) {
            setErrors({ [field]: messageOf(error) });
            return;
        }
        if (code === VISIT_DRAFT_STALE || code === VISIT_ALREADY_SUBMITTED) {
            setNotice({ tone: 'error', text: messageOf(error), reopen: true });
            return;
        }
        if (code === VISIT_DRAFT_NOT_FOUND) {
            setDraft(null);
            setNotice({ tone: 'error', text: `${messageOf(error)} Save draft keeps this form as a new draft.` });
            return;
        }
        setNotice({ tone: 'error', text: writeFailure(error, what, 'My visits') });
    };

    /** Saves the form as a draft -- the first time, or over the version it holds. The draft as saved, or `null`. */
    const saveDraft = async ({ submitting }) => {
        const { draft: body, errors: invalid } = draftOf(formData, { submitting });
        setErrors(invalid);
        setNotice(null);
        if (Object.keys(invalid).length) return null;
        try {
            const saved = draft?.id
                ? await updateDraft(draft.id, { ...body, version: draft.version })
                : await createDraft(body);
            setDraft(saved);
            setBaseline(formData);
            setListed((n) => n + 1);
            return saved;
        } catch (error) {
            refused(error, 'The draft');
            return null;
        }
    };

    const handleSaveDraft = async (e) => {
        e?.preventDefault?.();
        if (busy || readOnly) return;
        setBusy('save');
        try {
            const saved = await saveDraft({ submitting: false });
            if (saved) setNotice({ tone: 'ok', text: `Draft saved for ${saved.workplaceName} (R/A ${saved.raNumber}).` });
        } finally {
            setBusy(null);
        }
    };

    /**
     * SUBMIT VISIT: the form is saved first where it changed since its last save (or was never saved), then submitted
     * at that version -- the visit goes on record and the form becomes read-only. A submission refused leaves the draft
     * saved, and says why at the field.
     */
    const handleSubmitVisit = async () => {
        if (busy || readOnly) return;
        setBusy('submit');
        try {
            let current = draft;
            if (!current?.id || changed) {
                current = await saveDraft({ submitting: true });
                if (!current) return;
            } else {
                const { errors: invalid } = draftOf(formData, { submitting: true });
                setErrors(invalid);
                if (Object.keys(invalid).length) return;
            }
            const submitted = await submitDraft(current.id, current.version);
            setDraft(submitted);
            setBaseline(formData);
            setListed((n) => n + 1);
            setNotice({
                tone: 'ok',
                text: `Visit submitted for ${submitted.workplaceName} (R/A ${submitted.raNumber}). It is on record now, `
                    + `and this form can be printed but not changed.${complementChange(submitted)}`,
            });
        } catch (error) {
            refused(error, 'The visit');
        } finally {
            setBusy(null);
        }
    };

    /** The draft as it now stands on the server -- after a save from elsewhere, or a submission. */
    const reopen = async () => {
        if (!draft?.id) return;
        try {
            const fresh = await getDraft(draft.id);
            show(fresh, fresh.status === DRAFT_STATUS.SUBMITTED
                ? 'This visit has been submitted: it is shown as it was submitted, read-only.'
                : 'The draft is shown as it was last saved.');
        } catch (error) {
            refused(error, 'The draft');
        }
    };

    /** A draft from My visits, into the form -- asked first where the form has changes not yet saved. */
    const open = (chosen) => {
        if (changed && !readOnly) {
            setOpening(chosen);
            return;
        }
        show(chosen, chosen.status === DRAFT_STATUS.SUBMITTED
            ? `Showing the visit submitted for ${chosen.workplaceName}: read-only, to print.`
            : `Opened the draft for ${chosen.workplaceName} (R/A ${chosen.raNumber}).`);
    };

    const startAgain = () => {
        const form = openedWith(filters);
        setFormData(form);
        setBaseline(form);
        setDraft(null);
        setErrors({});
        setNotice(null);
        setOpening(null);
        setDiscarding(false);
    };

    /**
     * DISCARD DRAFT (October build review): the draft the form holds is deleted at the version it holds, once the
     * organiser has said so, and the form starts again. Saved since or submitted elsewhere, it is offered as it now is
     * (`refused`); a submitted visit is SASBO's record, and the server never discards one.
     */
    const handleDiscard = async () => {
        if (busy || readOnly || !draft?.id) return;
        setBusy('discard');
        try {
            await discardDraft(draft.id, draft.version);
            const gone = draft;
            startAgain();
            setListed((n) => n + 1);
            setNotice({ tone: 'ok', text: `Draft discarded for ${gone.workplaceName} (R/A ${gone.raNumber}).` });
        } catch (error) {
            setDiscarding(false);
            const code = error?.body?.code;
            if (code === VISIT_DRAFT_STALE || code === VISIT_ALREADY_SUBMITTED || code === VISIT_DRAFT_NOT_FOUND) {
                refused(error, 'The draft');
            } else {
                reportError('LocationVisit.discard', error);
                setNotice({
                    tone: 'error',
                    text: writeMayHaveLanded(error)
                        ? 'Discarding the draft could not be confirmed. Reload My visits and check before trying again.'
                        : `The draft was not discarded. ${messageOf(error)}`.trim(),
                });
            }
        } finally {
            setBusy(null);
        }
    };

    // One object per render, so a keystroke reaches all 31 fields -- which is what a
    // controlled form does anyway. What must NOT change per render is the component
    // identity, and that is now fixed at module scope above.
    const scope = useMemo(() => ({ values: formData, errors, onChange: handleChange, readOnly }),
        [formData, errors, handleChange, readOnly]);
    const next = encodeURIComponent(`${location.pathname}${location.search}`);

    const sheet = (
        <FormScope.Provider value={scope}>
            <div className="mt-5 max-w-4xl rounded-lg bg-surface p-6 shadow-(--shadow-1)">
                {/* The form's own letterhead, kept because this IS a paper form that gets
                    printed -- but as a hairline and quiet ink rather than a 2px brand rule.
                    Nothing else in this world underlines itself in the brand's colour. */}
                <div className="mb-6 border-b border-line pb-4 text-center">
                    {/* 🔴 THIS WAS AN <h1>, AND IT IS THE SUBTLER HALF OF N8. The page's
                        actual title was an <h2> above it while this line of form chrome --
                        `text-sm`, uppercase, 14px -- was the document's top-level heading.
                        A screen-reader user jumping by heading landed here and was told the
                        most important thing on the page was the form's letterhead. It is
                        the form's name, so it is a heading, but a SECOND-level one under
                        the page title PageTitle now supplies. */}
                    {/* 🗣️ SASBO, 18 Sep 2026: this letterhead is the line that confused the people filling the
                        form in -- "it says on top ... organizers, branch or department report form". The
                        report is of a VISIT to a location, so that is what it is called now. */}
                    <h2 className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-muted">
                        SASBO Finance Union — Location Visit: Branch / Department
                    </h2>
                    {draft && (
                        <p className="no-print mt-2 text-sm text-ink-muted">
                            {readOnly ? 'Submitted visit — read-only' : 'Draft'} · {draft.workplaceName} · R/A {draft.raNumber}
                        </p>
                    )}
                </div>
                {/* 🔴 CLEAR FORM RESET THE BOXES AND NOT THE FORM. A native reset puts each control back to its
                    default and tells React nothing, so the state kept every value the boxes no longer showed --
                    harmless while the form saved nothing, and a visit saved from stale values once it did. */}
                <form
                    onSubmit={capture ? handleSaveDraft : handleSubmit}
                    onReset={() => {
                        setFormData(draft ? baseline : openedWith(filters));
                        setErrors({});
                        setNotice(null);
                    }}
                >
                    <Section title="General Data">
                        <Field label="Organiser Name" name="organiserName" />
                        <Field label="Date of Visit" name="dateOfVisit" type="date" />
                        <Field label="Time In" name="timeIn" type="time" />
                        <Field label="Time Out" name="timeOut" type="time" />
                        <Field label="Bank" name="bank" />
                        <Field label="Branch / Department" name="branchDepartment" />
                        <Field label="R / A Number" name="raNumber" />
                        <Field label="Area" name="area" />
                        <Field label="Postal Address" name="postalAddress" fullWidth textarea />
                        <Field label="Physical Address" name="physicalAddress" fullWidth textarea />
                        <Field label="Branch / Dep Tel No" name="branchDepTelNo" type="tel" />
                        <Field label="Branch / Dep Fax No" name="branchDepFaxNo" type="tel" />
                        {/* What Save visit keeps, said beside the fields it does not: they are people's details. On
                            screen only -- the paper form prints as it always has. */}
                        <p className="no-print text-sm text-ink-muted sm:col-span-2">
                            The day, who visited, the times and the counts are saved with the visit. The contact
                            details are printed with the form and not saved.
                        </p>
                        <Field label="Branch / Dep Manager" name="branchDepManager" fullWidth />
                        <Field label="Branch Rep Name" name="branchRepName" />
                        <Field label="Cell Phone & E-Mail" name="cellPhoneEmail1" />
                        <Field label="Memnet Mem Name" name="memnetMemName" />
                        <Field label="Cell Phone & Email" name="cellPhoneEmail2" />
                        <Field label="BEC Member Name" name="becMemberName" />
                        <Field label="Cell Phone & Email" name="cellPhoneEmail3" />
                    </Section>

                    <Section title="Membership (After Visit)">
                        {/* 🗣️ "Complement", the staff of the branch -- spelt "Compliment" here until the owner promised
                            the fix at the 5 Oct Show & Tell. */}
                        <Field label="Complement" name="complement" />
                        <Field label="No of Non-Members" name="noOfNonMembers" type="number" />
                        <Field label="Membership" name="membership" />
                        <Field label="No of New Members Recruited" name="noOfNewMembersRecruited" type="number" />
                        <Field label="Percentage Penetration" name="percentagePenetration" />
                        <Field label="No of Non-Members Interviewed" name="noOfNonMembersInterviewed" type="number" />
                    </Section>

                    <Section title="Distribution">
                        <Field label="Organiser Signature" name="organiserSignature" />
                        <Field label="Date" name="organiserSignatureDate" type="date" />
                        <Field label="Snr Organiser Signature" name="snrOrganiserSignature" />
                        <Field label="Date" name="snrOrganiserSignatureDate" type="date" />
                        <Field label="Admin Dept Signature" name="adminDeptSignature" fullWidth />
                        <Field label="Date Captured" name="dateCaptured" type="date" />
                    </Section>

                    {/* The outcome, beside the buttons that asked: a confirmation announced, a failure that takes
                        focus -- with the draft as it now stands, where it was saved since or submitted already. On
                        screen only. */}
                    {notice && (
                        <PageNotice
                            ref={noticeRef}
                            id="visit-notice-title"
                            tone={notice.tone}
                            className="no-print"
                            onDismiss={() => setNotice(null)}
                        >
                            {notice.text}
                            {notice.reopen && (
                                <>
                                    {' '}
                                    <Button size="sm" onClick={reopen}>Open it as it is now</Button>
                                </>
                            )}
                        </PageNotice>
                    )}

                    {opening && (
                        <div role="alert" className="no-print mt-4 rounded-md border border-line-strong bg-surface-2 p-3 text-sm text-ink">
                            <p>
                                The form has changes that are not saved. Open {opening.workplaceName} anyway, and lose them?
                            </p>
                            <div className="mt-2 flex gap-2">
                                <Button size="sm" onClick={() => show(opening, `Opened the ${opening.status === DRAFT_STATUS.SUBMITTED ? 'visit' : 'draft'} for ${opening.workplaceName}.`)}>
                                    Open anyway
                                </Button>
                                <Button size="sm" variant="ghost" onClick={() => setOpening(null)}>Keep editing</Button>
                            </div>
                        </div>
                    )}

                    {discarding && draft && (
                        <div role="alert" className="no-print mt-4 rounded-md border border-line-strong bg-surface-2 p-3 text-sm text-ink">
                            <p>
                                Discard the draft for {draft.workplaceName} (R/A {draft.raNumber})? A discarded draft cannot
                                be brought back.
                            </p>
                            <div className="mt-2 flex gap-2">
                                <Button size="sm" variant="danger" loading={busy === 'discard'} onClick={handleDiscard}>
                                    Discard it
                                </Button>
                                <Button size="sm" variant="ghost" disabled={busy === 'discard'}
                                    onClick={() => setDiscarding(false)}>
                                    Keep the draft
                                </Button>
                            </div>
                        </div>
                    )}

                    {!capture && (
                        <p className="no-print mt-4 text-sm text-ink-muted">
                            {signedIn ? (
                                'You need permission to capture visits. Ask your administrator if you should have it. The form can still be printed.'
                            ) : (
                                <>
                                    Sign in to save visits as drafts and submit them.{' '}
                                    <Link className="font-medium text-brand-ink underline" to={`/login?next=${next}`}>Sign in</Link>
                                </>
                            )}
                        </p>
                    )}

                    {/* The actions sit where every other surface in this world puts them:
                        at the end of the sheet, to the right, primary last. */}
                    <div className="no-print mt-6 flex flex-wrap justify-end gap-2 border-t border-line pt-4">
                        {readOnly ? (
                            <Button type="button" variant="default" onClick={startAgain}>Start a new visit</Button>
                        ) : (
                            <Button type="reset" variant="default">Clear form</Button>
                        )}
                        <Button type="button" variant="default" onClick={() => window.print()}>Print form</Button>
                        {capture && !readOnly && (
                            <>
                                {draft?.id && (
                                    <Button type="button" variant="ghost" disabled={Boolean(busy) || discarding}
                                        onClick={() => setDiscarding(true)}>
                                        Discard draft
                                    </Button>
                                )}
                                <Button type="submit" variant="default" loading={busy === 'save'}
                                    disabled={busy === 'submit' || busy === 'discard' || (draft && !changed)}>
                                    Save draft
                                </Button>
                                <Button type="button" variant="primary" loading={busy === 'submit'}
                                    disabled={busy === 'save' || busy === 'discard'} onClick={handleSubmitVisit}>
                                    Submit visit
                                </Button>
                            </>
                        )}
                        {/* The form's one primary while nobody is signed in, where "Submit report" sent nothing
                            anywhere (A60): one visit, one save. */}
                        {!capture && !signedIn && (
                            <Button type="submit" variant="primary" loading={saving} disabled={savedAs === formData}>
                                Save visit
                            </Button>
                        )}
                    </div>
                </form>
            </div>
        </FormScope.Provider>
    );

    // My visits: the signed-in organiser's own drafts and submissions, to finish or to print.
    const mine = capture && !embedded ? (
        <MyVisitDrafts refreshKey={listed} openId={draft?.id} onOpen={open} />
    ) : null;

    if (embedded) return sheet;

    return (
        <Page>
            <PageTitle
                title="Location Visit"
                subtitle="Record an organiser's visit to a branch or department: the engagement, the membership, and the recruitment."
            />
            {mine}
            {sheet}
        </Page>
    );
};
