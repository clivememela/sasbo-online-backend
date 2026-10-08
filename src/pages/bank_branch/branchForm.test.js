import { describe, expect, it } from 'vitest'

import { BRANCH_FORM_KEYS, EMPTY_BRANCH_FORM, toBranchForm, toBranchPayload, toBranchRecord } from './branchForm'

/**
 * The branch dialog's two edges (A29): the API's record in, `BankBranchDto`'s keys out. The record is the ENTITY's
 * shape -- `branchName`, the address as an object with `{id, name}` rows -- and the DTO's is `branch` and a line.
 */
const RECORD = {
  id: 7,
  bankName: 'Synthetic Bank Limited',
  branchName: 'Market Square',
  branchCode: '123456',
  physicalAddress: { streetNumber: '9', streetName: 'Synthetic Road', suburb: { id: 1, name: 'Testville' }, city: null, province: null, postalCode: '0001' },
  postalAddress: null,
  tableCode: 3,
  subsValueBranch: 1740.5,
  category: 'A',
}

describe('toBranchForm: the record as the form holds it', () => {
  it('names the branch from branchName, and writes the address as the server formats it', () => {
    const form = toBranchForm(RECORD)
    expect(form.branch).toBe('Market Square')
    expect(form.physicalAddress).toBe('9, Synthetic Road, Testville, 0001')
  })

  it('holds every field as a string: numbers as their digits, nothing as empty', () => {
    const form = toBranchForm(RECORD)
    expect(form.tableCode).toBe('3')
    expect(form.subsValueBranch).toBe('1740.5')
    expect(form.postalAddress).toBe('')
    expect(Object.values(form).every((value) => typeof value === 'string')).toBe(true)
  })

  it('carries exactly the edited fields, and an empty form for a new branch', () => {
    expect(Object.keys(toBranchForm(RECORD)).sort()).toEqual([...BRANCH_FORM_KEYS].sort())
    expect(toBranchForm(null)).toEqual(EMPTY_BRANCH_FORM)
  })

  it('still reads the DTO\'s own spelling, which an optimistic answer carries', () => {
    expect(toBranchForm({ branch: 'Old Town', physicalAddress: '1 Line Street' })).toMatchObject({
      branch: 'Old Town', physicalAddress: '1 Line Street',
    })
  })
})

describe('toBranchPayload: the form as BankBranchDto reads it', () => {
  it('trims text, sends a blank as null, and numbers as numbers', () => {
    const payload = toBranchPayload({ ...toBranchForm(RECORD), managerName: '  Synthetic Person  ', tableCode: '' }, toBranchForm(RECORD))
    expect(payload.managerName).toBe('Synthetic Person')
    expect(payload.tableCode).toBeNull()
    expect(payload.subsValueBranch).toBe(1740.5)
    expect(payload.postalAddress).toBeNull()
    expect(payload).not.toHaveProperty('branchName')
    expect(payload).not.toHaveProperty('category')
  })

  it('🔑 leaves the address out when it was not changed, so the stored one is kept whole', () => {
    const opened = toBranchForm(RECORD)
    expect(toBranchPayload({ ...opened }, opened)).not.toHaveProperty('physicalAddress')
  })

  it('sends a changed address as the line typed -- a blank one too, which clears it', () => {
    const opened = toBranchForm(RECORD)
    expect(toBranchPayload({ ...opened, physicalAddress: '2 New Street ' }, opened).physicalAddress).toBe('2 New Street')
    expect(toBranchPayload({ ...opened, physicalAddress: '' }, opened).physicalAddress).toBe('')
  })

  it('for a new branch, sends an address only when one was typed', () => {
    expect(toBranchPayload({ ...EMPTY_BRANCH_FORM, bankName: 'A', branch: 'B' })).not.toHaveProperty('physicalAddress')
    expect(toBranchPayload({ ...EMPTY_BRANCH_FORM, physicalAddress: '3 Road' }).physicalAddress).toBe('3 Road')
  })
})

describe('toBranchRecord: what the list behind the dialog is given', () => {
  it('the server\'s answer as it came, when it answered the record', () => {
    const answer = { ...RECORD, managerName: 'Synthetic Person' }
    expect(toBranchRecord(answer, RECORD, {})).toBe(answer)
  })

  it('an optimistic answer folded onto the record opened, in the record\'s spelling', () => {
    const payload = toBranchPayload({ ...toBranchForm(RECORD), branch: 'Renamed' }, toBranchForm(RECORD))
    const record = toBranchRecord({ ...payload, id: 7 }, RECORD, payload)
    expect(record).toMatchObject({ id: 7, branchName: 'Renamed', category: 'A' })
    expect(record.physicalAddress, 'an unchanged address is still the stored one').toBe(RECORD.physicalAddress)
  })
})
