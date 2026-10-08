#!/usr/bin/env node
/**
 * The shipped-bundle ceiling.
 *
 * WHY THIS EXISTS
 * This phase adds msw, axe-core, RTL, jsdom and vitest to devDependencies. A
 * test suite must prove it did not move the shipped product, and "devDependency"
 * is a claim about intent, not a measurement -- one stray import from a test
 * helper into a component and the tooling ships to every member's browser.
 *
 * And once the number exists it keeps earning: a later feature that doubles the
 * bundle now has something to fail against, which is the half nobody builds
 * because on the day you write it there is nothing wrong.
 *
 * WHY IT IS NOT A VITEST TEST
 * It needs `dist/`, which only exists after a build. Inside the suite it would
 * have to either skip when dist is absent -- and this repository does not skip
 * tests for convenience -- or fail on a clean checkout. So it is its own
 * command. It also deliberately does NOT hook `npm run build`: never add work
 * to a name someone already runs.
 *
 * Run:  npm run build && npm run budget
 *
 * MEASURED 2026-09-04 on the phase/03.1 branch, Vite 7.3.3, in KiB as this
 * script counts them (Vite prints decimal kB, which is why its numbers read
 * about 2% larger -- the two are not disagreeing):
 *   dist/assets/index-*.js         502.08 KiB   (gzip 141.12 KiB)
 *   dist/assets/logo-*.png         118.30 KiB
 *   dist/Tansparent Lady Robot.png 118.30 KiB   <-- register F8: the SAME image
 *                                                   again, copied through from
 *                                                   public/. 118 KiB shipped
 *                                                   twice on every first load.
 *   dist/assets/index-*.css         48.94 KiB
 *   dist/index.html                  0.50 KiB
 *                                  ----------
 *   total                          788.13 KiB
 *
 * RE-MEASURED 2026-09-09 on design/00-baseline at the close of the design arc's
 * P3, same script, after `npm run build`:
 *   entry js                       251.87 KiB   ceiling 350   OK
 *   javascript (all chunks)        602.24 KiB   ceiling 640   <-- RAISED, see below
 *   css                             60.63 KiB   ceiling  70   OK
 *   all assets                     783.61 KiB   ceiling 900   OK
 *   emitted files                  59
 *
 * ✅ F8 IS CLOSED, on measurement. `public/Tansparent Lady Robot.png` and
 * `src/assets/images/logo.png` were BYTE-IDENTICAL (sha256 3863ca94...), so the
 * build really was shipping 121 KiB twice -- 242 KiB of a 901 KiB bundle for one
 * picture. The favicon now points at the source asset and Vite emits it once:
 * all assets 901.10 -> 783.61 KiB. ⏳ The other half of R-PERF-D is still open --
 * a favicon does not need a 121 KiB full-colour photo -- and needs the owner's
 * asset rather than an invented one.
 *
 * 🔴 WHY `js` MOVED 600 -> 640, and what spent it: ONE lazy chunk, `Modal-*.js`
 * at 41 KiB -- `@radix-ui/react-dialog`, behind the design arc's `03.4` single
 * overlay primitive. It replaces EIGHT hand-rolled `fixed inset-0` divs across
 * four files, none portalled, none trapping focus, one handling Escape; the 41 KiB
 * IS the focus trap, the Escape handling, the scroll lock and the portal. It is
 * lazy, so the entry chunk did not move.
 *
 * ⚠️ THE RAISE IS THE LAST STEP, NOT THE FIRST. Reclaimed before asking: F8's
 * duplicate image (above), `react-day-picker` removed entirely in `03.3` on its
 * own measurement (81.91 KiB for the date chunk, all assets 933.65 KiB -- this
 * guard caught that one too), and three dependencies with zero consumers deleted
 * (`cn`, `radix-ui`, `class-variance-authority`). Agreed with the automation arc,
 * 2026-09-09, with these figures beside the ask; `entryJs`, `css`, `total` and
 * every floor are untouched.
 *
 * The ceilings below sit above those with headroom, for the same reason every
 * floor in this suite sits below its actual: a limit equal to the measurement
 * reddens for reasons nobody caused, and gets raised by whoever is unlucky
 * enough to hit it first.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import process from 'node:process'

import { FLOORS } from '../src/test/floors.js'

const KB = 1024

/**
 * Raise only with a reason recorded beside the change.
 *
 * The measurements these sit above are NOT repeated here: they belong to
 * `FLOORS.BUNDLE_*` in `src/test/floors.js`, which carries the actual and the
 * date. Two copies had already drifted by 0.03 KiB before anyone read them
 * together, which is the whole argument for one home.
 */
const CEILINGS = {
  // The entry chunk: what a first paint downloads before anything renders. Added by
  // 03.1, which cut it from 506 KiB to 287 by splitting every route. The ceiling is
  // deliberately BELOW the pre-split figure -- its job is to notice a route quietly
  // becoming a static import again, which would show up here and nowhere else.
  //
  // 2026-10-07, MEASURED, the ceiling unmoved: sign-in (contracts/signin.md) puts its shell in the entry, as OB17a allows
  // and no more -- a first load that cannot know who is signed in is not leaner, only broken. Run 37665352812 at 644e967:
  // entry js 335.45 KiB, 4.98 over main's 330.47 at cbd11dc (run 37017335321); vite's index 338.40 -> 343.50 kB, +5,100 B.
  // Module by module, each esbuild-minified alone at both SHAs (Rollup's hoisting makes the sum differ by 10 B):
  //   session.js +1,407 (the tab's session, read by api.js and the provider) · AuthContext +1,259 (who is signed in; its
  //   server talk is the lazy authServer) · api.js +914, and its 319 on main came out of a lazy chunk of its own (the one
  //   fetch wrapper that puts the token on every API request) · App +493 (the lazy routes of /change-password and the
  //   four Maintenance screens) · AppLayout +347 (required mode's way to /login; a temporary password's to
  //   /change-password) · lazyAccount +281 (ONE import() for the header's account, the rail's foot and the session
  //   notice) · navigation +262 (the entries' permission names, as literals) · Main +160 · main.jsx +123 (the provider,
  //   the wrapper) · useAuth +118 · next.js +115 · Header -271 and Sidebar -437 (the inert "Administrator" chip and the
  //   dead "Log out" link, gone for the lazy account). Every screen stays lazy.
  //
  // 2026-10-07, MEASURED, the ceiling unmoved: register paging (A10) leaves the entry at 330.49 KiB, +0.02 over main's
  // 330.47 at cbd11dc (run 37672789318 at 1d6e43b against 37017335321; vite's index 338.40 -> 338.42 kB, +20 B). The
  // only entry module the stream changes is Header.jsx, -127 B esbuild-minified alone (the search chip names what the
  // server's search finds); the rest, by elimination, is the dependency map the entry keeps for its lazy routes: Members
  // now shares `useDebounce` with Workplaces, so Rollup split it into a chunk of its own, and both routes' lists name it.
  entryJs: 350 * KB,
  // 600 -> 640 on 2026-09-09: see the re-measured table above. 6% over the 602.24
  // actual, for the reason the header gives -- a limit equal to the measurement
  // reddens for things nobody caused.
  //
  // 640 -> 650 on 2026-09-17, MEASURED, at P9's batch 2. The measurement build went
  // 639.42 -> 641.29 KiB (empty VITE_API_BASE_URL; the production env adds 0.06). What
  // spent it, chunk by chunk (`p9b2` build against `p9b0`):
  //   DialogNotice 0 -> 885 B  a NEW SHARED CHUNK. R4-F1 needed a failure said inside the
  //                            dialog on its second and third consumers, so Recruiters'
  //                            local copy was lifted to components/common per conventions
  //                            §11. Rollup gives a module three pages share its own chunk.
  //   circle-check 0 -> 174 B  the CheckCircle2 icon, split out for the same reason.
  //   Members +533 · RecruitmentChannel +333 · AcbDebits +329 · index +305  R3-F2's
  //                            kind-aware copy plus `writeMayHaveLanded`.
  //   Recruiters -642 · memberService -131 · api -60  what the lift and P9 batch 0 removed.
  // ⚠️ THE RAISE IS STILL THE LAST STEP. It was taken only after the above accounted for
  // every byte: the growth is one extraction that REMOVED a duplicate and ~1 KiB of the
  // 1.87 is Rollup's per-chunk overhead for sharing it, not product code. 1.4% of headroom
  // over the 641.29 actual -- the same margin the header argues for, and no more, so the
  // next feature still meets a real ceiling. The design arc owns this number: the ACB arc
  // raises it again, by exactly its screen's bytes, when its Runs screen lands.
  //
  // 650 -> 666.58 on 2026-09-19, MEASURED, at ACB `01.5` -- by exactly the screen's bytes,
  // as above. Both builds with the worktree's tracked `.env` (the production URL), so the
  // pair compares like with like: 67ad286 647.96 -> the Runs screen 664.54 KiB, +16.58
  // (under the empty-URL measurement env 67ad286 reads 646.13, so expect ~662.7 there).
  // What spent it, chunk by chunk:
  //   AcbRuns 0 -> 15,583 B    THE SCREEN: the runs list, preparing and its polling, the
  //                            run's panel with its debits and exclusions sheets, and the
  //                            run vocabulary. Lazy, like every route: nothing on the entry.
  //   SelectInput 0 -> 1,475 B · options 0 -> 912 B   NEW SHARED CHUNKS, not new code: a
  //                            second lazy route now imports the native select, so Rollup
  //                            lifts it out of useReferenceData (-1,440) and SearchableSelect
  //                            (-794) into chunks of their own.
  //   index +824 · Acb +76     the route and the two A.C.B. menu entries.
  //   Members +69 · Recruiters +69 · Subscriptions +69 · CaptureMember +74 · BankBranch +30
  //   · Forms +30              Rollup's import stubs for the new shared chunks.
  // No headroom added on top: the next feature meets a real ceiling, as the P9 entry argues.
  //
  // 666.58 -> 693.35 on 2026-09-21, MEASURED, after merging origin/main e70b675 into the ACB
  // branch (the planning pack's F61: the 666.58 above was set over 67ad286, a base main has
  // since left). Both builds from one install of e70b675's lock, the tracked `.env`:
  //   main e70b675 ON ITS OWN   676.60 KiB -- 26.60 OVER its own 650 ceiling. Not ACB's bytes:
  //                            8c6b675's welcome-letter preview and confirmation, e70b675's
  //                            MemberPanel, and the lock's react 19.3 / react-router 7.18.4 /
  //                            rollup 4.63.4. The design arc owns that number and was told
  //                            the same day; it is carried here, as measured, so that this
  //                            branch's ceiling measures ACB's own bytes and nothing else.
  //   the ACB branch            693.35 KiB, +16.75 over main, chunk by chunk as 01.5 found it:
  //                            AcbRuns 0 -> 15,775 B · SelectInput 0 -> 1,475 · options 0 -> 912
  //                            · useReferenceData -1,440 · SearchableSelect -794 · index +824
  //                            · Acb +76 · Members, Recruiters, Subscriptions +69 each
  //                            · CaptureMember +57 (+74 before the flip: debitDate.js's
  //                            forward-first rule is 17 B shorter) · BankBranch, Forms +30 each.
  // Still no headroom on top.
  //
  // 693.35 -> 687.33 on 2026-09-21, MEASURED, after merging origin/main 3a3cc3c (design's RF and
  // the panel width) into the ACB branch. Both builds from one install of the same lock, the
  // tracked `.env`, in C:\tmp\acb-client:
  //   main 3a3cc3c ON ITS OWN   670.27 KiB -- still over its own 650, and still the design arc's
  //                            number; RF took 6.33 KiB off e70b675's 676.60.
  //   the ACB branch (e3285a3)  687.33 KiB, +17.06 over main, chunk by chunk:
  //                            AcbRuns 0 -> 15,742 B · SelectInput 0 -> 1,475 · options 0 -> 912
  //                            · useReferenceData -1,440 · SearchableSelect -794 · index +873
  //                            · circle-alert 0 -> 251 and useFocusOn -214 (Rollup re-splitting a
  //                            shared icon and hook once the ACB route imports them)
  //                            · CaptureMember +131 (the flip, and the debit-date sentence's
  //                            capital letter) · Members +109 · Recruiters +104 · Acb +76
  //                            · Subscriptions +64 · Auth +40 · AcbDebits +35 · RecruitmentChannel +35
  //                            · Forms, BankBranch +30 each · AddressAutocomplete +5 · DialogNotice +2.
  // The ceiling comes DOWN, to the measurement: carrying e70b675's 676.60 would have left 6 KiB of
  // slack nobody earned. It is 687.34, not 687.33, because the measurement is 703,827 B -- 687.331
  // KiB -- and a ceiling written to the hundredth must round UP or it refuses the build it was
  // measured from (it did, by one byte). Still no headroom on top.
  //
  // 687.34 -> 708.23 on 2026-09-21, MEASURED, three builds from one install in C:\tmp\acb-client,
  // the tracked `.env`: e3285a3 703,827 B (the 687.33 above, reproduced to the byte) -> 650ea61
  // 707,242 B -> the 04.1 tree 725,224 B. The remote gate had already refused 650ea61 at 690.67.
  //   e3285a3 -> 650ea61, +3,415 B   AcbRuns +3,197 (01.8's calendar notice, 01.9's rate note, 02.7's
  //                                  made-on and make-by, 02.8's decline notes) · Acb +213 (04.2's
  //                                  honest rows) · DataState +5.
  //   650ea61 -> 04.1, +17,982 B     AcbDebits 8,338 -> 22,678 (the Re-Debits screen: the unpaids and
  //                                  their decisions, the held-over permissions, the held letters,
  //                                  the server-side search) · acbPaging 0 -> 8,409 and AcbRuns -4,819
  //                                  (the paging hook and pager, moved out of the Runs panel to be
  //                                  shared) · index +52.
  // 725,224 B is 708.227 KiB, so 708.23. Still no headroom on top.
  //
  // 708.23 -> 708.70 on 2026-09-21, MEASURED ON THE GATE'S RUNNER: the box had 574 MB free (commit
  // 42.5 of 46.5 GB), no room for a vite build beside the other sessions' work. A throwaway commit printed
  // every chunk's bytes from the CI build, runs 35666314519 and 35666314630. The method checked first:
  //   3e86cba (00.2) 725,059 B, 165 under the 04.1 tree, and its 69 unchanged chunks byte-identical to the
  //                  local build above; the five that moved are exactly 00.2's files: CaptureMember -159
  //                  · Recruiters -103 · Members +63 · format +43 · AcbDebits -9.
  //   accdfd1 (Capture's holiday sentence) 725,706 B, +647 over 3e86cba, chunk by chunk:
  //                  acbRunService 0 -> 2,671 and acbPaging -2,637 (Rollup gives the service a chunk of its
  //                  own once Capture imports it) · CaptureMember +487 (the calendar read, the caveat on
  //                  every unknown date) · index +44 · AcbDebits, AcbRuns +41 each.
  // 725,706 B is 708.697 KiB, so 708.70. Still no headroom on top.
  //
  // 650 -> 680 on 2026-09-21, MEASURED, at the design arc's close (670.22 KiB on the lockfile's own
  // packages). The SAME code measured 641.31 on React 19.2.6: `8c6b675` (Clive, 2026-09-21) regenerated the lockfile --
  // `package.json` untouched, 86 packages refreshed within their ranges -- and React came with it, 19.2.6 -> 19.3.0.
  // What spent it, chunk by chunk (the lockfile's packages against 19.2.6's):
  //   index         298.45 -> 327.13  +28.68  the entry chunk, where react-dom lives: its production client is
  //                                           523.5 KiB at 19.2.6 and 610.5 at 19.3.0
  //   index.modern   34.96 ->  35.74   +0.78
  //   Modal          40.85 ->  40.31   -0.54
  //   every other chunk within 200 B, 69 chunks both sides.
  // Over the wire: +8.5 KiB gzipped (+7.2 brotli) on the first load, cached after. The suite and the E2E pass on 19.3,
  // and 19.2.6 against 19.3.0 rendered 350/350 pixel-identical. Kept on the owner's word (2026-09-21): the cost is
  // small, and rolling React back alone would fight a refresh of 86 packages. Checked before raising, as the header
  // asks: nothing from src/test/ is imported by the product, and no test library is in the built bundle. 1.4% of
  // headroom, as the last raise had; the ACB arc still raises it by exactly its own bytes.
  //
  // 680 -> 718.43 on 2026-09-21, MEASURED, at the merge of client main f96e723 (design/12) into the ACB branch:
  // main's own ceiling raised by exactly ACB's bytes, as the paragraph above asks, so main keeps its 1.4% and
  // ACB brings none of its own. On the gate's runner, the method of 708.70's paragraph (run 35667059993):
  //   main f96e723 ON ITS OWN   686,361 B (670.274 KiB -- the 670.27 measured locally at 3a3cc3c, to the
  //                            hundredth: design/12 changed a test and this script, not the product)
  //   the ACB tree              725,706 B -- accdfd1's product, byte for byte, for the same reason
  //   ACB's own bytes           +39,345 B = 38.423 KiB, chunk by chunk: AcbDebits 8,303 -> 22,710 · AcbRuns
  //                            0 -> 14,161 · acbPaging 0 -> 5,772 · acbRunService 0 -> 2,671 · SelectInput
  //                            0 -> 1,475 · options 0 -> 912 · index +969 · CaptureMember +459 · Acb +289
  //                            · circle-alert 0 -> 251 · Members +172 · Subscriptions +64 · format +43
  //                            · Auth +40 · RecruitmentChannel +35 · BankBranch, Forms +30 each
  //                            · AddressAutocomplete, DataState +5 each · DialogNotice +2 · Recruiters +1
  //                            · SearchableSelect -794 · useReferenceData -1,440 · useFocusOn -214.
  // 680 + 38.423 = 718.423, so 718.43 (rounded UP, as 687.34's paragraph explains).
  //
  // 718.43 -> 722.63 on 2026-09-22, MEASURED ON THE GATE'S RUNNER, for ACB's completion (00.5 the account type,
  // 03.5 the termination report and the member's Terminated status): main's 680 plus ACB's bytes and nothing
  // else, as the paragraph above does. Two throwaway builds, the method of 708.70's paragraph:
  //   main 3dddaac ON ITS OWN   729,941 B (712.833 KiB -- quick wins' +4.136 over f96e723 spent design's
  //                            headroom, not ACB's, and is not ACB's to raise; run 35673286333)
  //   the completion (34da403)  734,244 B, +4,303 B over it, chunk by chunk (run 35673323129): AcbDebits
  //                            +3,451 (the termination report, the banking dialog's type, the unpaids'
  //                            disabled Suspend) · memberConstants
  //                            0 -> 3,173 and bankBranches -3,116 (Rollup gives the constants a chunk of their
  //                            own once AcbDebits imports ACCOUNT_TYPE_OPTIONS: +57 net) · CaptureMember +308
  //                            · Members +225 · acbRunService +114 · StatusChip +102 · index +46.
  // ACB's bytes: 39,345 + 4,303 = 43,648 B = 42.625 KiB. 680 + 42.625 = 722.625, so 722.63 (rounded UP).
  //
  // 722.63 -> 723.15 on 2026-09-22, MEASURED ON THE GATE'S RUNNER, for 03.3's failed-send rule (EH-E, R3-7): a send
  // that failed on the run's worklist, said as such, with Send again. The completion's 734,244 B (run 35673323129)
  // against ff9df5e's 734,781 B (run 35717409196): +537 B, chunk by chunk -- acbPaging +374 (the worklist) ·
  // AcbDebits +163. ACB's bytes: 43,648 + 537 = 44,185 B = 43.149 KiB. 680 + 43.149 = 723.149, so 723.15.
  //
  // 723.15 -> 723.16 on 2026-09-22, MEASURED ON THE GATE'S RUNNER: SNAPSHOT_INCOMPLETE's label made general (it
  // covers the account type since 00.5) -- 79240a9 builds 734,790 B, +9 B over ff9df5e, all of it acbPaging (run
  // 35722286575). ACB's bytes: 44,185 + 9 = 44,194 B = 43.158 KiB. 680 + 43.158 = 723.158, so 723.16.
  //
  // 723.16 -> 726.99 on 2026-09-30, MEASURED, for SASBO's structures tree on Edit Member (V55; SASBO, through the
  // owner, 2026-09-30: "we need those functionalities back"). Two builds from one install of the same lock in
  // C:\tmp\sasbo-work, the tracked `.env`, each reproducing the gate's runner to the hundredth (runs 36697501618 and
  // 36715656966, whose budget refused this tree at 723.48):
  //   main 8a58340 ON ITS OWN   736,928 B (719.656 KiB)
  //   the tree (e55dc4d)        740,843 B, +3,915 B over it, chunk by chunk: Members +3,582 (the tree's controls,
  //                            its rules in utils/structures.js, the checkbox group's error -- net of the 44 invented
  //                            committee names it deletes) · memberConstants +328 (SASBO's three lists) · acbPaging +5.
  // Checked before raising, as the header asks: nothing from src/test/ is imported by the product, and no test library
  // is in the built bundle. Raised by exactly the tree's bytes, as ACB's were: 723.16 + 3.823 = 726.983, so 726.99
  // (rounded UP). The 3.50 KiB main still had spare is the design arc's remainder, and stays with it.
  //
  // 726.99 -> 781.72 on 2026-09-30, MEASURED, for month end's screens, on the owner's word (MO14, "Raise to measured":
  // both ceilings set to the measured figures in the client PR, citing the run). The pair, each built on a team runner
  // and listed by its chunk-bytes step: client main f6b1b3a 740,843 B (723.48 KiB, run 36773278932) -> month end's
  // integration f194755 800,474 B (781.71 KiB, run 36773289808), +59,631 B, chunk by chunk:
  //   useWorkplaceChoice 0 -> 14,179 B · bankBranches 7,303 -> 0   the Workplace control's search (01.3). Capture and
  //                            the record share it and the bank lookup, and Rollup gives what exactly those two pages
  //                            share one chunk: the control's own bytes are the difference, +6,876.
  //   MemberHistory 0 -> 11,342 B   the record's History tab (03.6) -- lazy, nothing of it before the tab is opened.
  //   useMonths 0 -> 5,965 · MonthEndStatistic 0 -> 5,665 · MonthlyMenu 2,644 -> 6,308 · monthEnd 0 -> 3,416
  //                            the Month end page, a statistic's screen, the months and the pack (04.1, 04.3-04.10).
  //   Workplaces 0 -> 5,498      the workplaces' screen (01.5).
  //   HomeFigures 0 -> 5,471 · FigureStrip 0 -> 2,086 · Home +970   the first screen's figures (02.3, 02.4).
  //   acbRunRules 0 -> 5,273 · acbPaging 6,160 -> 917   moved, not new: the pack polls with A.C.B.'s own run rules, so
  //                            they left A.C.B.'s paging chunk for one of their own (+30 B between the two).
  //   Members +4,180            Change status (03.3), the Workplace field, the strip at six.
  //   index +1,907              the routes and the rail's Month end leaf (04.1).
  //   referenceService 0 -> 1,162 · workplaceService 0 -> 400 · memberConstants +445   the lists' and workplaces' reads.
  //   Admin +121 · Maintenance +117 · CaptureMember +117 · format +93 · AcbDebits +39 · AcbRuns +39
  //   · NavigationCard +23 · Modal +5 · DataState +3 · DateField +1 · bankService +1 · Locations -5   imports and stubs.
  // Checked before raising, as the header asks: nothing from src/test/ is imported by the product, and no test library
  // is in the built bundle. The ceiling is the measured 781.713 rounded UP to the hundredth, and no more: the 3.51 KiB
  // main had spare is spent by it, as the owner chose the measured figure.
  //
  // 781.72 -> 789.34 on 2026-10-01, MEASURED, on the owner's word again (MO25, "Raise to measured"): the stand-in design
  // review's fixes (MO24) and A.1's screens (MO22). Measured on a team runner: clivememela/sasbo-online run 36873464879
  // at bb6508e, javascript 789.33 KiB, 7.61 over 781.72. That run prints two decimals, so each ceiling is its figure and
  // the hundredth that covers the rounding -- at most 10 B of room, and no more. +8,010 B over the branch's own start
  // (781.52 KiB at 017a88b, as a local build measures it), chunk by chunk by vite's sizes, as the commits account for them:
  //   MO24, +1,070 B: HomeFigures +430 · Table +240 (a footer row and the opened-row block) · monthEnd +130
  //                   · useWorkplaceChoice +130 · MemberHistory +90 · Workplaces +40 · Members +10.
  //   A.1, +6,940 B:  Workplaces +3,050 (a workplace's visits) · OrganizerReport +2,630 (Save visit)
  //                   · workplaceVisitService 0 -> 510 · Forms +490 (A60: the four criteria reach the form) · the rest
  //                   the contract's copies and imports.
  // Checked before raising, as the header asks: nothing from src/test/ is imported by the product.
  //
  // 789.34 -> 789.86 on 2026-10-01, MEASURED, under the same word (MO25): 04.9 and Memnet (04.8, MM80). Measured on a
  // team runner: clivememela/sasbo-online run 36880881477 at a94ff46, javascript 789.85 KiB, 0.52 over 789.33 at
  // 7b4daa2 (run 36874974129). +530 B by vite's sizes, both chunks there already:
  //   Members +310 (the record asks and shows the Memnet mark) · useMonths +220 (04.9's statistic, its read and its
  //   sheet's head; the Memnet sheet's head). Nothing from src/test/ is imported by the product.
  //
  // 789.86 -> 790.66 on 2026-10-01, MEASURED, on the owner's word again (MO30, "Raise to measured"): long statistic
  // sheets paged, and 04.11. Measured on a team runner: clivememela/sasbo-online run 36897384908 at 6db4523,
  // javascript 790.65 KiB, 0.80 over 789.85 at 4358ee2. +810 B by vite's sizes, all three chunks there already:
  //   MonthEndStatistic +360 (a sheet longer than a page is paged under the card's pager -- 04.9 holds every branch)
  //   · useMonths +310 (SHEET_PAGE; why a month taken again waits; the sentence of a month taken again)
  //   · MonthlyMenu +140 (Take the month again, disabled with its reason). Nothing from src/test/ is imported.
  //
  // 790.66 -> 790.76 on 2026-10-02, MEASURED, on the owner's word once more (MO33, "Raise to measured"): a past month
  // that was taken says its members are grouped as they stood when it was taken, never "today". Measured on a team
  // runner: clivememela/sasbo-online run 36988020502 at b0f9a4d, javascript 790.75 KiB, 0.10 over 790.65 at 0eb0d56
  // (run 36903903677). +100 B by vite's sizes, in one chunk there already: HomeFigures 5.90 -> 6.00 kB (the sentence,
  // and the choice between it and "today"). Nothing from src/test/ is imported.
  //
  // 790.76 -> 843.77 on 2026-10-07, MEASURED, under OB17 (the October build's rule for every client stream: javascript,
  // css and all assets rise to the figures measured on the stream's staging run, and no further; the entry is OB17a's,
  // above): sign-in -- the session and its token on every API request, /login and /change-password made real, and
  // Maintenance's Users, Positions, Roles and Who did what (contracts/signin.md). Measured on a team runner:
  // clivememela/sasbo-online run 37665352812 at 644e967, javascript 843.76 KiB, 53.88 over 789.88 at cbd11dc (run
  // 37017335321). +55,190 B by vite's sizes, chunk by chunk:
  //   the new screens, every one lazy: Users 12.33 kB · usersRules 6.72 (what the four Maintenance screens share)
  //   · Positions 5.26 · Roles 4.93 · ChangePassword 4.71 · Audit 4.17 · signInRules 3.94 (what /login and
  //   /change-password share) · AccountControls 3.66 (the header's account, the rail's foot, the session notice)
  //   · adminLists 1.42 · authServer 0.56 · authVocabulary 0.50 · authService 0.34 · their icons key-round 0.35, eye 0.25,
  //   log-in 0.23, plus 0.15.
  //   index +5.10 (the entry -- OB17a's paragraph above) · Maintenance +1.75 (the four entries) · Auth -0.65 (/login
  //   made real; its fake Sign Up gone).
  //   Shared chunks Rollup split out once a second screen used them: CheckboxGroup 0 -> 1.23 (Roles' permissions) as
  //   Members -1.14 · LoadFailureFrame 0 -> 0.35 · useDebounce 0 -> 0.20 · options -0.34 · api 0.29 -> 0 (into the
  //   entry) · thirty-one chunks within 0.15 kB each (their import stubs, re-hashed).
  // Nothing from src/test/ is imported by the product.
  //
  // 790.76 -> 791.39 on 2026-10-07, MEASURED, under OB17 (the October build's rule for every client stream: `entry js`
  // must not rise; javascript, css and all assets rise to the figures measured on the stream's staging run, and no
  // further): Home keeps only the modules, as cards, a list or a table, and the month's figures move to Month end (the
  // 5 Oct Show & Tell). Measured on a team runner: clivememela/sasbo-online run 37549523705 at 3824ec2, javascript
  // 791.38 KiB, 1.50 over 789.88 at cbd11dc (run 37017335321) -- after reclaiming first: run 37543710742 had it at
  // 793.11, the css at 81.37 and the entry up 0.02. +1,536 B by this script's count; by vite's sizes, chunk by chunk:
  //   Home +1,250 (the view switch, the table view, the modules' whole names and menu paths, the remembered choice --
  //   less the figures' lazy loader) · NavigationCard +360 (the card, drawn from the row's own parts) · MonthlyMenu
  //   +6,080 and HomeFigures -6,000 (the figures moved, their own month control gone) · Admin +80 · monthEnd -210 (the
  //   months the figures no longer offer) · index -70 (the entry: the rail's short name, Subscriptions off its top
  //   level) · MemberHistory +30 (the reasons' new name) · AcbDebits +20 (two longer labels) · eight chunks within 10 B
  //   each (a column's new name, renamed import stubs).
  //   Nothing from src/test/ is imported. The css is unmoved at 80.63; the entry is 330.40 against main's 330.47.
  //
  // 791.39 -> 791.44 on 2026-10-07, MEASURED (OB17), for the senior review's fixes to the same stream (review-home):
  // run 37557813096 at 7db86d9, javascript 791.43 KiB, 0.05 over 791.38 at 3824ec2. +60 B by vite's sizes, in one chunk
  // there already: MonthlyMenu 12.53 -> 12.59 kB -- a month never taken now names what refuses it (its statistics and its
  // pack) and says its figures are worked out now, where the page had said "a past month is read only from what was
  // taken" above figures that work the month out. Nothing from src/test/ is imported; entry and css unmoved.
  //
  // 790.76 -> 796.18 on 2026-10-07, MEASURED, under the October build's rule (OB17, "raise to measured"): the bugs
  // stream -- A67, A29, A23, RC15 and the user manual's defects. Measured on a team runner: clivememela/sasbo-online
  // run 37670914235 at 32c63ab, javascript 796.17 KiB, +6.29 over 789.88 at main cbd11dc (run 37017335321). +6,460 B by
  // vite's sizes:
  //   bankBranches 0 -> 5.35 and AddressAutocomplete 4.25 -> 0 (+1.10: one shared chunk now holds the address search
  //   and A29's branch form, converted once at each edge) · RequirePermission 0 -> 2.13 (oct/base's notice and the
  //   permission catalogue, first bundled: Subscriptions offers Edit to SUBSCRIPTIONS_MAINTAIN alone, and a channel's
  //   members need MEMBERS_VIEW) · Subscriptions +1.94 (A23's create, the permission's gate, an emptied debit day
  //   refused, what ACTIVE decides) · recruitmentChannelService +0.86 (RC15's five calls) · BankBranch +0.70 (A29: the
  //   record converted, a refusal's fields read in place) · Recruiters +0.31 (RC15 step 2: the server's figures and
  //   pages, Add Member and removal by the channel's routes, the browser's own count gone) · CaptureMember +0.30 (A67:
  //   the bank's day is the default the save sends) · useDebounce 0 -> 0.20 and useWorkplaceChoice -1.04 (a module
  //   shared by more screens leaves for its own chunk) · the entry +0.08, NO CODE (OB17a): the preload map naming the
  //   two more lazy chunks · the rest +/-0.10 of shared-chunk names. Nothing from src/test/ is imported by the product.
  //
  // 790.76 -> 798.62 on 2026-10-07, MEASURED, under OB17 (javascript, css and all assets rise to the stream's staging
  // run, and no further): the member register a page at a time (A10) and the record's save saying why it was refused
  // (V68). Measured on a team runner: clivememela/sasbo-online run 37672789318 at 1d6e43b, javascript 798.61 KiB, 8.73
  // over 789.88 at cbd11dc (run 37017335321). +8,930 B by vite's sizes, chunk by chunk:
  //   Members 54.96 -> 62.50 kB (+7.54: the view in the address bar, the server's page and count, sorting, Per page,
  //   the record read when its row is opened, the refused save's reason and MEMBER_CHANGED_SINCE -- less the browser's
  //   own filtering and counting, gone) · Table +1.01 (headings that sort, with aria-sort) · memberService +0.27 (the
  //   page and the overview) · useDebounce 0 -> 0.20 and Workplaces -0.10 (shared now, so split out) · monthEnd +0.03
  //   (MEMBER_CHANGED_SINCE) · format -0.06 (the dates the register counted, no longer read) · index +0.02 (above)
  //   · Admin +0.01 · Locations +0.01.
  // Nothing from src/test/ is imported by the product.
  //
  // 790.76 -> 813.88 on 2026-10-07, MEASURED, under the October build's rule (OB17, "raise to measured", the parent's
  // word for every client stream): the organisers' batch print, the members at a branch, the visit drafts. Measured on
  // a team runner: clivememela/sasbo-online run 37556370376 at 1595f03, javascript 813.87 KiB, against 789.88 at main
  // cbd11dc (run 37017335321). +24,550 B by vite's sizes:
  //   BatchPrint 0 -> 12.17 (the new route -- its four modules only, BatchPrint, WorkplaceListing, raNumbers and
  //   limiter: the build's sourcemaps show no shared module bundled in, and no module of the app in two chunks)
  //   · OrganizerReport +8.22 (Save draft, Submit visit, My visits, a submitted visit read-only) · LocationListing +1.25
  //   (the blank-card fix: a /api/branches row read in its own shape; "Complement") · workplaceVisitService +0.92 (the
  //   drafts' five calls) · permissions 0 -> 0.84 (oct/base's auth context, first imported by a screen here) · the
  //   entry +0.59, SHELL ONLY (OB17a): the sidebar's Batch print item, the route's lazy line, and the Printer icon,
  //   which leaves its own shared chunk (-0.32) for the entry because the sidebar now draws it · locationService +0.37 ·
  //   Locations +0.32 (the line to the batch print) · ReportOptions +0.16 · monthEnd +0.12 (five refusal codes) · the
  //   rest +/-0.05 of shared-chunk names. Nothing from src/test/ is imported by the product.
  //
  // 813.88 -> 816.91 on 2026-10-07, MEASURED (OB17): the reports-visits review's fixes. Measured on a team runner:
  // clivememela/sasbo-online run 37667621360 at 8b5ec54, javascript 816.90 KiB, +3.03 over 813.87 at 1595f03 (run
  // 37613005859 at 0b6b8e0 builds the same). +3,130 B by vite's sizes:
  //   OrganizerReport +1.43 (Discard draft, asked first; My visits' pager) · permissions +0.71 (RequirePermission, first
  //   bundled: Batch print tells a signed-in user without REPORTS_PRINT what they need) · BatchPrint +0.55 (the batch's
  //   members in one read, a stale answer dropped; the limiter's module gone) · printer 0 -> 0.32 and the entry -0.23
  //   (the rail draws an icon it already had, so Printer leaves the first load for a shared chunk of the three lazy
  //   screens that draw it -- OB17a: the entry only shrinks) · workplaceVisitService +0.22 (discardDraft) · the rest
  //   +/-0.05 of shared-chunk names. Nothing from src/test/ is imported by the product.
  // INTEGRATION, MEASURED (OB17): the batch's javascript is 882.06 KiB on clivememela/sasbo-online run 37676718704
  // at be7b1ab, every paragraph above merged; the ceiling is that figure. The entry chunk measured 336.29, +5.82 over
  // main's 330.47: the streams' own figures sum to +5.36 (sign-in's shell +4.98, OB17a); the other +0.46 is, by
  // elimination, the merged preload map naming every stream's lazy chunks.
  // INTEGRATION, MEASURED (OB17): 882.07 KiB on clivememela/sasbo-online run 37782197874 at dff342f, with A29's keyed
  // footer buttons (bugs 030e408, +10 B in BankBranch); the ceiling is that figure.
  js: 882.08 * KB,
  // 2026-09-10: 60.63 → 72.02 KiB when the client became card.html whole -- the theme
  // switch's pour, the register's container ladder and phone rows, the card's landing and
  // sweep, the skeleton, and the utilities every rebuilt screen now shares. Checked before
  // raising: nothing from src/test/ is in the product; the growth is the design. 80 keeps
  // the same headroom over the measured figure that 70 kept over 60.
  //
  // 80 -> 80.64 on 2026-10-01, MEASURED (MO25): 80.63 KiB on the same run, +720 B over 79.92 -- MO24's card rows' `end`
  // rule, the first screen's phone block and five utilities the build had not shipped (+590 B), and A.1's opened row's
  // rule (+130 B). The hundredth over the printed figure covers its rounding, as the javascript's does.
  //
  // 80.64 -> 81.14 on 2026-10-07, MEASURED (OB17): 81.13 KiB on run 37665352812 at 644e967, +0.50 over 80.63 at cbd11dc
  // (vite's index.css 82.56 -> 83.08 kB, +520 B) -- the utilities sign-in's screens use that no screen had before (the
  // account chip, the password field, the users' dialogs and lists).
  //
  // 80.64 -> 80.79 on 2026-10-07, MEASURED (OB17): 80.78 KiB on run 37672789318 at 1d6e43b, +0.15 over 80.63 at cbd11dc
  // (vite's index.css 82.56 -> 82.72 kB, +160 B) -- the sortable headings' and Per page's utilities.
  //
  // 80.64 -> 81.00 on 2026-10-07, MEASURED (OB17): 80.99 KiB on run 37556370376, +380 B over 80.63 at main cbd11dc --
  // the batch print's page rules (a bank a sheet, its A4 named page, a heading kept with what follows it) and the
  // utilities its screens are the first to use.
  //
  // 81.00 -> 81.19 on 2026-10-07, MEASURED (OB17): 81.18 KiB on run 37667621360, +190 B over 80.99 -- the utilities
  // the review's fixes are the first to use (My visits' pager set flush to its panel, Discard draft's confirmation).
  // INTEGRATION, MEASURED (OB17): the batch's css is 81.84 KiB on clivememela/sasbo-online run 37676718704
  // at be7b1ab, every paragraph above merged; the ceiling is that figure.
  css: 81.85 * KB,
  // 900 -> 925.12 on 2026-09-30, MEASURED, on the owner's word (MO14), the first time `all assets` was over: the same
  // pair as the javascript's, 866.48 KiB (run 36773278932) -> 925.11 KiB (run 36773289808). The javascript's +59,631 B
  // and the stylesheet's +409 B (296 of them the History tab's: its column widths, gutter bleed, alignment and one line
  // height; css stays under its own 80) are 60,040 B, 58.63 KiB -- the whole difference, to the hundredth. Rounded UP.
  //
  // 925.12 -> 933.45 on 2026-10-01, MEASURED (MO25): 933.44 KiB on the same run -- the javascript's and the stylesheet's
  // growth above, and nothing else. The hundredth over the printed figure covers its rounding.
  //
  // 933.45 -> 933.97 on 2026-10-01, MEASURED (MO25): 933.96 KiB on run 36880881477 -- the javascript's +0.52 above, the
  // stylesheet unmoved at 80.63.
  //
  // 933.97 -> 934.77 on 2026-10-01, MEASURED (MO30): 934.76 KiB on run 36897384908 -- the javascript's +0.80 above,
  // the stylesheet unmoved.
  //
  // 934.77 -> 934.87 on 2026-10-02, MEASURED (MO33): 934.86 KiB on run 36988020502 -- the javascript's +0.10 above,
  // the stylesheet unmoved at 80.63.
  //
  // 934.87 -> 988.39 on 2026-10-07, MEASURED (OB17): 988.38 KiB on run 37665352812 at 644e967, 54.39 over 933.99 at
  // cbd11dc -- the javascript's +53.88 and the stylesheet's +0.50 above, index.html unmoved at 3.19 kB.
  //
  // 934.87 -> 935.58 on 2026-10-07, MEASURED (OB17): 935.57 KiB on run 37549523705 -- the javascript's +1.50 above and
  // index.html's +80 B (its pre-paint script sets the color-scheme meta: light unless the browser chose dark), the
  // stylesheet unmoved at 80.63.
  //
  // 935.58 -> 935.64 on 2026-10-07, MEASURED (OB17): 935.63 KiB on run 37557813096 -- the javascript's +60 B above
  // (review-home's never-taken sentence), the stylesheet unmoved at 80.63.
  //
  // 934.87 -> 940.29 on 2026-10-07, MEASURED (OB17): 940.28 KiB on run 37670914235 -- the javascript's +6.29 above, the
  // stylesheet unmoved at 80.63, against 933.99 at main cbd11dc.
  //
  // 934.87 -> 942.88 on 2026-10-07, MEASURED (OB17): 942.87 KiB on run 37672789318 at 1d6e43b, 8.88 over 933.99 at
  // cbd11dc -- the javascript's +8.73 and the stylesheet's +0.15 above, index.html unmoved.
  //
  // 934.87 -> 958.35 on 2026-10-07, MEASURED (OB17): 958.34 KiB on run 37556370376 -- the javascript's +23.97 KiB and the
  // stylesheet's +0.37 above, and nothing else, against 933.99 at main cbd11dc.
  //
  // 958.35 -> 961.57 on 2026-10-07, MEASURED (OB17): 961.56 KiB on run 37667621360 -- the javascript's +3.03 and the
  // stylesheet's +0.19 above, and nothing else.
  // INTEGRATION, MEASURED (OB17): the batch's all assets is 1027.46 KiB on clivememela/sasbo-online run 37676718704
  // at be7b1ab, every paragraph above merged; the ceiling is that figure.
  //
  // 940.29 -> 940.30 on 2026-10-08, MEASURED (OB17): 940.29 KiB printed on run 37779077999 at e6499ba, over by bytes; the
  // hundredth over the printed figure covers its rounding. One chunk moved against run 37672009580 at 56c4a67:
  // BankBranch 13.91 -> 13.92 kB, A29's footer buttons keyed apart so that pressing Edit no longer saves the form it
  // opens. The javascript row stays within its 796.18; the stylesheet unmoved at 80.63. Nothing from src/test/ is
  // imported by the product.
  // INTEGRATION: the base plus every stream's rise above, until the integration run measures it (OB17).
  total: 1027.48 * KB,
  // 2026-09-14: DOCUMENTS ARE THEIR OWN ROW, not part of `total`. The membership application
  // PDF became a real Vite import (it had been a dev-only `/src/...` path that a production
  // build served as an empty frame), and at 487.6 KiB it alone took `all assets` from ~764 to
  // 1251 KiB. Checked before splitting: nothing from src/test/ is in the product, and the
  // growth is one file a visitor downloads only by opening that form -- it is not page weight,
  // so folding it into `total` would have forced a ceiling too loose to notice real bloat.
  // It still has a ceiling of its own: a second form, or a far larger scan, is still noticed.
  documents: 600 * KB,
}

// Floors come from the registry; CEILINGS stay local -- a ceiling is not a
// non-vacuity canary, and `src/test/floors.js` owns the why and the dates.

const walk = (dir) =>
  readdirSync(dir, { withFileTypes: true }).flatMap((e) =>
    e.isDirectory() ? walk(join(dir, e.name)) : [join(dir, e.name)],
  )

let files
try {
  files = walk('dist')
} catch {
  console.error(
    '\n  dist/ does not exist. This checks the SHIPPED bundle, so it needs a build:\n' +
      '    npm run build && npm run budget\n',
  )
  process.exit(1)
}

const sum = (pred) =>
  files.filter(pred).reduce((n, f) => n + statSync(f).size, 0)

/**
 * The entry chunk, read from index.html rather than guessed from a filename.
 *
 * Vite hashes every chunk and several are called `index-*.js`, so picking "the one
 * that looks like the entry" is a guess that goes wrong on the day a lazy page is
 * also named index. The document says which script it loads; that is the answer.
 */
const entryJs = (() => {
  let html
  try {
    html = readFileSync('dist/index.html', 'utf8')
  } catch {
    return 0
  }
  const srcs = [...html.matchAll(/<script[^>]+src="([^"]+\.js)"/g)].map((m) => m[1])
  return srcs.reduce((n, src) => {
    try {
      return n + statSync(join('dist', src.replace(/^\//, ''))).size
    } catch {
      return n
    }
  }, 0)
})()

const js = sum((f) => f.endsWith('.js'))
const css = sum((f) => f.endsWith('.css'))
// Downloadable documents are measured on their own row -- see `CEILINGS.documents`.
const isDocument = (f) => /\.pdf$/i.test(f)
const total = sum((f) => !isDocument(f))
const documents = sum(isDocument)

const fmt = (n) => `${(n / KB).toFixed(2)} KiB`

const count = (n) => `${n} file(s)`

// The file count is a row like any other, not a special case ahead of the
// table. It has no ceiling -- a build emitting many files is not a defect --
// and `Infinity` says that in the same shape rather than in a second code path
// that sets the same flag from somewhere else.
const rows = [
  // 🔑 FIRST, because it is the only row a user experiences directly. Every other
  // byte here is paid later, or never.
  ['entry js', entryJs, FLOORS.BUNDLE_ENTRY_JS_BYTES, CEILINGS.entryJs, fmt],
  ['javascript', js, FLOORS.BUNDLE_JS_BYTES, CEILINGS.js, fmt],
  ['css', css, FLOORS.BUNDLE_CSS_BYTES, CEILINGS.css, fmt],
  ['all assets', total, FLOORS.BUNDLE_TOTAL_BYTES, CEILINGS.total, fmt],
  // Floor 0: shipping no documents at all is a legitimate build, unlike shipping no code.
  ['documents', documents, 0, CEILINGS.documents, fmt],
  // 🔑 The count catches what the byte floors cannot: one large asset copied
  // into an otherwise empty dist/ clears every byte floor while shipping no
  // application at all.
  ['emitted files', files.length, FLOORS.BUNDLE_FILES, Infinity, count],
]

console.log('\n  bundle budget\n')
let over = false
let under = false

for (const [name, actual, floor, ceiling, format] of rows) {
  const isOver = actual > ceiling
  const isUnder = actual < floor
  if (isOver) over = true
  if (isUnder) under = true
  const mark = isOver ? 'OVER ' : isUnder ? 'UNDER' : 'OK   '
  const limit = ceiling === Infinity ? 'none'.padStart(10) : format(ceiling).padStart(10)
  console.log(
    `  ${mark} ${name.padEnd(13)} ${format(actual).padStart(10)}  ` +
      `floor ${format(floor).padStart(10)}  ceiling ${limit}`,
  )
}
console.log('')

if (over) {
  console.error(
    '  The shipped bundle is over its ceiling. Before raising the number, check\n' +
      '  whether test tooling has leaked into the product: an import from\n' +
      '  src/test/ into a component is the failure this guard was built for.\n',
  )
}

if (under) {
  console.error(
    '  The shipped bundle is under its FLOOR, which almost never means the product\n' +
      '  got smaller. It means the build did not emit what it should have -- a failed\n' +
      '  or partial build, a changed output directory, or a stale dist/ from a run\n' +
      '  that produced almost nothing.\n\n' +
      '  This is here because every other assertion in this file is `actual <= ceiling`,\n' +
      '  and a dist/ containing nothing but index.html satisfies all of them. Rebuild\n' +
      '  and look at what was emitted. Do not lower the floor.\n',
  )
}

if (over || under) process.exit(1)
