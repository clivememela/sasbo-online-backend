Feature: Subscriptions and debit orders
  The money-adjacent half of the system. The subscriptions table is what every
  bank's members are charged, and it is also reference data the capture path
  reads: creating a member overwrites their monthly subscription and their debit
  day from the row for their bank. A wrong row here is not a wrong screen, it is
  a wrong charge.

  Background:
    Given the subscriptions register is available

  Scenario: The subscriptions register lists every bank's subscription
    When an administrator opens the subscriptions register
    Then the register shows a subscription for "Nedbank Limited"
    And the register shows one row for every subscription on record

  Scenario: Filtering the subscriptions register narrows it to one bank
    When an administrator opens the subscriptions register
    And they filter the subscriptions by the bank "Capitec Bank Limited"
    Then the register shows a subscription for "Capitec Bank Limited"
    And the register no longer shows a subscription for "Nedbank Limited"

  # Was A30 — no member could be captured through the browser, so this, the one
  # assertion joining the two halves of the system (the subscriptions table is
  # what a new member is actually charged), sat behind that wall. Capture works
  # since client 6d54dc3; un-skipped 2026-09-17.
  # ⚠️ A58: this scenario captures against Nedbank Limited, whose ONLY seeded
  # subscription row is PENDING at 99.99 — and MemberServiceImpl resolves a bank's
  # subscription with findFirst(), never reading the status. So what passes here is
  # the rate the application chose, which is the defect stated as behaviour. It is
  # left running and NOT tagged @known-broken: the round trip it asserts (the bank's
  # subscription reaches the member) is real and worth guarding. When A58 is fixed
  # this scenario must be re-pointed at a bank with an ACTIVE row, or the seed's
  # Nedbank row made ACTIVE — otherwise it will redden for the right reason.
  Scenario: A new member is charged what their bank's subscription says
    When an administrator captures a member with:
      | field               | value                        |
      | First Name(s)       | Zola                         |
      | Surname             | <a name minted for this run> |
      | Identity Type       | I.D. Number                  |
      | I.D. Number         | <an identity number minted for this run>|
      | Personal Email      | zola@example.invalid         |
      | Payment Type        | Payroll Deduction            |
      | Bank Where Employed | Nedbank Limited              |
    Then the member is charged the subscription on record for "Nedbank Limited"

  Scenario: The Debits screen finds a member by surname
    When an administrator searches the Debits screen for the surname "Naidoo"
    Then the search reports at least one member

  Scenario: The Debits screen says so when a search matches nothing
    When an administrator searches the Debits screen for the surname "Nobodyhasthissurname"
    Then the search says no members match

  # ACB 04.3 -- the Re-Debits screen, Debit Order Deductions' Debits. A decision
  # on an unpaid is final (the server refuses a second), so nothing can be put
  # back afterwards. The Given therefore mints what the scenario decides: the
  # seed's one undecided unpaid while it lasts, then a late return on a paid
  # debit of the same returned month. It never touches a decision the seed made.
  Scenario: An administrator presents an unpaid debit again from the Re-Debits screen
    Given the latest returned debit order month has an undecided unpaid its code would present again
    When an administrator presents that unpaid again from the Debits screen
    Then the screen says the unpaid is presented again in the next run
    And the server holds the decision to present it again

  # Was A33 — PUT /api/subscriptions/{id} answered 500 for every row AFTER the
  # write had committed: the service mapped the saved entity to its DTO once the
  # transaction had closed, so serialising the lazy Bank proxy threw, and the
  # administrator was told a save had failed that had not. Fixed by 71bf9f7 (ACB
  # groundwork: the PUT maps inside the transaction), which reached server main
  # in ec1d026 by PR #7. Un-skipped 2026-09-21 to prove it on main; the register
  # strikes A33 when this is green.
  # Since the October build only staff change amounts (the 5 Oct Show & Tell):
  # the write needs a permission the Chief Financial Officer holds, so the
  # correction is the CFO's. The register is still read without signing in.
  Scenario: A corrected debit order day is kept
    When the Chief Financial Officer corrects the debit order day on "Bank Zero Mutual Bank" to 25
    And an administrator opens the subscriptions register
    Then the register shows a debit order day of "25" for "Bank Zero Mutual Bank"

  # The user manual's reading of Maintenance (6 Oct): an emptied debit order day
  # was saved as 0 and shown as "NaN", and what a row's ACTIVE decides was said
  # nowhere. The day is refused at its field before anything is sent (and by the
  # server too), and a sentence above the table says what ACTIVE decides.
  Scenario: An emptied debit order day is refused at its field, and nothing is sent
    Given the Chief Financial Officer is signed in
    When an administrator opens the subscriptions register
    Then the register says a bank is debited only while its row is ACTIVE
    When they empty the debit order day for "Nedbank Limited" and save it
    Then the debit order day is refused in words at its field, and nothing is sent

  # The other half of the same rule: without signing in the rows can be read,
  # the screen offers no change and says why, and the server refuses a change
  # asked for directly -- in its one refusal for a caller nobody signed in.
  Scenario: Without signing in, the subscriptions can be read but not changed
    When an administrator opens the subscriptions register
    Then the register says that changing an amount needs signing in, and offers no change
    And a change to a subscription asked for without signing in is refused until someone signs in

  # A5 — DataSeeder matches subscription rows to banks by exact name and drops
  # the bank silently when no row matches: no log, no error, no failed startup.
  # One row in the live register therefore has no bank at all, and the screen
  # renders it as an empty first cell — a subscription that charges nobody,
  # sitting in the middle of the money table.
  @known-broken @defect:A5 @skip
  Scenario: Every subscription on the register names its bank
    When an administrator opens the subscriptions register
    Then every row names a bank
