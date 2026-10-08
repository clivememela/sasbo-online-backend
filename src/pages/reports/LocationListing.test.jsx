// @vitest-environment jsdom -- the location listing against the records it is actually handed
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'

import LocationListing from './LocationListing'
import { renderWithProviders } from '../../test/renderWithProviders'

/**
 * A BRANCH RECORD IS NOT REQUIRED TO CARRY MEMBERS, AND THIS PAGE ASSUMED IT WAS.
 *
 * `Locations.jsx:127` hands every `/api/branches` result straight to this component as
 * `data`. A branch row has no `members` key, so `listingData.members.map(...)` threw
 * `TypeError: Cannot read properties of undefined (reading 'map')` and the ErrorBoundary
 * replaced the entire page with *"This page could not be displayed."* — on every successful
 * location search.
 *
 * 🔴 IT WAS INVISIBLE TO EVERY INSTRUMENT POINTED AT THIS ROUTE. The width matrix loads
 * `/reports/locations` at seven widths and finds a search form that renders perfectly; the
 * route smoke mounts it and finds the same. The crash needs a *search to have been run*, and
 * nothing in the suite ran one. It was found by driving the route into the state the matrix
 * cannot reach — which is the entire argument for `05.1`'s state harness.
 *
 * The three cases below are one guard and its two non-vacuity halves:
 *
 *   ① a record without members renders, and says the location is empty
 *   ② the blank-form sample still renders its four rows        (else ① passes by rendering nothing)
 *   ③ the sample never appears under a supplied record         (else ① could be "fixed" by merging)
 *
 * ③ is the one that matters. The obvious repair — filling a real record's gaps from the
 * sample — also makes ① pass, while printing four invented members under a real branch's
 * name. That is a worse defect than the crash, and only ③ can tell the two apart.
 */

/** Exactly the shape `/api/branches` returns: no `members` key at all. */
const branchRecord = {
  id: 1,
  bankName: 'Synthetic Bank',
  branchName: 'Sample Branch',
  randomAccessNumber: '250655',
  areaRepName: 'Synthetic Area Rep',
}

describe('LocationListing, given the record Locations actually hands it', () => {
  it('renders a branch that has no members instead of taking the page down', () => {
    expect(() => renderWithProviders(<LocationListing data={branchRecord} />)).not.toThrow()

    expect(screen.getByText(/No members are recorded at this location/i)).toBeInTheDocument()
    // The listing itself is still there -- an empty members list is not an empty page.
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByText('Mem No')).toBeInTheDocument()
  })

  it('still renders the blank form sample when no record is supplied', () => {
    // ✅ THE MUST-PASS HALF. Without it, deleting the map entirely would satisfy the case
    // above: "no members recorded" is also what a component that renders nothing says.
    renderWithProviders(<LocationListing />)

    expect(screen.getByText('SAMPLE ,A, MS')).toBeInTheDocument()
    expect(screen.getByText('PLACEHOLDER ,D, MRS')).toBeInTheDocument()
    expect(screen.queryByText(/No members are recorded/i)).not.toBeInTheDocument()
  })

  it('never fills a supplied record from the sample', () => {
    // 🔴 THE ONE THAT CATCHES THE WRONG FIX. Printing invented members under a real
    // branch heading is worse than the crash this replaced: a crash is obviously broken,
    // and a table of four people who do not work at that branch is not.
    renderWithProviders(<LocationListing data={branchRecord} />)

    expect(screen.queryByText('SAMPLE ,A, MS')).not.toBeInTheDocument()
    expect(screen.queryByText(/branch\.manager@example\.invalid/)).not.toBeInTheDocument()
    // ...and the record's own values are the ones on screen.
    expect(screen.getByText('Synthetic Area Rep')).toBeInTheDocument()
  })
})

/**
 * 🗣️ "COMPLEMENT", THE STAFF OF THE BRANCH -- spelt "Compliments" on this listing until the owner promised the fix at
 * the 5 Oct Show & Tell. The first case is the guard; the second is its must-pass, so a listing that rendered no
 * footer at all could not pass the first by saying nothing.
 */
describe('"Complement", spelt as SASBO means it', () => {
  it('names the staff complement "Complement" -- and "Compliment" nowhere a person reads it', () => {
    renderWithProviders(<LocationListing />)

    expect(screen.getByText('Complement:')).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/Compliment/i)
  })

  it('a branch record from /api/branches fills it with the branch\'s own staff complement', () => {
    renderWithProviders(<LocationListing data={{ ...branchRecord, branchStaffComplement: 31 }} />)

    expect(screen.getByText('Complement:').nextSibling).toHaveTextContent('31')
  })
})

/**
 * 🔴 EVERY RESULT CARD ON LOCATION LISTINGS PRINTED BLANK (found by the October build's manual pass, 7 Oct 2026).
 *
 * The card reads the sample's own keys -- `bank`, `ibtNo`, `raNo`, `telNo`, `faxNo`, `branch`, `address` -- and
 * `Locations` hands it the server's `BankBranch` rows, whose keys are `bankName`, `branchCode`, `randomAccessNumber`,
 * `phoneNumber`, `fax`, `branchName` and an address OBJECT. Not one key matched, so every label of the header stood
 * beside nothing, on every card of every search, and the footer's manager, membership and last visit with them. The
 * crash fix above made the card render; nothing ever asked whether it rendered the branch.
 *
 * The record below is the shape `GET /api/branches` answers -- the entity's own fields as Jackson writes them, the
 * address with its parts -- with invented values. The first case is the defect; the second is its must-pass twin, so a
 * card that showed the record's values by accident of the sample could not pass the first.
 */
const API_BRANCH = Object.freeze({
  id: 41,
  branchCode: '900901',
  bankName: 'Invented Bank Limited',
  bankAbbreviation: 'INV',
  branchName: 'Invented Central',
  area: 'Invented Area',
  randomAccessNumber: 'RAN-901',
  phoneNumber: '011 000 0901',
  managerName: 'Invented Manager',
  fax: '011 000 0902',
  email: 'manager.invented@example.invalid',
  category: null,
  repsCount: 2,
  membersCount: 28,
  insertsCount: 0,
  branchStaffComplement: 40,
  branchMemberComplement: 31,
  lastVisitedDate: '2026-08-14',
  physicalAddress: {
    complexOrBuilding: null, streetNumber: '9', streetName: 'Invented Street', suburb: null, city: null,
    province: null, postalCode: null, cityName: 'Invented Town', suburbName: null, provinceName: null,
  },
  postalAddress: 'P.O. Box 0901, Invented Town',
  createdAt: '2026-09-01T08:00:00',
})

/** The value printed beside a label of the card. */
const valueBeside = (label) => screen.getByText(`${label}:`).nextSibling?.textContent

describe('a Location Listing card shows the branch the server sent', () => {
  it('🔴 the header names the bank, the branch, its numbers and its address; the footer its manager, complement, membership and last visit', () => {
    renderWithProviders(<LocationListing data={API_BRANCH} />)

    expect(valueBeside('Bank')).toBe('Invented Bank Limited')
    expect(valueBeside('Branch')).toBe('Invented Central')
    expect(valueBeside('R/A No')).toBe('RAN-901')
    expect(valueBeside('IBT No')).toBe('900901')
    expect(valueBeside('Tel No')).toBe('011 000 0901')
    expect(valueBeside('Fax No')).toBe('011 000 0902')
    expect(valueBeside('Address')).toBe('9 Invented Street, Invented Town')
    expect(valueBeside('Bank Manager')).toBe('Invented Manager')
    expect(valueBeside('Bank Manager Email')).toBe('manager.invented@example.invalid')
    expect(valueBeside('Complement')).toBe('40')
    expect(valueBeside('Membership')).toBe('28')
    expect(valueBeside('Last Visited')).toBe('Aug 2026')
  })

  it('must-pass: what the server does not send reads as missing (an em dash), never as a blank or as the sample', () => {
    renderWithProviders(<LocationListing data={{ ...API_BRANCH, physicalAddress: null, phoneNumber: '' }} />)

    expect(valueBeside('Cost Code')).toBe('—')
    expect(valueBeside('Tel No')).toBe('—')
    expect(valueBeside('Address')).toBe('P.O. Box 0901, Invented Town')
    expect(screen.queryByText('SYNTHETIC BANK')).not.toBeInTheDocument()
  })
})
