Feature: Where the union stands — the month's figures, on Month end
  Month end opens with where the union stands: how many members it has on
  record, how many of them are suspended, how many are paying, who joined and
  who left in the month, and how far into each employer's staff its membership
  reaches. They are counts, never a member's details. They were the first
  screen's until the 5 Oct Show & Tell, where SASBO looked for them on Month
  end; they follow the month the page has chosen, as its statistics do.

  Other journeys capture members on the same stack and those members cannot be
  removed, so nothing here asserts a total. Each scenario that changes a figure
  reads the open month's figures, does one thing, and reads them again -- and
  puts its member back.

  Scenario: Month end shows the month's figures above its statistics
    When an administrator opens the Month end page
    Then the page shows the month's figures
    And the page shows its statistics

  # Month end opens on the month a person comes to it for: the newest month
  # taken. The figures are that month's, frozen when it was taken.
  Scenario: Month end opens on the newest month taken, and its figures are that month's
    When an administrator opens the Month end page
    Then the figures are those of the newest month taken

  Scenario: Members on record, suspended and paying are shown side by side, each saying what it counted
    When an administrator reads the figures for the month that is open
    Then the figures say what each counted:
      | figure            | what it counted                                       |
      | Paying members    | on record and not suspended                           |
      | Suspended         | by SASBO's suspension codes                           |
      | Members on record | of a status this system knows, and not a leaving one  |

  # The assumption this guards was ours, and wrong: a suspended member was
  # counted as a member like any other. The union's own reports leave them out
  # of the membership and count them beside it.
  Scenario: A suspended member is on record and is not a paying member
    Given a member captured for this scenario
    And an administrator has read the open month's figures for members on record, suspended and paying
    When that member is suspended, with the union's reason
    Then the figure for members on record has not changed
    And the figure for suspended members is one more than it was
    And the figure for paying members is one fewer than it was

  # The browser dates a capture and the server names the month, and on the last
  # evening of a month the two can be a day apart. So both months are read
  # first, and the one that moves is named off the date the member was given --
  # never off a clock.
  Scenario: A member captured is counted as having joined in the month of their join date
    Given an administrator has read the figures for members who joined in the server's month and the month before it
    When a member is captured for this scenario
    Then the figure for members who joined in the month of that member's join date is one more than it was

  Scenario: A member who leaves is counted as having left this month
    Given a member captured for this scenario
    And an administrator has read the open month's figures for members on record and for members who left
    When that member leaves, with the union's reason
    Then the figure for members who left this month is one more than it was
    And the figure for members on record is one fewer than it was

  Scenario: The figures can be grouped the way the union groups them
    When an administrator reads the figures for the month that is open
    Then the figures are shown by employer group
    And they can be grouped by employer, by table code, by area, by region and by committee

  Scenario: Every grouping adds up to the same figures
    When an administrator reads the figures for the month that is open
    Then the sheet by employer group adds up to the paying and suspended members above it
    And so does the sheet by area

  Scenario: Members with no workplace on record are one line, in every grouping
    Given a member captured for this scenario
    When an administrator reads the figures for the month that is open
    Then the members with no workplace on record are one line in every grouping
    And the screen says how many members have no workplace on record

  # Until a month has been taken its figures are worked out when they are
  # asked for, from where each member works today; once taken, they are read
  # from what was frozen, each member where they stood. The screen says which.
  # A past month that was not taken is not offered by the month control; the
  # address carries it, as a statistic's screen carries its month back here.
  Scenario: A past month that was not taken says how its figures are grouped
    Given a past month that has not been taken
    When an administrator reads the figures for that month
    Then the screen says the figures are grouped by where each member works today

  Scenario: A past month that was taken says it was read as taken
    Given a month that has been taken
    When an administrator reads the figures for that month
    Then the screen says its members are grouped where they stood when it was taken

  # The members on a demonstration are invented; the staff complement is the
  # union's own. The screen says so rather than leave the percentage to be doubted.
  Scenario: A demonstration says that its members are invented
    When an administrator reads the figures for the month that is open
    Then the screen says how many of the members are invented, for demonstration

  Scenario: The screen says which of the union's lists grouped the figures
    When an administrator reads the figures for the month that is open
    Then the screen says which of SASBO's lists grouped the figures

  Scenario: The month before the open one can be chosen, and the figures follow it
    When an administrator reads the figures for the month that is open
    And they choose the month before it
    Then the figures are those of that month
