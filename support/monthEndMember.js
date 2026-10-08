import { MembersApi } from '../api/MembersApi.js'
import { MemberCapturePage } from '../pages/MemberCapturePage.js'
import { mintEmailAddress, mintIdentityNumber, mintTestName } from './guards.js'

/**
 * A member of a month-end scenario's own.
 *
 * CAPTURED THROUGH THE SCREEN, AS AN ADMINISTRATOR WOULD, because the browser dating the capture is half of what the
 * joined figure is about: the member is joined on the day the capturing browser gives them, and the server counts them
 * in that day's month (MD55). A member written straight to the API would carry whatever date the scenario chose.
 *
 * Found again by the surname minted for it -- never by position -- and retired when the scenario ends, through the
 * suite's own neutraliser (`MembersApi.neutralise`: the status goes through Change status with SASBO's code 3,
 * *Enrolled in error*). Registered the moment the member exists, so a scenario that fails half-way still retires it.
 * Each member registers its OWN retirement: a scenario that captures two retires two.
 *
 * @returns {Promise<object>} the member as the register holds it -- its id, status and join date among the rest
 */
export async function captureAMemberOfItsOwn({ page, api, world }) {
  const surname = mintTestName('member')
  const capture = await new MemberCapturePage(page).open()
  const fields = [
    ['First Name(s)', 'Zola'],
    ['Surname', surname],
    ['Identity Type', 'I.D. Number'],
    ['I.D. Number', mintIdentityNumber()],
    ['Personal Email', mintEmailAddress()],
    ['Payment Type', 'Payroll Deduction'],
  ]
  for (const [field, value] of fields) {
    await capture.fillField(field, value)
  }
  await capture.save()

  const membersApi = new MembersApi(api.request)
  const member = (await membersApi.listMembers()).find((candidate) => candidate.surname === surname)
  if (!member) {
    throw new Error(`The member captured as ${surname} is not in the register`)
  }
  world.registerRestore(`the member captured as ${surname}, retired`, () => membersApi.neutralise(member.id))
  return member
}
