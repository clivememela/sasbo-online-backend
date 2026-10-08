import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';

import { cn } from '../../lib/utils';
import { formatDateLong } from '../../lib/format';
import { personName } from '../../lib/names.js';
import Button from '../form/Button';
import MembershipCard from './MembershipCard';
import { PANEL_SLOT_ID } from '../shellIds';

/**
 * THE RECORD PANEL — the third column, and the thing the contract was written around.
 * card.html's `.panel`, transcribed.
 *
 * 🗣️ [`directionContract.md`](../../../../planning/branches/design/directionContract.md):
 *
 *     THESIS   "Every member is a card; the register is the wallet that hands you that
 *               card. The page exists to put one member's card in your hand and the record
 *               beneath it, not to show a dashboard."
 *     REFUSES  "A modal for tasks that need neither interruption nor protected focus:
 *               the record edits in place."
 *
 * 📐 **IT IS A COLUMN OF THE SHELL, NOT OF THE PAGE.** The mock's `.app` grid is
 * rail | main | panel, so the panel stands beside the topbar rather than under it. The
 * page renders this component where it likes; the component portals itself into the
 * shell's slot (`PANEL_SLOT_ID`) when the shell is there, and renders in place when it is
 * not -- the tests mount pages without the shell, and a panel that disappeared in a test
 * would be a panel nobody could assert on.
 *
 * 📐 **THREE CONTAINERS, ONE SET OF FIELDS** *(R-RESP-M)*:
 *
 *   `xl` and up  a real third column, `clamp(424px, 32vw, 448px)` (`DOCKED_WIDTH`), full height, own scroll.
 *   below `xl`   a drawer from the right, `min(448px, 100vw)`, with a scrim; hidden at rest.
 *
 * ⚠️ **THE DOCKING EDGE IS `xl` (1280px), NOT THE MOCK'S ORIGINAL 1361** — R-RESP-D, a
 * measured correction: the commonest Windows laptop is 1366 with a 17px scrollbar showing,
 * which is **1349** — under the old edge, so the panel would have overlaid the register on
 * the exact machine most of this union's staff use. card.css carries the same 1280 now.
 *
 * 🔑 **THE EMPTY STATE IS THE WALLET'S EMPTY SLOT, NOT A BLANK COLUMN.** A ghost card in
 * the card's own proportion, what will appear here, how to make it appear, and the keys.
 *
 * 🎬 **THE ARRIVAL.** When a member is chosen the card is drawn from the wallet and laid on
 * the desk (580ms, the one animation allowed past the 400ms budget), a band of light sweeps
 * the metal, and the record's fields rise under it in a capped stagger. It plays once per
 * member, never on re-render, and reduced motion collapses it to a fade (index.css).
 */

const ARRIVAL_MS = 900;

/**
 * 📐 **THE DOCKED WIDTH -- ONE RULE FOR BOTH ITS USERS.** The column and the body it clips are held at the same width,
 * which is what makes the collapse a slide rather than a reflow (see the body below); two copies of it drift the day
 * one is edited.
 *
 * 🗣️ **THE FLOOR IS 424px, NOT 360** -- the owner, 2026-09-21: the panel *"may be a little too thin -- maybe we can
 * widen it a bit"*. On his own screen, a 1280x661 CSS viewport, it was 410px: 32vw, because a 360px floor never binds
 * at `xl`. 424px is the widest it can be there and cost the register NOTHING. The register shares the row and reflows
 * off its own width (the `register` container, index.css), and at 1280 with a record open it turns into the phone's
 * stacked rows past a 429px panel -- measured in Chromium with this app's thin scrollbar reserved (10px in Chrome).
 * Nothing else moves: 32vw passes 424px at 1325px, and the ceiling stays 448px because at 1440 the register keeps its
 * Bank column only up to a 449px panel.
 *
 * ⚠️ A panel much wider than this on a laptop is a trade, not a number -- the register stacking while a record is open,
 * or the 256px rail giving some of itself back. Both are the owner's to choose.
 */
const DOCKED_WIDTH = 'xl:w-[clamp(424px,32vw,448px)]';

/**
 * @param {{
 *   member: object|null,
 *   open: boolean,
 *   onClose: () => void,
 *   children?: React.ReactNode,
 *   footer?: React.ReactNode,
 *   remarks?: string,
 * }} props
 */
export default function MemberPanel({ member, open, onClose, children, footer }) {
  const scrollRef = useRef(null);
  const asideRef = useRef(null);
  const headingRef = useRef(null);
  const returnFocusRef = useRef(null);
  const memberKey = member?.id ?? member?.membershipNumber ?? null;

  /*
   * The arrival plays when the MEMBER changes, and the record scrolls back to the top.
   *
   * Without the scroll reset, opening a second member from halfway down the first one's
   * record drops you into the middle of a stranger's employment details -- the panel is a
   * sticky column, so nothing else resets it. Without the key, every keystroke in the edit
   * form would re-land the card.
   *
   * The class is set on the element rather than held in state, exactly as the mock's
   * `arrive()` does it: the animation is a fact about this DOM node for 900ms, and a
   * re-render in that window must neither restart it nor be caused by it.
   */
  useEffect(() => {
    if (memberKey === null) return undefined;
    // `scrollTop`, not `scrollTo()`: the property exists on every element in every
    // environment this renders in, and the method does not (jsdom has no `scrollTo`).
    if (scrollRef.current) scrollRef.current.scrollTop = 0;
    const aside = asideRef.current;
    if (!aside) return undefined;
    aside.classList.remove('panel-arriving');
    void aside.offsetWidth;
    aside.classList.add('panel-arriving');
    const timer = setTimeout(() => aside.classList.remove('panel-arriving'), ARRIVAL_MS);
    return () => {
      clearTimeout(timer);
      aside.classList.remove('panel-arriving');
    };
  }, [memberKey]);

  /*
   * 🔴 OPENING A RECORD MOVED NO FOCUS, AND CLOSING IT DROPPED FOCUS TO THE PAGE (`06.5`, the `06.3` critique, F-P55).
   * A keyboard user who chose a member stayed on the row, with the record they asked for somewhere to the right; a
   * screen reader was told nothing had opened; and closing it lost their place in a register of thousands. Focus goes
   * to the record's heading when a member arrives, and back to whatever held it when the record closes.
   *
   * The scrim below is marked `aria-hidden` and taken out of the tab order for the same reason: it was a second
   * "Close member details" button for a keyboard and a screen reader, beside the real one in the record's head.
   * `preventScroll`, because the record is a column already in view. The aside's role and name are untouched -- the
   * automation arc finds the record as the complementary region "Member details".
   */
  useEffect(() => {
    if (!open || memberKey === null) return;
    const active = document.activeElement;
    if (active && active !== document.body && !asideRef.current?.contains(active)) returnFocusRef.current = active;
    headingRef.current?.focus({ preventScroll: true });
  }, [open, memberKey]);

  useEffect(() => {
    if (open) return;
    const target = returnFocusRef.current;
    returnFocusRef.current = null;
    if (target?.isConnected) target.focus({ preventScroll: true });
  }, [open]);

  const updated = member?.dateLastChanged ?? member?.updatedAt ?? null;
  const meta = member
    ? [
        member.branch,
        member.employeeNumber && (
          <span key="emp">
            Employee no. <span className="font-medium tabular-nums text-ink">{member.employeeNumber}</span>
          </span>
        ),
        member.joinDate && `joined ${formatDateLong(member.joinDate)}`,
      ].filter(Boolean)
    : [];

  const panel = (
    <>
      {/* The scrim exists ONLY below the docking edge. Docked, the panel is a column of the
          page and a scrim over the register would be a lie about what is interactive. */}
      {member && (
        <button
          type="button"
          aria-label="Close member details"
          aria-hidden="true"
          tabIndex={-1}
          onClick={onClose}
          className={cn(
            'fixed inset-0 z-30 bg-ink/25 transition-opacity duration-(--duration-panel) ease-(--ease-out) xl:hidden',
            'motion-reduce:transition-none',
            open ? 'opacity-100' : 'pointer-events-none opacity-0',
          )}
        />
      )}

      {/* 🔴 THE COLUMN CLOSES WHEN THERE IS NOTHING IN IT -- the owner, 2026-09-10, looking at
          a screenshot of the empty slot: *"Is it not better to rather close that right hand
          bar instead of showing an empty card slot?"*

          It used to hold a dashed card outline and an invitation, permanently, on a column
          `clamp(360px, 32vw, 448px)` wide. Two things were wrong with that. It is a picture of
          absence taking up a third of the desk -- the wallet's empty slot was a nice idea and
          a nice idea is not a reason to spend 448px on nothing. And it cost the REGISTER that
          width: the register's columns are chosen by a container query, so at 1440 it was
          reading ~680px and dropping to its narrow ladder, which is why the desk showed four
          columns where the phone showed six. Closing the column gives both back.

          It COLLAPSES rather than unmounting, so the width can be animated: an instant 448px
          reflow of the register underneath it is the kind of jump this design does not make.
          `overflow-hidden` clips the content during the slide -- safe here because `DateField`
          portals its calendar, and the panel's own scroller already clips anything else. */}
      <aside
        ref={asideRef}
        aria-label={open ? 'Member details' : undefined}
        aria-hidden={open ? undefined : 'true'}
        data-testid="member-panel"
        data-open={open ? 'true' : undefined}
        className={cn(
          'flex min-w-0 flex-col border-line bg-surface',
          // ── docked: a real third column, and no column at all when it is empty ──
          'xl:sticky xl:top-0 xl:h-dvh xl:shrink-0 xl:overflow-hidden xl:shadow-none',
          'xl:transition-[width] xl:duration-(--duration-panel) xl:ease-(--ease-out)',
          open ? `${DOCKED_WIDTH} xl:border-l` : 'xl:w-0 xl:border-l-0',
          // ── drawer: everything below xl. It SLIDES now rather than being switched off:
          //    `hidden` cut the drawer out of the layout on the same frame the record was
          //    cleared, so the phone's close was the one movement in the application with no
          //    motion at all. It is out of the layout again as soon as there is no record.
          'max-xl:fixed max-xl:inset-y-0 max-xl:right-0 max-xl:z-40 max-xl:w-[min(448px,100vw)]',
          'max-xl:border-l max-xl:shadow-(--shadow-2)',
          'max-xl:transition-transform max-xl:duration-(--duration-panel) max-xl:ease-(--ease-out)',
          open ? 'max-xl:translate-x-0' : 'max-xl:translate-x-full',
          !member && 'max-xl:hidden',
          'motion-reduce:transition-none',
        )}
      >
        {/* 🔴 THE RECORD LEAVES WITH THE COLUMN. Rendered on `open`, it was cleared on the
            same frame the collapse began, so a 380px animation played over an empty white
            box -- the panel did not slide away, it emptied and then shrank. It renders on
            `member` instead, and `Members.jsx` holds the member for exactly this long.

            📐 THE FIXED WIDTH IS WHAT MAKES IT A SLIDE. Without it the body is a flex child
            of a box whose width is animating, so every line of the record re-wraps sixty
            times a second on the way out. Held at the column's own width and clipped by the
            aside, it travels instead of reflowing. */}
        {member && (
          <div
            className={cn(
              'flex h-full min-h-0 flex-col max-xl:w-full',
              DOCKED_WIDTH,
              'transition-opacity duration-(--duration-panel) ease-(--ease-out) motion-reduce:transition-none',
              open ? 'opacity-100' : 'opacity-0',
            )}
          >
            <div ref={scrollRef} className="scroll-quiet min-h-0 flex-1 overflow-y-auto px-6 pb-6 pt-3.5">
              <div className="mb-3 flex h-9 items-center justify-between">
                <p
                  ref={headingRef}
                  tabIndex={-1}
                  className="rounded-sm text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-ring"
                >
                  Member record
                </p>
                <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close member details">
                  <X size={18} strokeWidth={1.75} aria-hidden="true" />
                </Button>
              </div>

              {member && (
                <MembershipCard
                  name={personName(member) || '—'}
                  membershipNumber={member.membershipNumber}
                  status={member.status}
                  since={member.joinDate}
                  employer={member.bankName}
                />
              )}

              {meta.length > 0 && (
                // 🔴 EACH FRAGMENT IS UNBREAKABLE, and the line breaks BETWEEN them. In a
                // 400px column this used to wrap wherever the words ran out, so the record
                // opened with "joined 04 Jan" on one line and "2022" alone on the next --
                // a date cut in half under the member's own card. `text-pretty` only moved
                // the break by a word; a branch name, an employee number and a date are each
                // one fact, and a fact does not wrap in the middle.
                <p className="record-rise mx-0.5 mt-3.5 text-sm text-ink-muted">
                  {meta.map((part, index) => (
                    <span key={index}>
                      {index > 0 && ' · '}
                      <span className="whitespace-nowrap">{part}</span>
                    </span>
                  ))}
                </p>
              )}

              {children}
            </div>

            <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-line bg-surface px-6 py-3">
              {/* The comp's foot says when the record last changed. With no date to say, it says
                  nothing: its old fallback repeated the membership number already stamped on the
                  card a few inches above (`06.3`). */}
              <p className="text-[12.5px] text-ink-muted">
                {updated ? `Updated ${formatDateLong(updated)}` : ''}
              </p>
              {footer && <div className="flex flex-wrap items-center gap-2">{footer}</div>}
            </footer>
          </div>
        )}
      </aside>
    </>
  );

  const slot = typeof document !== 'undefined' ? document.getElementById(PANEL_SLOT_ID) : null;
  return slot ? createPortal(panel, slot) : panel;
}
