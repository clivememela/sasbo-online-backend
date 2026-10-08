import { test, expect } from '@playwright/test'
import { readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { assertNonVacuousScan, FLOORS } from '../../support/assertNonVacuousScan.js'

const here = dirname(fileURLToPath(import.meta.url))

/**
 * The canary is itself a guard, so it gets the treatment every guard here gets:
 * tested in BOTH directions. A canary that cannot fail is decoration.
 */
test.describe('the non-vacuity canary', () => {
  test('passes when the collection clears the floor', () => {
    expect(() => assertNonVacuousScan([1, 2, 3], 2, 'things')).not.toThrow()
  })

  test('passes at exactly the floor', () => {
    expect(() => assertNonVacuousScan([1, 2], 2, 'things')).not.toThrow()
  })

  test('fails below the floor, naming what was being scanned', () => {
    expect(() => assertNonVacuousScan([1], 2, 'feature files')).toThrow(/feature files/)
  })

  test('fails on an empty collection -- the case it exists for', () => {
    // A broken glob matches nothing. That is the exact failure this function
    // was written to convert from a green run into a red one.
    expect(() => assertNonVacuousScan([], 1, 'feature files')).toThrow(/NON-VACUITY CANARY/)
  })

  test('says fix the walker, not lower the floor', () => {
    // The message is load-bearing. A floor that reads as a coverage target is a
    // floor somebody lowers the first time it is inconvenient.
    expect(() => assertNonVacuousScan([], 1, 'anything')).toThrow(/do not lower the floor/)
  })

  test('counts a Set as well as an Array', () => {
    expect(() => assertNonVacuousScan(new Set(['a', 'b']), 2, 'things')).not.toThrow()
  })
})

test.describe('the support-module floor', () => {
  test('the harness has the modules the suite is built from', () => {
    // Completeness by discovery, carrying no number...
    const modules = readdirSync(resolve(here, '../../support')).filter((f) => f.endsWith('.js'))

    // ...and the canary, carrying one deliberately below the actual.
    assertNonVacuousScan(modules, FLOORS.SUPPORT_MODULES, 'harness support modules')
  })
})
