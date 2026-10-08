Feature: Month end — where the month's work lives
  Once a month the union takes stock: who joined, who left, how far its
  membership reaches into each employer. The Month end page is where that work
  is found. It lists what is built and nothing else, so a row is never a promise.

  These journeys read and choose; none of them writes. Taking a month freezes
  its figures for good, so it waits until administrators sign in, and the page
  says so before anybody asks.

  Scenario: Month end is reached from the Admin menu
    When an administrator opens the Admin menu
    And they choose Month end
    Then the Month end page is shown

  Scenario: Month end is reached from the navigation
    When an administrator opens the navigation's Admin Menu
    And they choose Month end there
    Then the Month end page is shown
    And the page says where it is in the application

  # The defect this guards was real: every row of the old Monthly Menu was a
  # link to the page it was already on.
  Scenario: Every statistic on the Month end page opens a page of its own
    When an administrator opens the Month end page
    Then every statistic opens a page of its own
    And no statistic opens the page it is on

  Scenario: A statistic says what the old system called it
    When an administrator opens the Month end page
    Then the statistics are shown with the names the old system gave them:
      | statistic                          | the old system's name           |
      | New and deleted members            | Print Membership Summary        |
      | The branches and their penetration | Print Bank Master Comp          |
      | The branches due a visit           | Print Branches requiring visits |

  # A listing names members, so it waits for sign-in -- and the page says so
  # without asking the server for it.
  Scenario: A listing of members says that it waits for sign-in
    When an administrator opens the Month end page
    Then each listing of members says that it waits for sign-in
    And no listing of members can be opened

  Scenario: A month that has been taken says so
    Given a month that has been taken
    When an administrator chooses that month on the Month end page
    Then the page says when that month was taken

  # Since sign-in the server holds the take by a permission (MONTH_END_TAKE) and
  # answers a caller nobody signed in with sign-in's one refusal -- asked here for
  # a month that has not begun, so that even a refusal gone missing could take
  # nothing. The page's sentence is still the one it had.
  Scenario: The open month cannot be taken before administrators sign in
    Given the month that is open
    When an administrator chooses that month on the Month end page
    Then taking that month is offered and cannot be done
    And the page says that taking a month waits for sign-in
    And a take asked for without signing in is refused until someone signs in
