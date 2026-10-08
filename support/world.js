import { assertDeletable } from './guards.js'

/**
 * What one scenario created, so that same scenario can clean it up.
 *
 * WHY PER-SCENARIO AND NOT A SHARED TEARDOWN
 * Scenarios run in parallel workers. A shared "delete everything the suite
 * made" teardown cannot run safely while other scenarios are still using their
 * own rows, and a scenario that depends on another's leftovers passes when run
 * alone and fails under load -- which reads as flake and gets investigated as a
 * code bug. Each scenario seeds and cleans its own state; nothing is inherited
 * from a sibling.
 *
 * WHY CLEANUP IS REGISTERED AT CREATION TIME
 * The alternative is a teardown that re-queries the database for anything
 * matching the test prefix, which is a delete driven by a SEARCH rather than by
 * a record of what this scenario actually made. That is the shape of cleanup
 * that eventually removes something it did not create.
 *
 * REVERSE ORDER, DELIBERATELY
 * Registrations unwind last-in-first-out, so a branch created inside a bank is
 * removed before the bank it belongs to. Deleting the parent first either fails
 * on a foreign key or cascades further than intended.
 */
export class World {
  #cleanups = []

  /**
   * Record how to undo something this scenario just created.
   *
   * The name is checked against BOTH destructive guards HERE, at registration
   * time, rather than at teardown. Failing early means the scenario reddens at
   * the line that created the bad record -- where the cause is -- instead of
   * during teardown, where the stack trace points at the cleanup routine and
   * says nothing about which step produced it.
   */
  registerCleanup(name, what, undo) {
    assertDeletable(name, what)
    this.#cleanups.push({ name, what, undo })
    return name
  }

  /**
   * Record how to put back something this scenario CHANGED rather than created.
   *
   * WHY THIS BYPASSES THE DESTRUCTIVE GUARDS AND WHY THAT IS NOT A HOLE
   * `registerCleanup` guards deletions, and it guards them by demanding a name
   * this suite can prove it minted. A restore is the opposite operation: it
   * writes a value back, or starts a container that was stopped, and there is
   * no record being removed for a guard to protect. Forcing those through
   * `assertDeletable` would mean inventing a fake minted name for a container,
   * which is how a guard stops meaning anything.
   *
   * The distinction is enforced by the two methods existing rather than by
   * anyone remembering it: nothing that DELETES may register here, and nothing
   * registered here deletes.
   */
  registerRestore(what, undo) {
    this.#cleanups.push({ name: '(restore)', what, undo })
    return this
  }

  /** How many undo actions are pending. Used by the harness self-tests. */
  get pending() {
    return this.#cleanups.length
  }

  /**
   * Run every registered undo, newest first.
   *
   * EVERY cleanup runs even if one throws. A teardown that stops at its first
   * failure leaks every record after it, and those leaks are what poison the
   * next run. Failures are collected and reported together, so one broken undo
   * is visible without hiding the rest.
   */
  async cleanUp() {
    const failures = []

    for (const { name, what, undo } of this.#cleanups.reverse()) {
      try {
        await undo()
      } catch (error) {
        failures.push(`  ${what} ${name}: ${error.message}`)
      }
    }
    this.#cleanups = []

    if (failures.length > 0) {
      throw new Error(
        `Scenario cleanup failed for ${failures.length} record(s). These are ` +
          `LEAKED and will be present for the next run:\n${failures.join('\n')}`,
      )
    }
  }
}
