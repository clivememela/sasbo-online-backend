// @vitest-environment jsdom -- the Forms screen's four criteria, carried into the form it opens (register A60)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import { Forms } from './Forms'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { setupUser } from '../../test/user'

/**
 * ✅ REGISTER A60, CLOSED WITH MONTH END A.1 (MO22): the Forms screen asked for a Bank, a Branch, an Area and an R/A No
 * and passed none of them to the form it opened -- the Location Visit form opened with all four blank, and the General
 * Form printed "ABSA BANK" and "94" whatever was typed.
 *
 * 🔑 THE ROW'S OWN EXIT CONDITION: "Not by adding `filters={filters}` alone: that makes those two effects live again and
 * wrong for a second reason -- they would overwrite whatever the user has typed each time a criterion changes." What
 * the code does, and what this holds: a form is SEEDED as it opens -- by choosing its type, or by Search Form -- with
 * the criteria as they stand. A criterion changed afterwards touches nothing in it. Search Form opens it again for the
 * criteria as they stand then -- another workplace, remounted, and what was typed for the last one goes with it; with
 * the criteria unchanged it is the same form, and keeps everything.
 */

const CRITERIA = { Bank: 'INVENTED BANK', Branch: 'INVENTED HEAD OFFICE', Area: 'A', 'Random access number': '900002' }

/** The criteria's own form -- the one Search Form submits. */
const criteria = () => screen.getByRole('button', { name: /Search Form/ }).closest('form')
/** A field of the form the screen opened, by its label -- never the criterion of the same name. */
const inForm = (label) => screen.getAllByLabelText(label).find((field) => !criteria().contains(field))

async function typeCriteria(user, values = CRITERIA) {
  for (const [label, value] of Object.entries(values)) {
    const box = within(criteria()).getByLabelText(label)
    await user.clear(box)
    await user.type(box, value)
  }
}

async function choose(user, formType) {
  await user.click(within(criteria()).getByRole('combobox', { name: /^Form Type/ }))
  await user.click(await screen.findByRole('option', { name: formType }))
}

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  // The General Form and the Location Visit form read nothing; Location Listing would, and is not opened here.
  server.use(http.get('*/api/*', () => HttpResponse.json([])))
})

describe('the Forms screen\'s criteria reach the form it opens (A60)', () => {
  it('the Location Visit form opens with the Bank, the Branch, the R/A number and the Area typed above it', async () => {
    const user = setupUser()
    renderWithProviders(<Forms />)
    await typeCriteria(user)
    await choose(user, 'Branch Form (online)')

    expect(inForm('Bank')).toHaveValue('INVENTED BANK')
    expect(inForm('Branch / Department')).toHaveValue('INVENTED HEAD OFFICE')
    expect(inForm('R / A Number')).toHaveValue('900002')
    expect(inForm('Area')).toHaveValue('A')
  })

  it('the General Form opens with the Bank and the R/A No -- and, none typed, empty: never "ABSA BANK" and "94"', async () => {
    const user = setupUser()
    renderWithProviders(<Forms />)
    await typeCriteria(user)
    await choose(user, 'General Form')

    expect(inForm('Bank')).toHaveValue('INVENTED BANK')
    expect(inForm('R/A No')).toHaveValue('900002')

    // The must-pass: with no criteria, the boxes are empty -- the hardcoded values wore a filter's clothes.
    for (const label of Object.keys(CRITERIA)) await user.clear(within(criteria()).getByLabelText(label))
    await user.click(screen.getByRole('button', { name: /Search Form/ }))
    expect(inForm('Bank')).toHaveValue('')
    expect(inForm('R/A No')).toHaveValue('')
    expect(screen.queryByDisplayValue('ABSA BANK')).not.toBeInTheDocument()
    expect(screen.queryByDisplayValue('94')).not.toBeInTheDocument()
  })

  it('🔴 a value typed into the form is not overwritten when a criterion changes afterwards', async () => {
    const user = setupUser()
    renderWithProviders(<Forms />)
    await typeCriteria(user)
    await choose(user, 'Branch Form (online)')

    await user.clear(inForm('Bank'))
    await user.type(inForm('Bank'), 'TYPED IN THE FORM')
    await user.type(inForm('Organiser Name'), 'INVENTED ORGANISER')
    // A criterion changed -- and only changed: nothing asked the form to open again.
    await user.clear(within(criteria()).getByLabelText('Bank'))
    await user.type(within(criteria()).getByLabelText('Bank'), 'ANOTHER BANK')
    await user.clear(within(criteria()).getByLabelText('Random access number'))
    await user.type(within(criteria()).getByLabelText('Random access number'), '900003')

    expect(inForm('Bank')).toHaveValue('TYPED IN THE FORM')
    expect(inForm('R / A Number')).toHaveValue('900002')
    expect(inForm('Organiser Name')).toHaveValue('INVENTED ORGANISER')
  })

  it('Search Form opens the form again for the criteria as they stand -- another workplace, and what was typed goes', async () => {
    const user = setupUser()
    renderWithProviders(<Forms />)
    await typeCriteria(user)
    await choose(user, 'Branch Form (online)')
    await user.type(inForm('Organiser Name'), 'INVENTED ORGANISER')

    await user.clear(within(criteria()).getByLabelText('Random access number'))
    await user.type(within(criteria()).getByLabelText('Random access number'), '900003')
    await user.click(screen.getByRole('button', { name: /Search Form/ }))

    expect(inForm('R / A Number')).toHaveValue('900003')
    expect(inForm('Bank')).toHaveValue('INVENTED BANK')
    expect(inForm('Organiser Name'), 'the form was opened afresh, for the other workplace').toHaveValue('')
  })

  it('the must-pass: Search Form with the criteria as they were is the same form, and keeps everything typed', async () => {
    const user = setupUser()
    renderWithProviders(<Forms />)
    await typeCriteria(user)
    await choose(user, 'Branch Form (online)')
    await user.type(inForm('Organiser Name'), 'INVENTED ORGANISER')

    await user.click(screen.getByRole('button', { name: /Search Form/ }))

    expect(inForm('Organiser Name')).toHaveValue('INVENTED ORGANISER')
    expect(inForm('R / A Number')).toHaveValue('900002')
  })
})
