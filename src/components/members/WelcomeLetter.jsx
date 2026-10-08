import { useState } from 'react';
import { Send, CheckCircle2, AlertCircle, Loader2 } from 'lucide-react';
import Modal, { ModalClose, closeOn } from '../overlay/Modal';
import Button from '../form/Button';
import { memberService } from '@/services/memberService.js';
import { formatDate } from '@/utils/validateSAID.js';
import useWelcomeLetterEmail from '@/hooks/useWelcomeLetterEmail.js';
import { LETTER_OUTCOMES, UNKNOWN_OUTCOME, outcomeOf } from './welcomeLetterOutcome.js';

/** The notice tones the dialog uses: the world's status colours, as the membership-number notice already had them. */
const TONE = {
    pending: 'border-status-pending-dot bg-status-pending-bg text-status-pending',
    warning: 'border-status-suspended-dot bg-status-suspended-bg text-status-suspended',
};

/**
 * Opened by Capture Member's Save: the letter the member will receive, and the button that captures them.
 *
 * Renders the Sasbo Welcome Letter content with all member fields filled in,
 * matching the placeholders in the .docx template. "Send Welcome Letter" saves
 * the member and asks the server to email them the letter.
 *
 * 🔴 A64: SAVING NO LONGER DEPENDS ON THE LETTER. The server saves first and then
 * says what happened to the letter (`welcomeLetter`: `LETTER_OUTCOMES`), so a
 * member with no email address, a server with no mail (dev.sasbo.online), and a
 * failed send all leave the member captured -- and the dialog says which it was.
 * Before Send is pressed it already says whether a letter will go at all
 * (`useWelcomeLetterEmail`). The button keeps its name: the capture screen and the
 * automation suite both find it by it, and it still sends wherever mail is set up.
 *
 * 🔑 THE CARD'S WORLD, BY RULE. card.html drew no letter, so the preview takes the world's materials rather than a
 * page of its own: the sheet is `surface` on the dialog's `bg`, ruled with `line`, its ink `ink` and `ink-muted`, in
 * the application's type -- in both themes. The words are the template's, including its sign-off, so the preview
 * says what the member will receive.
 */
export default function WelcomeLetter({ open, onClose, member, onSuccess, onFailed }) {
    const [sendState, setSendState] = useState('idle'); // idle | sending | done | error
    const [errorMessage, setErrorMessage] = useState('');
    // What the server said happened to the letter, and the member as it saved them -- a retry sends for THAT record.
    const [outcome, setOutcome] = useState(null);
    const [saved, setSaved] = useState(null);
    const emailing = useWelcomeLetterEmail();

    /*
     * 🔴 A51: ONE SEND STATE PER MEMBER. CaptureMember keeps this dialog mounted and swaps its member, so a state that
     * is never reset carried "sent" onto the next member captured in the same visit -- whose dialog opened with no
     * Send button. Reset when the member changes, during render (React's pattern for state derived from a prop),
     * never on a re-render of the same member.
     */
    const memberKey = member?.id ?? (member ? (member.identityNumber || member.surname || 'new') : null);
    const [stateFor, setStateFor] = useState(memberKey);
    if (stateFor !== memberKey) {
        setStateFor(memberKey);
        setSendState('idle');
        setErrorMessage('');
        setOutcome(null);
        setSaved(null);
    }

    if (!member) return null;

    const fullName = [member.title, member.firstNames, member.surname]
        .filter(Boolean).join(' ');

    const today = new Date().toLocaleDateString('en-ZA', {
        day: 'numeric', month: 'long', year: 'numeric',
    });

    /*
     * 🔴 A52: NOTHING ASSIGNS A MEMBERSHIP NUMBER YET (A54, the server's), so a newly captured member has none and the
     * emailed letter goes out with the space blank. The preview said "—", which reads as a number to come. It now
     * says, before Send is pressed, that the letter carries no number.
     *
     * Once this dialog has saved the member, the server has given them a number (03.4), and the saved record is what
     * the letter shows -- the first sight of the number a clerk gets, on the screen that captured them.
     */
    const membershipNo = saved?.membershipNumber || member.membershipNumber || '';

    const preferredLabel = (() => {
        const pref = member.preferredEmail;
        if (pref === 'both') {
            return [member.personalEmail, member.workEmail].filter(Boolean).join(' and ') || '—';
        }
        if (pref === 'work') return member.workEmail || '—';
        return member.personalEmail || member.workEmail || '—';
    })();

    /*
     * 🔴 A56, THEN A64: A LETTER WITH NOWHERE TO GO IS NEVER PROMISED -- AND NEVER BLOCKS THE SAVE. A56 found the dialog
     * reading "emailed as an attachment to —" beside an enabled Send, and its fix disabled Send for a member with no
     * address. Since Send is also the SAVE, that left such a member with no way to be captured at all (A64); SASBO's
     * register holds members with no address. Send stays enabled, and the dialog says no letter will go.
     */
    const hasAddress = Boolean(member.personalEmail || member.workEmail);

    // A member already on record -- given one, or saved by this dialog -- is SENT a letter; otherwise Send CAPTURES.
    const onRecord = saved?.id ? saved : (member.id ? member : null);
    const done = sendState === 'done';
    const result = done ? (LETTER_OUTCOMES[outcome] ?? UNKNOWN_OUTCOME) : null;
    const canSend = !done || result.retry;

    const handleSend = () => {
        setSendState('sending');
        setErrorMessage('');

        if (onRecord) {
            memberService.sendWelcomeLetter(onRecord.id)
                .then(() => {
                    setOutcome('SENT');
                    setSendState('done');
                })
                .catch((err) => {
                    setErrorMessage(err?.message || 'The letter could not be emailed. Please try again.');
                    setSendState('error');
                });
            return;
        }

        memberService.createMember(member, { sendWelcome: true })
            .then((savedMember) => {
                setSaved(savedMember ?? null);
                setOutcome(outcomeOf(savedMember));
                setSendState('done');
                onSuccess?.(savedMember || member);
            })
            .catch((err) => {
                setErrorMessage(err?.message || 'The member was not saved. Please try again.');
                setSendState('error');
                // Said here, in the dialog it happened in -- and handed back, so a refusal of one of the form's own
                // fields (01.3's WORKPLACE_UNKNOWN) is also said at that field when the dialog closes.
                onFailed?.(err);
            });
    };

    return (
        <Modal
            open={open}
            onOpenChange={closeOn(onClose)}
            // 🔴 R2-F3, THE ONE WRITE-BEARING DIALOG IT MISSED (found by P10's component inventory). While the letter
            // is sending, every way out is shut, so the result lands on the dialog the user is still looking at.
            busy={sendState === 'sending'}
            title="Welcome Letter Preview"
            size="xl"
            footer={
                <>
                    <ModalClose variant="ghost">Close</ModalClose>
                    {done && (
                        <span role="status" className="flex items-center gap-2 text-sm font-medium text-status-active">
                            <CheckCircle2 className="size-4" aria-hidden="true" />
                            {result.status}
                        </span>
                    )}
                    {canSend && (
                        <Button
                            variant="primary"
                            onClick={handleSend}
                            disabled={sendState === 'sending'}
                        >
                            {sendState === 'sending'
                                ? <Loader2 className="mr-1.5 size-4 animate-spin" aria-hidden="true" />
                                : <Send className="mr-1.5 size-4" aria-hidden="true" />}
                            {/* A capture is a SAVE first, whatever happens to the letter after it. */}
                            {sendState === 'sending' ? (onRecord ? 'Sending…' : 'Saving…') : 'Send Welcome Letter'}
                        </Button>
                    )}
                </>
            }
        >
            {/* What will happen -- or, once the member is saved, what did. Never a status role: the footer holds the one. */}
            {saved || done ? (
                <LetterOutcome result={result ?? LETTER_OUTCOMES.FAILED} outcome={outcome} to={preferredLabel} />
            ) : !hasAddress ? (
                <Notice tone="pending">
                    This member has no email address, so no letter will be sent. Send Welcome Letter still saves them.
                </Notice>
            ) : emailing === 'off' ? (
                <Notice tone="pending">
                    Emailing is not switched on yet, so this letter will not be sent. Send Welcome Letter saves the member.
                </Notice>
            ) : emailing === 'on' ? (
                <p className="mb-4 text-sm text-ink-muted">
                    This letter will be emailed as an attachment to{' '}
                    <strong className="text-ink">{preferredLabel}</strong>
                    {member.preferredEmail === 'both' && ' (both addresses)'}.
                </p>
            ) : (
                <p className="mb-4 text-sm text-ink-muted">
                    Send Welcome Letter saves the member and, where emailing is switched on, sends this letter to{' '}
                    <strong className="text-ink">{preferredLabel}</strong>.
                </p>
            )}

            {!membershipNo && (
                <p className="mb-4 flex items-start gap-2 rounded-lg border border-status-pending-dot bg-status-pending-bg p-3 text-sm text-status-pending">
                    <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                    <span>This member has no membership number yet, so the letter will be sent with that space blank.</span>
                </p>
            )}

            {sendState === 'error' && (
                <div role="alert" data-send-error className="mb-4 flex items-start gap-2 rounded-lg border border-status-suspended-dot bg-status-suspended-bg p-3 text-sm text-status-suspended">
                    <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                    <span>{errorMessage}</span>
                </div>
            )}

            {/* Letter preview — mirrors the .docx layout */}
            <div data-testid="welcome-letter" className="rounded-lg border border-line bg-surface p-5 text-sm leading-relaxed text-ink sm:p-8">

                {/* Header: date + address block */}
                <div className="mb-6 flex flex-wrap items-start justify-between gap-x-6 gap-y-2">
                    <div className="min-w-0 space-y-0.5">
                        <p>{fullName}</p>
                        <p>{member.address || ''}</p>
                    </div>
                    <p className="sm:text-right">{today}</p>
                </div>

                {/* Membership number banner */}
                <div className="mb-6 rounded-md bg-surface-2 px-4 py-2 text-center font-semibold">
                    Your Sasbo Membership No:{' '}
                    {membershipNo
                        ? <span className="text-brand-ink">{membershipNo}</span>
                        : <span className="font-normal text-ink-muted">(no membership number yet)</span>}
                </div>

                {/* Salutation */}
                <p className="mb-4">Dear <strong>{fullName}</strong>,</p>

                {/* Body */}
                <p className="mb-3">
                    Welcome to Sasbo, thank you for joining the most progressive Union in the financial
                    services sector. We trust it will be a long and happy relationship as we continue to
                    make a positive difference in the lives of our members.
                </p>
                <p className="mb-3">
                    Now that you are a Sasbo member, be assured of the Unions' responsible protection and
                    support while being entitled to many money saving benefits. Your membership number is
                    enclosed at the top of the home page for your convenience. Please keep it safe as you
                    would need the membership number to appreciate the benefits our Union has to offer you.
                </p>
                <p className="mb-3">
                    In the meantime, we have so much more to share to ensure that you are completely
                    informed about our services, benefits and partnerships we have established for your
                    benefit. Please refer to the enclosed member onboarding information sheet for your
                    individual use.
                </p>
                <p className="mb-3">
                    We encourage you to play an active role at Sasbo's Annual General Meetings and to
                    participate in the branch committee in your area. We wish a long and happy relationship
                    with us.
                </p>

                {/* Member details summary -- label and value pairs, so a definition list rather than a table element, which
                    would be one more outside TableScroller (contract ⑮); these rows never need a sideways scroll. */}
                <dl className="my-6 grid grid-cols-[8rem_minmax(0,1fr)] text-sm sm:grid-cols-[11rem_minmax(0,1fr)]">
                        {[
                            ['Full Name',        fullName],
                            ['Identity Number',  member.identityNumber || '—'],
                            ['Date of Birth',    member.dateOfBirth ? formatDate(member.dateOfBirth) : '—'],
                            ['Employee Number',  member.employeeNumber || '—'],
                            ['Bank',             member.bankName || '—'],
                            ['Payment Type',     member.paymentType || '—'],
                            ['Date Joined',      member.joinDate ? formatDate(String(member.joinDate).split('T')[0]) : today],
                            ['Status',           member.status || 'Active'],
                        ].map(([label, value], i, rows) => {
                            const rule = i < rows.length - 1 ? 'border-b border-line' : '';
                            return [
                                <dt key={`${label}-term`} className={`py-1.5 pr-4 font-medium text-ink-muted sm:pr-6 ${rule}`}>{label}</dt>,
                                <dd key={`${label}-value`} className={`break-words py-1.5 ${rule}`}>{value}</dd>,
                            ];
                        })}
                </dl>

                {/* Sign-off -- the template's own words (Sasbo Welcome Letter.docx), which is what the member receives. */}
                <p className="mt-6">Your Faithfully,</p>
                <p className="mt-1 font-semibold">Ms. Rosemary M. Rauleka</p>
                <p className="text-ink-muted">General Secretary: Sasbo the Finance Union</p>
            </div>
        </Modal>
    );
}

/** Once the member is saved: where the letter went, or -- for every other outcome -- why it did not. */
function LetterOutcome({ result, outcome, to }) {
    if (outcome === 'SENT') {
        return (
            <p className="mb-4 text-sm text-ink-muted">
                The letter was emailed as an attachment to <strong className="text-ink">{to}</strong>.
            </p>
        );
    }
    return <Notice tone={result.tone === 'warning' ? 'warning' : 'pending'}>{result.says}</Notice>;
}

function Notice({ tone, children }) {
    return (
        <p className={`mb-4 flex items-start gap-2 rounded-lg border p-3 text-sm ${TONE[tone]}`}>
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>{children}</span>
        </p>
    );
}
