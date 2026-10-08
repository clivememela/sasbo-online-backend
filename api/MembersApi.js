import { ApiClient } from './ApiClient.js'

/**
 * The members REST surface. Written by copying `BanksApi` -- which is the test
 * of whether Phase 5's harness was actually finished.
 *
 * WHAT THE SERVER EXPOSES, read from the running application rather than from
 * memory:
 *
 *   GET   /api/members            every member, unpaginated
 *   POST  /api/members/register/search  a page of the register's slim rows (register paging, A10)
 *   GET   /api/members/register/overview the register's figures, counted by the server
 *   GET   /api/members/{id}       one member
 *   POST  /api/members            create one
 *   PUT   /api/members/{id}       replace one -- never its status, since month end 03.4
 *   POST  /api/members/{id}/status change its status, by SASBO's code (month end 03.1)
 *   PATCH /api/members/{id}/banking
 *   PATCH /api/members/{id}/subscription
 *
 * 🔴 THERE IS NO DELETE, exactly as there is none for branches. The difference
 * is that a member journey CANNOT route around it: capture is the journey, and
 * a capture that does not save asserts nothing. So this client creates, and
 * `neutralise` below is a MITIGATION rather than a cleanup -- it is honest
 * about that, because a helper called `delete` that does not delete is how a
 * suite ends up believing it cleans up after itself.
 */
export class MembersApi extends ApiClient {
  /**
   * Every member, SORTED BY ID -- for the same reason `BanksApi` sorts
   * branches. `MemberController.getAll` has no `ORDER BY`, so "the first
   * member" is a different record between calls unless the suite decides.
   */
  async listMembers() {
    const members = await this.json(await this.get('/api/members'))
    return members.sort((a, b) => a.id - b.id)
  }

  async findMember(id) {
    return this.json(await this.get(`/api/members/${id}`))
  }

  /**
   * A page of the register (register paging, A10): `{ content, page, size, totalElements, totalPages, sort }`, each row
   * the register's own columns. The question travels in the BODY -- a name searched for is never in a URL.
   */
  async registerPage(query = {}) {
    return this.json(await this.post('/api/members/register/search', query))
  }

  /** The register's figure strip, counted by the server over the caller's own register. */
  async registerOverview() {
    return this.json(await this.get('/api/members/register/overview'))
  }

  /**
   * PUT a member exactly as `copy` has it -- its `version` included -- and answer what the server said, `{ status,
   * body }`, never thrown: a journey asserting a refusal changes nothing even if the refusal it expects has gone.
   */
  async attemptUpdate(copy) {
    const response = await this.put(`/api/members/${copy.id}`, copy, { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }

  /**
   * Create a member.
   *
   * `expectOk: false` is available to callers that are asserting a REFUSAL --
   * the debit-day scenario needs the 400 and its message, not a thrown error.
   */
  async createMember(payload, options) {
    const response = await this.post('/api/members', payload, options)
    return { status: response.status(), body: await this.json(response) }
  }

  async updateMember(id, changes) {
    const current = await this.findMember(id)
    return this.json(await this.put(`/api/members/${id}`, { ...current, ...changes }))
  }

  /**
   * Put a member back exactly as they were found.
   *
   * 🔴 NOT "put the field back", and the difference cost a run. A correction
   * made through the SCREEN is not one field: opening the modal and choosing an
   * identity type also clears the date of birth and the gender, and saving
   * normalises the payment type -- so a scenario that restores only what it
   * meant to change leaves three other fields altered, and the NEXT run starts
   * from data the seeder never produced. That is the shape of test pollution
   * that looks like flake a week later.
   *
   * The whole record goes back, from the copy the scenario read before it
   * touched anything.
   *
   * 🔴 AT THE VERSION IT HAS NOW, NOT THE ONE IT WAS READ AT. Since the October
   * build a member carries a `version`, and an edit naming one other than the
   * record's is refused `409 MEMBER_CHANGED_SINCE` (server `oct/register-paging`
   * 98e9595, V68) -- so a restore sending the copy read before the scenario's own
   * edit would be refused by that very edit. The values are the original's; only
   * the version is read again. (A server without versions answers none, and the
   * write names none.)
   */
  async restore(original) {
    const { version } = await this.findMember(original.id)
    return this.json(await this.put(`/api/members/${original.id}`, { ...original, version }))
  }

  /**
   * Change one field and hand back the function that puts the old value back.
   * The reversible-update pattern from `BanksApi`, unchanged: it is how a
   * journey asserts real persistence against records it must not remove.
   */
  /** Domain hooks for `ApiClient.changeFieldReversibly`. */
  get recordNoun() {
    return 'member'
  }

  findOne(id) {
    return this.findMember(id)
  }

  updateOne(id, changes) {
    return this.updateMember(id, changes)
  }

  /**
   * 🔴 NOT A DELETE, and deliberately not named like one.
   *
   * The members API has no `DELETE` (register row D9), so a captured member
   * cannot be removed. The best available end state is a record that is
   * unmistakably ours and unmistakably dead: the surname already carries the
   * suite's `ZZ-AT` marker, and this moves it to a terminal status so it can
   * never be mistaken for a live member by anyone reading the register.
   *
   * The row still exists. That is the defect, it is filed, and the compose
   * stack contains the blast radius by building its database from empty for
   * every run -- which is exactly the containment a shared or deployed target
   * would not give.
   *
   * 🔁 MONTH END 03.3: THE STATUS GOES THROUGH CHANGE STATUS. Since 03.4 the PUT
   * refuses a changed status (409 `STATUS_VIA_CHANGE_STATUS`), so the remark is
   * written by the PUT, the status unchanged, and the member is retired through
   * `POST /api/members/{id}/status` with SASBO's deletion code 3, "Enrolled In
   * error" -- the code that says what a test record is. The status the member
   * has is sent as `expectedStatus`, so a retire after one already landed is the
   * route's no-op, not a second change.
   */
  async neutralise(id) {
    await this.updateMember(id, { remarks: 'Automation test record. No delete endpoint exists (D9).' })
    const { status } = await this.findMember(id)
    await this.json(await this.post(`/api/members/${id}/status`, {
      toStatus: 'Resigned',
      expectedStatus: status,
      reason: 'DELETION_CODE',
      deletionCode: 3,
      note: 'Automation test record, retired by the suite.',
    }))
  }
}
