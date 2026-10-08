Feature: The member register
  The highest-traffic screen in the system: every member on record, fifteen
  filters above them, and a record panel that reads and corrects one member. An
  administrator finds a member, reads their details, and fixes what has changed.

  Background:
    Given the member register is available

  Scenario: The register lists the members on record
    When an administrator opens the member register
    Then the register reports every member on record
    And the register shows a row for each member on the page

  Scenario: Searching the register narrows it to one member
    When an administrator opens the member register
    And they search the register for the first member's employee number
    Then the register reports 1 member
    And the register shows that member's surname

  # Was A61 — the same search, by the number a member is actually known by. Every
  # seeded member's membership number was NULL on a database built from empty
  # (the seeder's Jackson dropped it: the entity field is READ_ONLY). Fixed by
  # 156916e (the seeder's own reader relaxes READ_ONLY for that one property),
  # on server main since ec1d026 by PR #6. Un-skipped 2026-09-21 to prove it;
  # the register strikes A61 when this is green.
  Scenario: Searching the register by membership number narrows it to one member
    When an administrator opens the member register
    And they search the register for the first member's membership number
    Then the register reports 1 member
    And the register shows that member's surname

  # Register paging (A10), held to the server's contract: the register is
  # read a page of slim rows at a time (POST /api/members/register/search), its
  # figures counted apart (GET /api/members/register/overview), and an edit of
  # a record saved since it was read is refused (409 MEMBER_CHANGED_SINCE).
  # The screen that keeps the register's view in its address is judged once
  # the paging client is final.
  Scenario: The register is read from the server a page at a time, each row only what the register shows
    When the register's first two pages of 20 are asked for
    Then each page holds 20 members of one count, and no member is on both
    And each row carries only the register's own columns, and no identity, account or contact detail

  Scenario: A page far past the register's last is answered with its last page, never an empty one
    When a page far past the register's last is asked for
    Then the server answers the register's last page, with its members

  Scenario: The register's figures are counted by the server, each within the total
    When the register's figures are asked for
    Then the figures count the register's members, each status and this month's within the total

  Scenario: An edit of a member saved since it was read is refused, and changes nothing
    Given a member of the scenario's own, read by two administrators at once
    When the first administrator saves a change to that member
    And the second saves a change from the copy read before it
    Then the second is refused because the member was saved since
    And the member keeps the first administrator's change

  # Register paging (A10): the register asks the server for one page at a
  # time, and keeps its view -- the page, the order, the search -- in the
  # address, so a link or a refresh returns to the same place.
  Scenario: The register's second page is read from the server, a page at a time
    When an administrator opens the member register at its second page
    Then the register asks the server for its second page, and shows that page's members

  Scenario: The register can be read in another order
    When an administrator opens the member register sorted by surname, Z to A
    Then the register asks the server for that order, and shows the members in the order the server gave

  Scenario: The register's search finds a member by their membership number
    When an administrator searches the register for the first member's membership number
    Then the server finds that one member, and the register shows them

  Scenario: A search that matches nothing says so
    When an administrator opens the member register
    And they search the register by surname for "Nobodyhasthissurname"
    Then the register reports 0 members
    And the register says no members match

  Scenario: A member's details can be read from the register
    When an administrator opens the member register
    And they open the first member on record
    Then the member's details show their identity number
    And the member's details show their bank

  Scenario: A corrected cellphone number is saved and shown
    When an administrator opens the member register
    And they open the first member the register can save
    And they correct that member's cellphone number
    Then the register confirms the change was saved
    And the member on record has the new cellphone number

  # A36 — no member in the register has an identity type: the seeder never sets
  # one, and the server only derives it from an identity number when the type
  # already reads "ID" (see A17). The edit form makes Identity Type required, so
  # the register refuses every correction — to a phone number, an address,
  # anything — until the administrator also supplies a value the record has
  # never held. The working scenario above supplies it, which is what an
  # administrator has to do; this one asserts that they should not have to.
  @known-broken @defect:A36 @skip
  Scenario: Correcting one field does not require completing another
    When an administrator opens the member register
    And they open the first member the register can save
    And they correct only that member's cellphone number
    Then the register confirms the change was saved

  # Was A32 — the member validator ran against the record being saved, not
  # against what changed, and rejected any debit day outside 20, 25 and 31, so
  # writing a seeded member straight back was a 400 and blamed a debit day nobody
  # touched. Read as FIXED at server main 99936a5: the range is 20–31 and it is
  # checked only when the request CHANGED the day (R7-F2) -- which matters,
  # because 38 of the 57 seeded debit-order members still store a day below the
  # 20th, the first member on record among them (day 1). Un-skipped 2026-09-21
  # to prove it; the register strikes A32 when this and its capture half are green.
  Scenario: Any member on record can be corrected
    When the cellphone number on the first member on record is changed
    Then the correction is accepted

  # B1 · Q2 — SecurityConfig permits every request under /api/**, so an
  # unauthenticated caller can read every member's identity number, bank account
  # number and employer. This scenario is written to the standard the plans bind
  # us to — "no endpoint returns member data to an unauthenticated caller, ever"
  # — and lands skipped, because authorization needs an auth mechanism to
  # authorize against and the application has none. It turns green the day one
  # exists, and it is the thing that will notice.
  @known-broken @defect:B1 @skip
  Scenario: Member data is not readable without signing in
    When an anonymous caller asks the API for every member
    Then they are refused

  # Was A27 — before the design client the register had no loading state, so the
  # screen said no members matched while the data was still on its way. CLOSED
  # 2026-09-16 against the design client. The list is held in flight so "loading"
  # is asserted while it is true, not raced; releasing it proves the members then
  # arrive on the same screen.
  Scenario: The register says it is loading before the members arrive
    Given the member list is held in flight until it is released
    When an administrator navigates to the member register
    Then the register says it is loading
    And the register does not claim there are no members
    When the member list is released
    Then the register reports every member on record
