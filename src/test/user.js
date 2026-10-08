import userEvent from '@testing-library/user-event'

/**
 * The one way this suite sets up user interaction.
 *
 * WHY `delay: null` -- AND WHY IT IS AN ANTI-FLAKE FIX, NOT A SPEED-UP
 * `userEvent.setup()` defaults to inserting a real delay between keystrokes,
 * advancing the clock between each one. Typing a 13-digit identity number and
 * two names into `CaptureMember` -- an 858-line component that re-renders on
 * every keystroke -- therefore costs real wall clock, and how much depends on
 * how busy the machine is.
 *
 * Observed 2026-09-05: `CaptureMember.roundtrip.test.jsx` passed in isolation
 * and TIMED OUT at 5000ms when a second test file ran beside it. That is the
 * defining shape of a flake -- a test whose result depends on load rather than
 * on the code it covers, which passes on a developer machine and fails on a
 * loaded CI runner.
 *
 * The fix is at the design, not at the threshold. Raising `testTimeout` would
 * have made the symptom go away on this machine while leaving the test's
 * result a function of scheduling; `delay: null` removes the wall-clock
 * dependence itself. Nothing here is retried, and no timeout was negotiated
 * upwards.
 *
 * It is a shared helper rather than an option each file passes, because the
 * next component test will otherwise be written with the default and
 * rediscover this the slow way.
 */
// Options pass straight through -- e.g. `{ advanceTimers: vi.advanceTimersByTime }` under fake timers.
export const setupUser = (options = {}) => userEvent.setup({ delay: null, ...options })
