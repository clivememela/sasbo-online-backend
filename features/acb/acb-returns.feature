Feature: Debit Order Deductions' returns — unpaid debits, the union's letters, and presenting them again
  After a run is submitted, the bank sends back the debits it could not collect,
  each with a code. Every unpaid is recorded once, gets the union's own letter for
  its reason, and is presented again in the next run or suspended — the
  administrator's decision on each unpaid (Q7, Q9), the code only suggesting it
  (Q40, Q44). Three unpaid presentations end the membership, by the union's
  constitution; every unpaid counts (Q27).

  🔴 These journeys cannot be driven end to end yet, and the reason is not this
  arc's. An unpaid belongs to a SUBMITTED run; a run is submitted only after it is
  approved; and nothing may approve or submit while the API answers anyone
  (register B1, and ACB's D13). So every scenario here is written the way the loop
  must behave and waits on B1. The rules are proven meanwhile in the server's
  integration lane against runs seeded straight into the tables, and the
  Re-Debits screen's own journey runs today against the seed's returned month
  (subscriptions-and-acb.feature).

  # B1 — no run can reach SUBMITTED while approval and submission wait on
  # authentication. Recording an unpaid is data entry and is not itself gated;
  # it simply has no submitted run of this scenario's own to attach to yet.
  @known-broken @defect:B1 @skip
  Scenario: An unpaid debit is recorded once, with its code
    Given a submitted debit order run debits a member minted for this scenario
    When an administrator records that member's debit as unpaid with the bank's code "4"
    And they record the same unpaid again
    Then the run shows one unpaid for that member, for "Payment Stopped"

  # B1 — as above. The reasons and their three letters are the union's own,
  # transcribed from the letters SASBO printed on 11/09/26.
  @known-broken @defect:B1 @skip
  Scenario Outline: Each unpaid reason gets the union's own letter
    Given a submitted debit order run debits a member minted for this scenario
    When an administrator records that member's debit as unpaid with the bank's code "<code>"
    Then the member's letter for that unpaid is the "<letter>" letter

    Examples:
      | code | letter         |
      | 2    | re-debit       |
      | 4    | return debit   |
      | 12   | account closed |

  # B1 — as above: the decision is the administrator's (Q7, Q9). An unpaid
  # marked present again is offered in the next run; one marked suspend is not.
  @known-broken @defect:B1 @skip
  Scenario: An unpaid marked present again is in the next run beside the member's own debit; one suspended is not
    Given a submitted debit order run has two unpaids, one marked present again and one marked suspend
    When an administrator prepares the debit order run for the following month
    Then the run debits the first member twice: the month's own debit and the unpaid presented again
    And the run does not present the suspended unpaid again

  # B1 — as above. A member's SECOND re-debit in one run goes on the same file only
  # with the member's permission (D32, Q38); without it, it is held over.
  @known-broken @defect:B1 @skip
  Scenario: A second re-debit in one run waits for the member's permission
    Given a member has two unpaids marked present again, from two submitted months
    When an administrator prepares the debit order run for the following month
    Then the run presents the older unpaid and holds the newer one over for the member's permission

  # B1 — as above. Every unpaid counts toward three (Q27); the report terminates
  # nobody -- it says who the rule would terminate.
  @known-broken @defect:B1 @skip
  Scenario: Three unpaid presentations put a member on the termination report
    Given a member's debits were returned unpaid three times, the last three presentations
    When an administrator opens the termination report
    Then the member is listed with the three unpaid months named
    And no member's status has been changed by the report
