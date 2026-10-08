import { test, expect } from '@playwright/test'
import { World } from '../../support/world.js'
import { mintTestName } from '../../support/guards.js'

/**
 * Cleanup is the part of a suite that is only exercised when something else has
 * already gone wrong, so it is the part most likely to be quietly broken.
 */
test.describe('per-scenario cleanup', () => {
  test('undoes registrations in reverse order', async () => {
    // A branch created inside a bank must be removed before the bank. Deleting
    // the parent first either trips a foreign key or cascades further than the
    // scenario intended.
    const order = []
    const world = new World()
    world.registerCleanup(mintTestName('bank'), 'bank', () => order.push('bank'))
    world.registerCleanup(mintTestName('branch'), 'branch', () => order.push('branch'))

    await world.cleanUp()

    expect(order).toEqual(['branch', 'bank'])
  })

  test('refuses to register something it could not delete', () => {
    const world = new World()
    expect(() => world.registerCleanup('Nedbank', 'bank', () => {})).toThrow(/REFUSING to delete/)
    expect(world.pending).toBe(0)
  })

  test('runs every cleanup even when one throws, then reports the leaks', async () => {
    // Stopping at the first failure leaks everything after it, and those leaks
    // are what poison the next run.
    const ran = []
    const world = new World()
    world.registerCleanup(mintTestName('a'), 'a', () => ran.push('a'))
    world.registerCleanup(mintTestName('b'), 'b', () => {
      throw new Error('boom')
    })
    world.registerCleanup(mintTestName('c'), 'c', () => ran.push('c'))

    await expect(world.cleanUp()).rejects.toThrow(/LEAKED/)
    expect(ran).toEqual(['c', 'a'])
  })

  test('empties itself so a second cleanup is a no-op', async () => {
    const world = new World()
    world.registerCleanup(mintTestName('a'), 'a', () => {})

    await world.cleanUp()
    expect(world.pending).toBe(0)
    await expect(world.cleanUp()).resolves.toBeUndefined()
  })
})
