// @vitest-environment jsdom -- renders Capture Member's Employment & Bank tab
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'

import CaptureMember from './CaptureMember'
import { renderWithProviders } from '../../test/renderWithProviders'
import { server } from '../../test/msw/server'
import { pageFixtures } from '../../test/msw/pageFixtures'
import { setupUser } from '../../test/user'
import { BRANCH_OPTIONS } from '../../config/memberConstants'

/**
 * EMPLOYMENT & BANK -- the order the stakeholder asked for, and a branch list that belongs to the bank.
 *
 *   E1 (spec): Bank Where Employed sits ABOVE Employee Number.
 *   E3 + the owner, 2026-09-15 ("take what's on Bank Branch of dev.sasbo.online"): the branch list is the chosen
 *      bank's own branches TOGETHER WITH the fourteen names dev offers -- a union, because the seeded listing
 *      holds one branch per bank and the fourteen alone name nothing a bank owns.
 *   A branch only means something once the bank is known, so the field waits for one, and a branch that the
 *      NEW bank does not offer is cleared when the bank changes.
 *   R3-F3 (design arc P9): the job-grade effect cleared the chosen grade on every run, including a reference-list
 *      "Try again" -- while that notice promises "anything you have already typed is kept".
 */

const ABSA = { id: 1, bankName: 'Absa Bank Limited', branchName: 'Sandton City', branchCode: '632005', universalBranchCode: '632005' }
const CAPITEC = { id: 2, bankName: 'Capitec Bank Limited', branchName: 'Menlyn Maine', branchCode: '470010', universalBranchCode: '470010' }
const GRADES = [{ value: 'C1', label: 'C1' }, { value: 'BA1', label: 'BA1' }]

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  server.use(
    http.get('*/api/banks', () => HttpResponse.json([ABSA, CAPITEC])),
    http.get('*/api/banks/branches', () => HttpResponse.json([ABSA, CAPITEC])),
    http.get('*/api/job-grades', () => HttpResponse.json(GRADES)),
    ...pageFixtures,
  )
})

const openEmployment = async (user) => user.click(await screen.findByRole('button', { name: /^Employment & Bank/ }))

async function chooseFrom(user, labelText, typed, optionName) {
  // Through the control's own Clear button when it already holds a choice: typing into the closed box APPENDS to
  // the selection it displays (the same accommodation CaptureMember.jobGrade.test.jsx makes).
  const clear = screen.queryByRole('button', { name: new RegExp(`^Clear ${labelText}`) })
  if (clear) await user.click(clear)
  const label = await screen.findByText(new RegExp(`^${labelText}`))
  const input = label.closest('div').querySelector('input')
  await user.type(input, typed)
  await user.click(await screen.findByRole('option', { name: optionName }))
}

const branchCombobox = () => {
  const label = screen.getByText(/^Bank Branch/)
  return label.closest('div').querySelector('input')
}

async function branchOptionNames(user) {
  await user.click(branchCombobox())
  const listbox = await screen.findByRole('listbox')
  const names = within(listbox).getAllByRole('option').map((o) => o.textContent.trim())
  await user.keyboard('{Escape}')
  return names
}

describe('Capture Member · Employment & Bank', () => {
  it('E1: Bank Where Employed comes before Employee Number', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)

    const bank = await screen.findByText(/^Bank Where Employed/)
    const employee = screen.getByText(/^Employee Number/)
    expect(bank.compareDocumentPosition(employee) & Node.DOCUMENT_POSITION_FOLLOWING, 'Employee Number follows the bank').toBeTruthy()
  })

  it('Bank Branch waits for a bank', async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await screen.findByText(/^Bank Branch/)

    expect(branchCombobox()).toBeDisabled()
    await chooseFrom(user, 'Bank Where Employed', 'Absa', ABSA.bankName)
    await waitFor(() => expect(branchCombobox()).toBeEnabled())
  })

  it("offers the chosen bank's own branches together with the fourteen names", async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseFrom(user, 'Bank Where Employed', 'Absa', ABSA.bankName)

    const names = await branchOptionNames(user)
    expect(names).toContain('Sandton City')
    expect(names).toEqual(expect.arrayContaining(BRANCH_OPTIONS))
    // The must-pass half: another bank's branch is not offered.
    expect(names).not.toContain('Menlyn Maine')
  })

  it("a branch the new bank does not offer is cleared when the bank changes; one it does offer is kept", async () => {
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    await openEmployment(user)
    await chooseFrom(user, 'Bank Where Employed', 'Absa', ABSA.bankName)
    await chooseFrom(user, 'Bank Branch', 'Sandton', 'Sandton City')
    expect(branchCombobox()).toHaveValue('Sandton City')

    await chooseFrom(user, 'Bank Where Employed', 'Capitec', CAPITEC.bankName)
    await waitFor(() => expect(branchCombobox(), 'Sandton City is an Absa branch').toHaveValue(''))
    // The display alone proves nothing: a combobox shows blank for any value its new list lacks. Back to Absa, whose
    // list DOES offer Sandton City -- a branch still held in the form would reappear here.
    await chooseFrom(user, 'Bank Where Employed', 'Absa', ABSA.bankName)
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(branchCombobox(), 'the Absa branch was really cleared, not just hidden').toHaveValue('')
    await chooseFrom(user, 'Bank Where Employed', 'Capitec', CAPITEC.bankName)

    await chooseFrom(user, 'Bank Branch', 'Rosebank', 'Rosebank')
    await chooseFrom(user, 'Bank Where Employed', 'Absa', ABSA.bankName)
    await waitFor(() => expect(branchCombobox(), 'Rosebank is one of the fourteen, offered for every bank').toHaveValue('Rosebank'))
  })

  it('R3-F3: a chosen job grade survives "Try again" on the reference lists, and changing the bank still clears it', async () => {
    // The real trigger: the bank-branch list fails, the notice offers "Try again", and the retry gives the listing a
    // new identity -- which re-ran the grade effect, whose first line cleared the grade.
    let branchCalls = 0
    server.use(
      http.get('*/api/banks/branches', () => {
        branchCalls += 1
        return branchCalls === 1 ? HttpResponse.json({ message: 'down' }, { status: 503 }) : HttpResponse.json([ABSA, CAPITEC])
      }),
    )
    const user = setupUser()
    renderWithProviders(<CaptureMember />)
    const retry = await screen.findByRole('button', { name: 'Try again' })
    await openEmployment(user)
    await chooseFrom(user, 'Bank Where Employed', 'Absa', ABSA.bankName)
    const grade = await screen.findByLabelText(/^Job Grade/)
    await waitFor(() => expect(grade).toBeEnabled())
    // ⚠️ Job Grade is a COMBOBOX since 02.5/CM-37 let a grade be typed -- `allowCustom` exists only on
    // SearchableSelect, and a native select cannot hold a value outside its options. Chosen through the
    // same helper as the bank above; what this test asserts is unchanged.
    await chooseFrom(user, 'Job Grade', 'C1', 'C1')

    await user.click(retry)
    await waitFor(() => expect(branchCalls).toBeGreaterThanOrEqual(2))
    await new Promise((resolve) => setTimeout(resolve, 300))
    expect(screen.getByLabelText(/^Job Grade/), 'the notice promised typed values are kept').toHaveValue('C1')

    // The must-pass: a grade belongs to a bank, so choosing another bank still clears it.
    await chooseFrom(user, 'Bank Where Employed', 'Capitec', CAPITEC.bankName)
    await waitFor(() => expect(screen.getByLabelText(/^Job Grade/)).toHaveValue(''))
  })
})
