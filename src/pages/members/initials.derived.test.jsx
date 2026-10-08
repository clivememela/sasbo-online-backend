// @vitest-environment jsdom -- types into Capture Member and Edit Member
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'

import CaptureMember from './CaptureMember'
import MembersPage from './Members'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { registerHandlers } from '../../test/msw/memberRegister'
import { setupUser } from '../../test/user'

/**
 * R4-F1 — INITIALS ARE DERIVED UNTIL SOMEBODY TYPES THEM, AND NOT AFTER.
 *
 * 🔴 Both screens recomputed Initials from First Name(s) on EVERY keystroke. An administrator who
 * corrected "TS" to "T.S." (or entered a member's preferred initials) and then fixed a typo in the
 * first names had their initials silently replaced -- the field they had deliberately set, changed
 * by a field they did not touch, with nothing on screen to say so.
 *
 * Each screen gets the pair: the derivation still runs for someone who never touched Initials (the
 * must-pass half -- a fix that simply stopped deriving would pass the other test), and it stops for
 * someone who did.
 */

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(...pageFixtures)
})

const retype = async (user, field, value) => {
  await user.clear(field)
  await user.type(field, value)
}

describe('Capture Member', () => {
  const initials = () => screen.getByLabelText(/^Initials/)
  const firstNames = () => screen.getByLabelText(/^First Name\(s\)/)

  it('derives Initials from First Name(s) while nobody has typed them', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await retype(user, await screen.findByLabelText(/^First Name\(s\)/), 'Thabo Sipho')
    expect(initials()).toHaveValue('TS')
  })

  it('🔴 keeps Initials the administrator typed when First Name(s) changes afterwards', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await retype(user, await screen.findByLabelText(/^First Name\(s\)/), 'Thabo Sipho')
    await retype(user, initials(), 'TSM')
    await user.type(firstNames(), 'n')

    expect(initials()).toHaveValue('TSM')
  })
})

describe('Edit Member', () => {
  const ALPHA = {
    id: 7, membershipNumber: 'M0007', firstNames: 'Thabo Sipho', surname: 'Alpha', initials: 'TS',
    identityType: 'I.D. Number', identityNumber: '9001010000080', status: 'Active', paymentType: 'Payroll Deduction',
  }
  const BRAVO = { ...ALPHA, id: 8, membershipNumber: 'M0008', surname: 'Bravo', identityNumber: '8506155000084' }
  const panel = () => within(screen.getByTestId('member-panel'))

  async function edit(user, name = /Alpha/) {
    await user.click(await screen.findByRole('button', { name: new RegExp(`open the record for.*${name.source}`, 'i') }))
    await user.click(await screen.findByRole('button', { name: /Edit Member/i }))
  }

  beforeEach(() => {
    server.use(...registerHandlers([ALPHA, BRAVO]))
  })

  it('derives Initials from First Name(s) while nobody has typed them', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await edit(user)
    await retype(user, await panel().findByLabelText(/^First Name\(s\)/), 'Lerato Palesa')
    expect(panel().getByLabelText(/^Initials/)).toHaveValue('LP')
  })

  it('🔴 keeps Initials the administrator typed when First Name(s) changes afterwards', async () => {
    const user = setupUser()
    renderWithProviders(<MembersPage />)
    await edit(user)
    await retype(user, await panel().findByLabelText(/^Initials/), 'T.S.')
    await user.type(panel().getByLabelText(/^First Name\(s\)/), 'n')

    expect(panel().getByLabelText(/^Initials/)).toHaveValue('T.S.')
  })
})
