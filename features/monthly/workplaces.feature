Feature: Where a member works — the workplaces, and a member's place in one
  The union counts its members by where they work: the branch or department,
  the employer it belongs to, the area it lies in, the committee it falls
  under. So every member is tied to a workplace, and everything the union
  counts is counted through it.

  The workplaces are the union's own, and no scenario here names one: each
  reads a workplace from the server when it runs, and looks for it.

  Scenario: A workplace is found by its name
    Given a workplace on record
    When an administrator searches the workplaces for that workplace's name
    Then the workplace is shown with its R/A number, its employer, its area and its committee

  Scenario: A workplace is found by its R/A number
    Given a workplace on record
    When an administrator searches the workplaces for that workplace's R/A number
    Then that workplace is the only one shown

  # Several thousand workplaces are never loaded at once.
  Scenario: The workplaces are shown a page at a time
    When an administrator opens the workplaces
    Then one page of workplaces is shown, the number on record is said, and the pages count them all

  Scenario: The workplaces say which of the union's lists they were read from
    When an administrator opens the workplaces
    Then the screen names the union's lists the workplaces were read from

  Scenario: A member captured without a workplace says so on their record
    Given a member captured for this scenario
    When an administrator opens the member's record at its employment
    Then the record says that no workplace is on record
