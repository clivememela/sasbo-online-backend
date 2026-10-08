Feature: When the backend is unavailable
  The third of the three states every list surface owes a user — loading, empty,
  and failed — is the only one that cannot be asserted without a real failure.
  Mocking a response proves a component handles a fabricated error; it proves
  nothing about a real network failure reaching a real component through a real
  proxy, which is the situation an administrator is actually in.

  So these scenarios stop the API container, assert what the screen says, and
  start it again. They run alone, after everything else has finished, because
  they take down the stack every other scenario is using.

  Scenario: The bank listing reports the failure rather than an empty register
    Given an administrator has the bank listing open
    When the API is stopped
    And they reload the bank listing
    Then the listing reports that it could not be loaded
    And the listing does not claim there are no banks
    When the API is started again
    And they reload the bank listing
    Then the listing shows "Absa Bank Limited"

  # Was A14 — the client had no request timeout, so how long an administrator
  # watched a spinner was nginx's default proxy_read_timeout: SIXTY SECONDS,
  # measured here. The design client gives every read a 10-second ceiling of its
  # own. CLOSED 2026-09-16. Asserted at twenty seconds: twice the client's
  # ceiling, so a slow host does not flake it, and a third of the proxy's, so the
  # old behaviour still fails it.
  Scenario: The bank listing gives up on its own rather than waiting for the proxy
    Given the API is stopped
    When an administrator navigates to the bank listing
    Then the listing reports that it could not be loaded before the proxy would give up

  # Was A27 — before the design client, the member register handled a failed
  # fetch with console.error and nothing else, so an unreachable backend left the
  # list empty and the screen said no members matched: the administrator was
  # told, confidently, that SASBO has no members. That is why this asserts the
  # ABSENCE of the empty-state text and not only the presence of an error.
  # CLOSED 2026-09-16 against the design client, retry included.
  Scenario: The member register reports that it could not be loaded
    Given the API is stopped
    When an administrator navigates to the member register
    Then the register reports that it could not be loaded
    And the register does not claim there are no members
    And the register offers a way to try again

  # Was A47 — the TABLE above learned to say it could not reach the server, and
  # the rest of the screen did not. Measured here on 2026-09-16 with the API
  # stopped: beneath "The register could not be reached" the pager said "No
  # members match", the figure strip read Total 0, and the reference-list banner
  # said "The register below is up to date". The register struck A47 on
  # 2026-09-17 (design a95e7b9, on client main since 0d6ef26) and this scenario
  # stayed skipped -- nothing proved the closure. Un-skipped 2026-09-21.
  Scenario: Nothing on a failed register claims to know what it could not load
    Given the API is stopped
    When an administrator navigates to the member register
    Then the register reports that it could not be loaded
    And nothing else on the register claims to know what it could not load

  # Was A34 — no failed state offered a way out of itself; the bank listing
  # reported the failure and left a dead screen. The design client's failed
  # table carries "Try again". CLOSED 2026-09-16.
  Scenario: A failed listing offers a way to try again
    Given the API is stopped
    When an administrator navigates to the bank listing
    Then the listing reports that it could not be loaded
    And the listing offers a way to try again

  # A76 — the first welcome letter the server builds after it starts took 18-25 s
  # in its own image (measured in p17, 2026-09-21), past the client's 20-second
  # write deadline. Since client 8c6b675 a capture IS that letter (A64), so the
  # first capture after a deploy or restart is reported as failed while the
  # server commits the member and mails them. The journeys pay the first letter
  # once before they run (support/fixtures.js), so which scenario happens to go
  # first cannot decide a verdict; this is where the defect itself is asserted,
  # as the application should behave. It lives here because only this project
  # can restart the API, which is what makes the letter cold again.
  @known-broken @defect:A76 @skip
  Scenario: The first member captured after the API restarts is confirmed
    Given the API is stopped
    When the API is started again
    And an administrator captures a member with:
      | field          | value                                    |
      | First Name(s)  | Zola                                     |
      | Surname        | <a name minted for this run>             |
      | Identity Type  | I.D. Number                              |
      | I.D. Number    | <an identity number minted for this run> |
      | Personal Email | <an email address minted for this run>   |
      | Payment Type   | Payroll Deduction                        |
    Then the welcome letter is emailed to their personal address, as a PDF
