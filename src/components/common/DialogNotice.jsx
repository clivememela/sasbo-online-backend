import { AlertCircle, CheckCircle2 } from 'lucide-react';

import Button from '../form/Button';
import { cn } from '../../lib/utils';

/**
 * What a notice wears for its tone. The page's notice and `DialogNotice` spelt out the same two tones and the same two
 * icons, character for character; the frame around each differs, and stays with it.
 */
export function noticeTone(tone) {
    return tone === 'ok'
        ? 'border-status-active-dot bg-status-active-bg text-status-active'
        : 'border-status-suspended-dot bg-status-suspended-bg text-status-suspended';
}

/**
 * The page's own notice -- a write's outcome, left on the page once no dialog is open to hold it (RF). AcbDebits and
 * Recruitment Channel each drew this box by hand, class for class, beside Recruiters' copy built on `noticeTone`; the
 * three now share one. It takes focus through the caller's ref (`useFocusOn`), and is labelled by its own sentence.
 *
 * A confirmation is a `status` and is announced; a failure has no live role because it TAKES FOCUS, and a focused
 * element's label is read on arrival -- a live region as well would announce one failure twice.
 */
export function PageNotice({ ref, id, tone = 'error', className, onDismiss, children }) {
    return (
        <div
            ref={ref}
            tabIndex={-1}
            {...(tone === 'ok' && { role: 'status' })}
            aria-labelledby={id}
            className={cn(
                'mt-4 flex items-start gap-2 rounded-lg border p-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring',
                noticeTone(tone),
                className,
            )}
        >
            <NoticeIcon tone={tone} />
            <p id={id} className="flex-1">{children}</p>
            <Button size="sm" variant="ghost" onClick={onDismiss}>Dismiss</Button>
        </div>
    );
}

export function NoticeIcon({ tone }) {
    return tone === 'ok'
        ? <CheckCircle2 aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={1.75} />
        : <AlertCircle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" strokeWidth={1.5} />;
}

/**
 * A failure said INSIDE the dialog it happened in. `06.4` on Recruiters; lifted here by P9's R4-F1 on its second and
 * third consumers (Recruitment Channel's delete, Manage Debits' banking update), per conventions §11.
 *
 * 🔴 WHY IT EXISTS: a page-level notice written while a dialog is open renders UNDER the dialog's scrim, and its focus
 * effect pulls focus out of the dialog, which Radix's trap pulls straight back. The dialog in front of the user says
 * nothing, and the explanation appears only once they give up and close it.
 *
 * `role="alert"` rather than the page notice's focus: the dialog owns focus, and pulling it out of a form the user is
 * still filling in would be worse than the silence this replaces.
 *
 * @param {{ notice: { tone?: 'ok' | 'error', message: string } | null, onDismiss: () => void }} props
 */
export default function DialogNotice({ notice, onDismiss }) {
    if (!notice) return null;
    return (
        <div
            role={notice.tone === 'ok' ? 'status' : 'alert'}
            className={cn('mb-4 flex items-start gap-2 rounded-lg border p-3 text-sm', noticeTone(notice.tone))}
        >
            <NoticeIcon tone={notice.tone} />
            <p className="flex-1">{notice.message}</p>
            <Button size="sm" variant="ghost" onClick={onDismiss}>Dismiss</Button>
        </div>
    );
}
