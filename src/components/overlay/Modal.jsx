import * as Dialog from '@radix-ui/react-dialog';
import { createContext, useContext, useEffect, useRef } from 'react';
import { X } from 'lucide-react';

import { cn, pick } from '../../lib/utils';
import Button from '../form/Button';

/**
 * THE ONE OVERLAY. `03.4`.
 *
 * 🔴 **WHAT IT REPLACES: EIGHT HAND-ROLLED OVERLAYS IN FOUR FILES**, re-derived from source
 * on 2026-09-09 (contract ⑰ — the plan said nine; the grep says eight, and the count in a
 * plan expires like every other):
 *
 *   `Recruiters.jsx` **:437 · :526 · :574 · :729 · :850** · `Members.jsx` **:513** ·
 *   `AcbDebits.jsx` **:227** · `BankBranchModal.jsx` **:87**
 *
 * Every one is a bare `fixed inset-0` div. **None is portalled**, so each is trapped in
 * whatever `overflow` and `z-index` context its page happens to have. **None traps focus**,
 * so Tab walks straight out of the dialog and into the page behind it — which the user
 * cannot see and cannot get back from. **One of the eight handles Escape.** They disagree
 * about `z-index` (`z-10 · 20 · 50 · [60]`), about the backdrop, and about whether the page
 * behind them scrolls.
 *
 * 🔑 **MIGRATING THEM IS A BEHAVIOUR CHANGE, AND THAT IS THE POINT** (BR-3). Focus trap,
 * Escape, scroll lock and a portal are things these surfaces do not have today. They are
 * also the state-gated surfaces `07.2` has to drive, and driving eight inconsistent
 * hand-rolled overlays is strictly worse than driving one primitive.
 *
 * ---
 *
 * 📱 **THE HEIGHT CAP IS BAKED IN, IN `dvh`, AND IT IS NOT NEGOTIABLE PER CALL SITE**
 * (conventions §6, contract ⑮). `100vh` on a phone is the viewport with the URL bar
 * *retracted*, so a `vh`-sized dialog is taller than the screen and its footer — where Save
 * lives — sits under the browser chrome, unreachable. MobiZone found two call-sites that had
 * "forgotten" their cap and fixed it by baking it into the primitive. This is that fix,
 * before the forgetting.
 *
 * ⚠️ **THERE IS NO `className` PROP, AND THAT IS THE POINT.** It was there, and it made the
 * paragraph above a lie: `cn` merges the caller's classes last, and `twMerge` resolves
 * `max-h-*` last-wins, so `<Modal className="max-h-[90vh]">` silently restored the exact bug
 * the height cap exists to prevent -- and `max-h-[90vh]` is literally what `Members.jsx`
 * carried before this migration. No call site passed one, so removing it costs nothing and
 * turns "not negotiable per call site" from a comment into a fact.
 *
 * 🔴 **A TITLE IS REQUIRED.** Radix warns about a missing `Dialog.Title` and then renders
 * anyway; this throws in development. A dialog with no accessible name is announced as
 * "dialog" and nothing else, and the user has no idea what just took over the screen. Where
 * a design genuinely wants no visible heading, pass `titleHidden` — the name still exists,
 * it is just `sr-only`, which is a different thing from not having one.
 */

/**
 * `onOpenChange` from a plain close handler.
 *
 * Radix reports both directions; every dialog in this application only ever needs the
 * closing one, so eleven call sites had written `(next) => { if (!next) close(); }` by hand.
 * One adapter, so the shape is stated once and a call site reads as what it means.
 */
export const closeOn = (close) => (next) => {
  if (!next) close();
};

/** Whether a write started from the enclosing dialog is running -- `Modal`'s `busy`, for the controls inside it. */
const BusyContext = createContext(false);

/**
 * A footer control that CLOSES the dialog, and is disabled while its write runs (RF; R2-F3's other half).
 *
 * 🔴 `busy` SHUT EVERY WAY OUT BUT THE ONES A CALLER DREW. Escape, a press outside and the X are the primitive's, and
 * it closed them; a footer's own Cancel/Close called its screen's setter directly, so each caller had to disable it by
 * hand -- `ConfirmDialog` did, the ACB banking editor did, and the welcome letter shipped without it until P10's
 * inventory found it. This reads `busy` from the dialog it sits in and closes through Radix, so the close takes the
 * same path as the X -- through `Modal`'s own guard -- and no caller can forget.
 *
 * Everything else is `Button`'s: the variant is the caller's, so a footer keeps exactly the button it drew.
 */
export function ModalClose({ disabled = false, children, ...props }) {
  const busy = useContext(BusyContext);
  return (
    <Dialog.Close asChild>
      <Button disabled={busy || disabled} {...props}>{children}</Button>
    </Dialog.Close>
  );
}

const SIZES = {
  sm: 'max-w-sm',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
};

export default function Modal({
  open,
  onOpenChange,
  title,
  titleHidden = false,
  description,
  size = 'md',
  footer,
  children,
  /** Escape and a click outside are ON by default. Off only for a step you must answer. */
  dismissible = true,
  /**
   * 🔴 A WRITE STARTED FROM THIS DIALOG IS RUNNING (R2-F3). Every way out is shut until it answers -- Escape, a press
   * outside, the X, and any `onOpenChange(false)` Radix would send -- so the result or the failure lands on the
   * surface the user is still looking at, and a second attempt cannot overlap the first. Nothing moves: the X stays
   * where it is, disabled, and only while the write is in flight.
   */
  busy = false,
  /**
   * 🔴 WHICH CONTROL GETS THE CARET WHEN THIS DIALOG OPENS (`10.4`). Pass a ref and that element is focused;
   * omit it and Radix's default stands, which focuses the first tabbable thing in the content -- the header's
   * Close button, because the header renders before `{children}`.
   *
   * ⚠️ IT REPLACES `autoFocus`, WHICH IS NOT THE SAME THING WRITTEN DIFFERENTLY. `autoFocus` worked here only by
   * a timing accident: React focuses in the COMMIT phase and Radix's focus scope runs in a PASSIVE effect
   * afterwards, finds focus already placed inside the content, and leaves it. That is invisible, version-
   * dependent, and `jsx-a11y/no-autofocus` is an error as of this phase. This says the same thing out loud,
   * through the mechanism Radix provides for it.
   */
  initialFocusRef,
}) {
  if (import.meta.env?.DEV && !title) {
    throw new Error(
      'Modal requires a `title`. Radix warns about a missing Dialog.Title and renders ' +
        'anyway, so the failure is a dialog announced to a screen reader as "dialog" and ' +
        'nothing else — the user cannot tell what has taken over the screen. If the design ' +
        'has no visible heading, pass `titleHidden` and keep the name.',
    );
  }

  // One blocker, passed three times, rather than the same arrow written three times.
  const block = dismissible && !busy ? undefined : (event) => event.preventDefault();
  const changeOpen = (next) => {
    if (busy && !next) return;
    onOpenChange?.(next);
  };

  /**
   * 🔴 ESCAPE BELONGS TO A POPUP OPEN INSIDE THE DIALOG FIRST (R2-F1, R2-F2). Radix takes Escape on the document in the
   * CAPTURE phase, before a calendar or a list inside the dialog can hear it -- so the key meant to close the calendar
   * closed the dialog, and everything typed into it. While a combobox or a popup trigger inside is expanded, the
   * dialog lets that control have the key; the next Escape, with nothing open, closes the dialog as before. Scoped to
   * popups on purpose: an expanded table row is not one, and Escape must still close a dialog that holds one.
   */
  const contentRef = useRef(null);
  const onEscapeKeyDown = (event) => {
    if (contentRef.current?.querySelector('[role="combobox"][aria-expanded="true"], [aria-haspopup][aria-expanded="true"]')) {
      event.preventDefault();
      return;
    }
    block?.(event);
  };

  /**
   * 🔴 FOCUS RESTORE, WHICH RADIX COULD NOT DO FOR US HERE — AND FAILED SILENTLY.
   *
   * Radix returns focus to `Dialog.Trigger` on close. This primitive is deliberately
   * CONTROLLED (`open` / `onOpenChange`) and renders no trigger, because the nine surfaces
   * it replaces open from rows, menu items and toolbar buttons that are nowhere near the
   * dialog. So `triggerRef.current` was always `null`, Radix had nothing to focus, and
   * focus fell to `<body>` — on every dialog in the application.
   *
   * What that costs: closing a dialog puts a keyboard user at the top of the document, so
   * the next Tab walks the skip link and the entire navigation tree again. They lose their
   * place on the page every time they open and close a record.
   *
   * ⚠️ THE TRAP HAD BEEN TESTED SINCE P3 AND THIS HAD NOT, which is why it survived: both
   * halves look identical from the outside — the dialog opens, the dialog closes — and
   * only the second one is felt after it has closed.
   */
  const restoreFocusTo = useRef(null);
  useEffect(() => {
    if (open) restoreFocusTo.current = document.activeElement;
  }, [open]);

  const returnFocus = (event) => {
    const target = restoreFocusTo.current;
    // Only if it is still in the document: a row action whose row has since been removed
    // is not somewhere to put focus, and Radix's fallback to `<body>` is right for that.
    if (target && document.contains(target) && typeof target.focus === 'function') {
      event.preventDefault();
      target.focus();
    }
  };

  return (
    <BusyContext.Provider value={busy}>
    <Dialog.Root open={open} onOpenChange={changeOpen}>
      <Dialog.Portal>
        {/* Portalled to the body, so no page's `overflow` or stacking context can clip it —
            which is the single thing all eight hand-rolled overlays got wrong. */}
        <Dialog.Overlay
          className={cn(
            'fixed inset-0 z-50 bg-ink/50 backdrop-blur-xs',
            'data-[state=open]:animate-in data-[state=open]:fade-in-0',
            'data-[state=closed]:animate-out data-[state=closed]:fade-out-0',
            'motion-reduce:animate-none',
          )}
        />

        {/* 🔴 CENTRED BY A GRID, NEVER BY A TRANSFORM (R2-F1). The content used to sit at `left-1/2 top-1/2` pulled back
            by `-translate-1/2`, and a transformed element is the containing block for every `position: fixed` inside
            it -- so a calendar could not be placed inside the dialog, was portalled to the body instead, and landed
            outside the dialog's focus trap, pointer layer and Escape handling. The wrapper passes pointer presses
            through to the overlay, so a press outside the content still dismisses. */}
        <div className="pointer-events-none fixed inset-0 z-50 grid place-items-center">
        <Dialog.Content
          ref={contentRef}
          // 🔑 A CONDITIONAL SPREAD, not a ternary. Radix warns when a dialog has no
          // Description, and the tempting silencer — an `sr-only` Description holding the
          // title — makes a screen reader announce the name TWICE ("Member details. Member
          // details."), which is what the test for `titleHidden` caught.
          //
          // ⚠️ `aria-describedby={undefined}` and OMITTING the prop are not the same thing
          // here: Radix spreads the caller's props AFTER its own wiring, so a present key
          // whose value is `undefined` REMOVES the attribute (that is the documented way to
          // say "no description"), while an absent key leaves Radix's own `describedby`
          // intact. A ternary between `undefined` and `undefined` would have done neither —
          // it always passes the key, and would have silently broken the association on
          // every dialog that DOES have a description.
          {...(description ? {} : { 'aria-describedby': undefined })}
          onEscapeKeyDown={onEscapeKeyDown}
          onPointerDownOutside={block}
          onInteractOutside={block}
          // 🔑 A TERNARY IS SAFE HERE, unlike the `aria-describedby` spread above, and the
          // difference is worth stating because the two sit ten lines apart. That one is an
          // ARIA attribute, where a present key holding `undefined` REMOVES Radix's own
          // wiring; this is an event handler, where an absent handler and an `undefined` one
          // are the same thing, so the default behaviour is genuinely untouched when no ref
          // is passed. The must-pass in `Modal.initialFocus.test.jsx` is what proves it.
          onOpenAutoFocus={
            initialFocusRef
              ? (event) => {
                  event.preventDefault()
                  initialFocusRef.current?.focus()
                }
              : undefined
          }
          onCloseAutoFocus={returnFocus}
          className={cn(
            'relative z-50 w-[calc(100%-2rem)]',
            // 📱 `dvh`, never `vh`. The cap and the internal scroll are the primitive's, so
            // a long form cannot push its own Save button off the bottom of a phone.
            'flex max-h-[85dvh] flex-col overflow-hidden',
            'rounded-card border border-line bg-surface shadow-2',
            'focus:outline-none',
            'data-[state=open]:animate-in data-[state=open]:fade-in-0 data-[state=open]:zoom-in-95',
            'data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95',
            'motion-reduce:animate-none',
            pick(SIZES, size, 'Modal size'),
          )}
        >
          <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
            <div className="min-w-0">
              <Dialog.Title
                className={cn('text-lg font-semibold text-ink', titleHidden && 'sr-only')}
              >
                {title}
              </Dialog.Title>
              {description && (
                <Dialog.Description className="mt-1 text-sm text-ink-muted">
                  {description}
                </Dialog.Description>
              )}
            </div>

            {dismissible && (
              <Dialog.Close asChild>
                <button
                  type="button"
                  aria-label="Close"
                  disabled={busy}
                  className={cn(
                    'touch-target -mr-1 shrink-0 rounded-md p-1.5 text-ink-faint',
                    'hover-hover:hover:bg-surface-2 hover-hover:hover:text-ink',
                    'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring',
                  )}
                >
                  <X aria-hidden="true" className="size-4" />
                </button>
              </Dialog.Close>
            )}
          </div>

          {/* The BODY scrolls, not the dialog — so the header and the footer stay put and
              the actions are always reachable. */}
          <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-4">
            {children}
          </div>

          {footer && (
            <div className="flex flex-wrap items-center justify-end gap-2 border-t border-line bg-surface-2 px-5 py-3">
              {footer}
            </div>
          )}
        </Dialog.Content>
        </div>
      </Dialog.Portal>
    </Dialog.Root>
    </BusyContext.Provider>
  );
}

/**
 * THE DESTRUCTIVE CONFIRM. `03.4`.
 *
 * 🔴 **IT REPLACES FOUR `window.confirm()` CALLS** — `RecruitmentChannel.jsx:71`,
 * `Members.jsx:360`, `Recruiters.jsx:294` and `Recruiters.jsx:346`.
 *
 * `window.confirm` is not merely ugly. It is **unstyleable**, it says "OK"/"Cancel" rather
 * than naming what will happen, it **blocks the whole browser thread**, and it is
 * suppressible — a user who ticks *"don't show me these again"* silently loses every
 * confirmation in the application, including the one that deletes a recruiter and sets
 * fifteen members inactive.
 *
 * 🧪 ⚠️ **AND IT IS LOAD-BEARING IN THE AUTOMATION SUITE.** Their `MemberRegisterPage` and
 * `MemberCapturePage` carry `page.on('dialog')` handlers. **The day these dialogs vanish
 * those steps pass for the wrong reason** — a handler for an event that never fires is
 * green forever. That is named in the brief that goes to the automation arc, and it is why
 * this migration is theirs to know about rather than ours to do quietly.
 *
 * 🔑 **THE CONFIRM BUTTON NAMES THE ACTION**, never "OK". *"Remove recruiter"* is a
 * sentence a user can check against what they meant to do; "OK" is not.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  onConfirm,
  destructive = true,
  loading = false,
  children,
}) {
  return (
    <Modal
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      description={description}
      size="sm"
      // The confirm's own write is the dialog's: while it runs, nothing closes it (R2-F3).
      busy={loading}
      footer={
        <>
          <ModalClose variant="ghost">{cancelLabel}</ModalClose>
          <Button variant={destructive ? 'danger' : 'primary'} loading={loading} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {children}
    </Modal>
  );
}
