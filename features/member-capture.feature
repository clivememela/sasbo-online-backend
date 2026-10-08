Feature: Capturing a member
  Capture is the only screen in the system where a record is made from nothing.
  Everywhere else a user edits something that already exists, and a field that
  fails to save is at least visible as a change that did not stick. Here it is
  invisible: the form reports success and the member is short a field nobody
  will notice until it is needed.

  So this journey does not check that a member was created. It checks that
  every field an administrator typed is the field the register holds.

  🔴 Since client 8c6b675 a member is captured by SENDING their welcome letter
  (A64): Save opens the letter, and its Send saves the member and emails them in
  one request, which the application refuses without an email address. So every
  scenario below that saves supplies one, and the stack runs a mail catcher --
  which is also where the letter is read back. A skipped scenario is written the
  way the application should behave, and names the defect standing between it
  and green.

  Background:
    Given the member register is available

  Scenario: The identity number fills in the date of birth and gender
    When an administrator begins capturing a member with:
      | field         | value                        |
      | First Name(s) | Zola                         |
      | Surname       | <a name minted for this run> |
      | Identity Type | I.D. Number                  |
      | I.D. Number   | 9001015009086                |
    Then the form shows the date of birth "01-01-1990"
    And the form shows the gender "Male"

  Scenario: A member cannot be captured without a surname
    When an administrator begins capturing a member with:
      | field         | value       |
      | First Name(s) | Zola        |
      | Identity Type | I.D. Number |
    And they try to save the member
    Then they are told "Surname field is required"
    And no member has been added

  # Was A30 — the form initialised Date Joined to
  # `new Date().toISOString().split("T")` without taking the first element, so it
  # submitted a two-element ARRAY and the API answered 500: no member could be
  # captured through the browser at all. FIXED on client main by 6d54dc3
  # (2026-09-17, `split("T")[0]`) and un-skipped the same day. Since client 8c6b675
  # the save IS the welcome letter's Send (A64); the page object drives it, and
  # this scenario reads the record back through the API.
  Scenario: A captured member reads back exactly as it was entered
    When an administrator captures a member with:
      | field                       | value                        |
      | Title                       | Mr                           |
      | First Name(s)               | Zola Thabo                   |
      | Initials                    | ZT                           |
      | Surname                     | <a name minted for this run> |
      | Identity Type               | I.D. Number                  |
      | I.D. Number                 | <an identity number minted for this run>|
      | Race                        | African                      |
      | Personal Email              | zola@example.invalid         |
      | Work Email                  | zola.work@example.invalid    |
      | Cellphone Number            | +27 82 000 0001              |
      | Work Tel                    | 011 000 0002                 |
      | Home Tel                    | 012 000 0003                 |
      | Residential Address         | 1 Test Street, Sandton       |
      | Employee Number             | EMP-ZZAT-0001                |
      | Employment Type             | Permanent                    |
      | Bank Where Employed         | Bidvest Bank Limited         |
      | Bank Branch                 | Sandton                      |
      | Payment Type                | Debit Order                  |
      | Bank Account Number         | 4001002003                   |
      | Account Type                | Cheque                       |
      | Debit Day                   | 25                           |
      | IBT No.                     | 099                          |
    Then the member on record has every field that was captured

  # The half of A64's change that works, checked where the member would see it.
  # Since client 8c6b675 and server 99936a5 the capture and the welcome letter are
  # one request, so a captured member has been sent their letter -- and the stack's
  # mail catcher holds what a real mailbox would have received.
  Scenario: Capturing a member emails them the welcome letter
    When an administrator captures a member with:
      | field          | value                                    |
      | First Name(s)  | Zola                                     |
      | Surname        | <a name minted for this run>             |
      | Identity Type  | I.D. Number                              |
      | I.D. Number    | <an identity number minted for this run> |
      | Personal Email | <an email address minted for this run>   |
      | Payment Type   | Payroll Deduction                        |
    Then the welcome letter is emailed to their personal address, as a PDF

  # A64 — since client 8c6b675 and server 99936a5 a member is saved only by
  # sending the welcome letter, and the letter's Send is disabled when the form
  # holds no email address, so a member with none cannot be captured at all.
  # SASBO's own register has members with no address (A56 was about them).
  # Written as the application should behave: a member without an email address
  # is captured like any other.
  @known-broken @defect:A64 @skip
  Scenario: A member with no email address can be captured
    When an administrator captures a member with:
      | field         | value                                    |
      | First Name(s) | Zola                                     |
      | Surname       | <a name minted for this run>             |
      | Identity Type | I.D. Number                              |
      | I.D. Number   | <an identity number minted for this run> |
      | Payment Type  | Payroll Deduction                        |
    Then the member on record has every field that was captured

  # A31 was CLOSED on 2026-09-14, by its own second condition -- "the form stops
  # collecting them". Client abbbab6 deleted the History & Remarks panel along
  # with its tab button, so nobody can enter the six administrative fields any more
  # (the form still posts them as fixed defaults, which the server ignores). Its
  # scenario was retired with it.

  # A40 — THREE FIELDS THE MEMBER RECORD CAN STORE CAN NO LONGER BE CAPTURED, and
  # this is a regression rather than the closure A31 got.
  #
  # `remarks`, `remarks2` and `useAltAddress` are declared on both the Member
  # entity and MemberDto — the database has somewhere to put them. They were
  # collected on the 'History & Remarks' tab, and that tab was removed from the
  # client along with the six fields that had no column at all. So the
  # six that could not be stored stopped being collected, which is right, and three
  # that COULD be stored stopped being collected with them, which nobody appears to
  # have chosen.
  #
  # It is written as the application should behave: these three are ordinary member
  # fields and an administrator should be able to record them. Whether that means
  # restoring the tab or moving the three fields elsewhere is a decision, not an
  # edit — which is why this scenario names the fields and not a tab.
  #
  # ⚠️ THE ROW ID IS PROVISIONAL, reported for allocation rather than taken: the
  # register is a shared namespace with no allocator and the arc that owns this
  # screen is working in it now.
  #
  # ⏳ IT WILL NOT RUN EVEN WHEN A30 IS FIXED, and that is deliberate rather than
  # an oversight. The page object refuses a removed tab by name, so this scenario
  # fails with "that tab is gone" — which IS the defect, stated as loudly as the
  # suite can state it from here.
  @known-broken @defect:A40 @skip
  Scenario: The remarks an administrator records against a member are kept
    When an administrator captures a member with:
      | field                       | value                        |
      | First Name(s)               | Zola                         |
      | Surname                     | <a name minted for this run> |
      | Identity Type               | I.D. Number                  |
      | I.D. Number                 | <an identity number minted for this run>|
      | Personal Email              | zola@example.invalid         |
      | Remarks / Comments          | captured by the suite        |
      | 2nd Remarks Field           | second remark                |
      | Use Alternate Address (Y/N) | N                            |
    Then the member on record has every field that was captured

  # Was A32 — the server replaced the debit day the administrator chose with the
  # one on the bank's subscription row, then rejected its own value against a
  # rule that only allowed 20, 25 and 31, so a debit-order member could not be
  # captured for thirteen of the fifteen banks. Read as FIXED at server main
  # 99936a5: the rule is 20–31 (`DebitDay`), and the bank's day is assigned only
  # inside it, so Absa's (5) leaves the administrator's 20 standing. Un-skipped
  # 2026-09-21 to prove it; the register strikes A32 when this is green. What
  # survives is the overwrite INSIDE the range, A67 -- Absa's day is outside it,
  # so this scenario does not reach that.
  Scenario: A debit-order member can be captured for a bank with its own debit day
    When an administrator captures a member with:
      | field               | value                        |
      | First Name(s)       | Zola                         |
      | Surname             | <a name minted for this run> |
      | Identity Type       | I.D. Number                  |
      | I.D. Number         | <an identity number minted for this run>|
      | Personal Email      | zola@example.invalid         |
      | Bank Where Employed | Absa Bank Limited            |
      | Bank Branch         | Sandton                      |
      | Payment Type        | Debit Order                  |
      | Bank Account Number | 4001002003                   |
      | Account Type        | Cheque                       |
      | Debit Day           | 20                           |
    Then the member on record has every field that was captured

  # A4 — GET /api/job-grades answers 200 with an empty list, because the query
  # filters by a bank code the table does not carry. The Job Grade dropdown on
  # this form is fed by it, so it offers "No job grades available" for every
  # bank and no member can be captured with a job grade at all.
  @known-broken @defect:A4 @skip
  Scenario: A member is captured with their job grade
    When an administrator captures a member with:
      | field               | value                        |
      | First Name(s)       | Zola                         |
      | Surname             | <a name minted for this run> |
      | Identity Type       | I.D. Number                  |
      | I.D. Number         | <an identity number minted for this run>|
      | Personal Email      | zola@example.invalid         |
      | Bank Where Employed | Bidvest Bank Limited         |
      | Job Grade           | A1                           |
    Then the member on record has every field that was captured

  # A39 (provisional, never allocated) was RETIRED on 2026-09-14: upstream client
  # 4a7a67b removed the 'Sasbo Structures' tab, so these answers can no longer be
  # submitted from capture. Where the columns can be set now -- nowhere, as of that
  # merge -- is recorded beside TABS_REMOVED_FROM_THE_CLIENT in pages/MemberCapturePage.js.

  # A35 — every "searchable" field on this form renders its label with `htmlFor`
  # pointing at an id nothing carries: the control underneath is a bare <input>
  # inside a hand-rolled component that sets neither `id` nor `aria-label`. The
  # label is attached to nothing, so a screen reader announces an unlabelled
  # text box, and the field cannot be addressed by the name printed above it.
  # Six fields on this form are affected, including both bank pickers.
  @known-broken @defect:A35 @skip
  Scenario: Every field on the capture form can be found by the name above it
    When an administrator opens the capture form
    Then every field on the form is labelled
