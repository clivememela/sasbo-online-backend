Feature: Debit Order Deductions — preparing a month's debit order run
  Once a month the union collects its subscriptions by debit order: Debit Order
  Deductions, the module once called A.C.B. (renamed at the 5 Oct Show & Tell).
  A run is that month's collection written down before anything leaves the
  building: who is debited, on which date, for how much, and what the totals come
  to. Preparing a run IS the dry run — nothing is sent until a run is approved,
  and a prepared run that is wrong is cancelled rather than edited. A run is never
  deleted.

  Every scenario here prepares a real run for a real month, so they run in a
  project of their own (journeys-acb): one at a time, and after the parallel
  journeys have finished, because a member captured by another journey
  mid-preparation would change what the run contains. Each registers a restore
  that cancels the run it prepared, and a cancelled run releases its month for
  the next scenario.

  The date each member is debited on is not asserted here. The expected date
  must come from the debit window's fixture, never from a second copy of the
  rule, and that fixture is not yet one of this suite's contract artifacts
  (ACB T2-A). The rule itself is proven where the fixture lives: in the
  server's and the client's own tests.

  Scenario: Preparing the next month debits its members at the month's rate, and the control total is the sum of its lines
    When an administrator prepares the debit order run for the next month
    Then the run is prepared
    And the run's control total is the sum of its lines
    And every debit of the month is charged the rate in force for it

  # The report is the deliverable, not a nicety: the seed's own members fall out
  # of a run for more than one reason, and an administrator has to see which
  # member and why.
  Scenario: The members a run cannot debit are named, each with the reason
    When an administrator prepares the debit order run for the next month
    Then the run is prepared
    And the run names the members it leaves out, each with a reason

  Scenario: Preparing a month that already has a run does not make a second one
    Given the debit order run for the next month has been prepared
    When an administrator prepares the debit order run for the next month again
    Then they are told that month's run already exists, and what state it is in
    And there is one live run for that month

  # Cancelling is the undo, and a cancelled run stays on record — but it must
  # release its month, or one mistaken preparation would block that month's
  # collection for ever.
  Scenario: A cancelled run releases its month
    Given the debit order run for the next month has been prepared
    When an administrator cancels the run
    And an administrator prepares the debit order run for the next month
    Then the run is prepared
    And the cancelled run is still on record, marked cancelled

  # This replaces the A3 scenario that was in subscriptions-and-acb.feature,
  # which asked GET /api/acb for a list. The runs live under /api/acb/runs, and
  # once every unknown path answers 404 (A18) the bare path is a 404 — correctly —
  # so deleting that scenario's tags would have reddened it, not turned it green.
  Scenario: The runs on record are listed with their state and control total
    Given the debit order run for the next month has been prepared
    When an administrator opens the debit order runs
    Then the runs list shows that month's run, with its state and its control total

  # The interim behaviour D13 promises while the API is anonymous: a route that
  # moves money refuses every caller, and says why.
  Scenario: While nobody can be signed in, approval is refused and says why
    Given the debit order run for the next month has been prepared
    When an anonymous caller asks the API to approve the run
    Then the approval is refused until administrators can sign in
    And the run is still prepared, not approved

  # B1 — the API answers anyone: /api/** is permitAll. Approving a run authorises
  # the union to debit thousands of members, so under D13 approval refuses every
  # caller until a real administrator can be signed in. Written the way it must
  # behave; it turns green by deleting these tags. Whoever prepared a run may
  # approve it (D14 as amended by SASBO, Q29).
  @known-broken @defect:B1 @skip
  Scenario: An approved run's lines can no longer change
    Given the debit order run for the next month has been prepared
    When a signed-in administrator approves the run
    Then the run is approved
    And the run's lines can no longer be changed

  # B1 — as above: a file the bank declines (D29) belongs to a run that was
  # submitted, and nothing can be submitted until an administrator can sign in.
  @known-broken @defect:B1 @skip
  Scenario: A file the bank declines releases its month, and says why
    Given the debit order run for the next month has been submitted to the bank
    When an administrator records that the bank declined the file for a wrong debit date
    Then the run is declined, with the bank's reason
    And the month can be prepared again
