import { describe, expect, it } from 'vitest'

import { DRAFT_FIELDS, draftOf, formFieldsOf } from './visitDraft.js'

/**
 * THE LOCATION VISIT FORM AS A DRAFT (October build, 5 Oct Show & Tell). What a draft sends: each field under the
 * server's name, the counts whole numbers, an empty field left out; the R/A number needed to save, the day only to
 * submit; a person's details never. And a saved draft fills the form back, leaving nothing of another behind.
 */
describe('draftOf -- what a draft sends', () => {
  const filled = {
    raNumber: ' 900001 ', dateOfVisit: '2026-09-28', timeIn: '09:00', timeOut: '10:30',
    organiserName: 'INVENTED ORGANISER', complement: '40', membership: '12', noOfNonMembers: '3',
    noOfNewMembersRecruited: '2', noOfNonMembersInterviewed: '5',
    branchRepName: 'INVENTED REPRESENTATIVE', cellPhoneEmail1: 'rep@example.invalid',
  }

  it('sends each field under the server\'s name, the counts as whole numbers -- and no person\'s details', () => {
    const { draft, errors } = draftOf(filled)

    expect(errors).toEqual({})
    expect(draft).toEqual({
      raNumber: 900001, visitedOn: '2026-09-28', timeIn: '09:00', timeOut: '10:30', organiser: 'INVENTED ORGANISER',
      complementFound: 40, membershipFound: 12, nonMembers: 3, recruited: 2, nonMembersInterviewed: 5,
    })
    expect(JSON.stringify(draft)).not.toMatch(/INVENTED REPRESENTATIVE|rep@example/)
  })

  it('needs only the R/A number to save: an empty field is left out, never sent as "" or 0', () => {
    expect(draftOf({ raNumber: '900001', complement: '  ' })).toEqual({ draft: { raNumber: 900001 }, errors: {} })
  })

  it('needs the day to submit -- said at the day', () => {
    expect(draftOf({ raNumber: '900001' }, { submitting: true }).errors)
      .toEqual({ dateOfVisit: 'Choose the day of the visit.' })
  })

  it('says each field it cannot send at that field: no R/A number, one that is not a number, a count that is not whole', () => {
    expect(draftOf({ raNumber: '' }).errors).toEqual({ raNumber: 'The R/A number says which workplace was visited.' })
    expect(draftOf({ raNumber: 'R900001' }).errors.raNumber).toBe('An R/A number is a number of up to nine digits.')
    expect(draftOf({ raNumber: '0' }).errors.raNumber).toBe('An R/A number is a number of up to nine digits.')
    expect(draftOf({ raNumber: '900001', complement: 'forty', noOfNonMembers: '1.5' }).errors)
      .toEqual({ complement: 'A whole number, or leave it empty.', noOfNonMembers: 'A whole number, or leave it empty.' })
  })
})

describe('formFieldsOf -- a saved draft, back into the form', () => {
  it('fills every field a draft keeps, and the branch with the workplace\'s name', () => {
    const fields = formFieldsOf({
      raNumber: 900001, workplaceName: 'INVENTED MAIN STREET', visitedOn: '2026-09-28', timeIn: '09:00',
      timeOut: null, organiser: 'INVENTED ORGANISER', complementFound: 40, membershipFound: 12, nonMembers: null,
      recruited: 2, nonMembersInterviewed: null,
    })

    expect(fields).toEqual({
      raNumber: '900001', dateOfVisit: '2026-09-28', timeIn: '09:00', timeOut: '', organiserName: 'INVENTED ORGANISER',
      complement: '40', membership: '12', noOfNonMembers: '', noOfNewMembersRecruited: '2',
      noOfNonMembersInterviewed: '', branchDepartment: 'INVENTED MAIN STREET',
    })
  })

  it('round trip: a form saved and opened again sends the same draft', () => {
    const saved = { raNumber: 900001, visitedOn: '2026-09-28', organiser: 'INVENTED ORGANISER', complementFound: 40 }
    expect(draftOf(formFieldsOf(saved)).draft).toEqual(saved)
  })

  it('every field the server names maps to a field of the form', () => {
    expect(Object.keys(DRAFT_FIELDS)).toEqual(['raNumber', 'visitedOn', 'timeIn', 'timeOut', 'organiser',
      'complementFound', 'membershipFound', 'nonMembers', 'recruited', 'nonMembersInterviewed'])
  })
})
