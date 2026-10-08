Feature: A member's status, and the record of what changed
  A member who leaves the union leaves for a reason and on a day, and the month
  end counts them by both. So a status is never simply edited: it is changed,
  with the union's code for it, and the change is kept. Everything else that is
  edited on a member's record is kept too, so that the record can say what it
  used to say.

  Every scenario works on a member of its own, captured for it through the
  screen, and retires that member when it ends.

  Background:
    Given a member captured for this scenario

  # The union's own records know a member who has left by the union's code for
  # why, and a suspended member by the union's code for why -- the union's
  # numbers, not ours.
  Scenario: A member who resigns carries the union's code for it, and the change is kept
    When an administrator changes the member's status to Resigned, with the union's code 2
    Then the member's status is Resigned
    And the member's history shows the change to Resigned, with the union's code 2 and its name

  Scenario: A member who is suspended carries the union's code for it
    When an administrator changes the member's status to Suspended, with the union's code 11
    Then the member's status is Suspended
    And the member's history shows the change to Suspended, with the union's code 11 and its name

  Scenario: A member cannot leave without the union's code for it
    When an administrator chooses Resigned as the member's new status, and no code
    Then they are asked for the union's code, and the change cannot be made
    And the member's status has not changed

  Scenario: A member cannot be suspended without the union's code for it
    When an administrator chooses Suspended as the member's new status, and no code
    Then they are asked for the union's code, and the change cannot be made
    And the member's status has not changed

  # Which of the union's codes fits which way of leaving is the union's to say;
  # until it has, a reason is judged by its kind. The screen never offers a code
  # of the wrong kind, so this is asked as an anonymous caller would ask it.
  Scenario: A reason that does not fit the status is refused
    When a change to Suspended is asked for with the union's code for leaving
    Then it is refused: "That reason does not fit Suspended."
    And the member's status has not changed

  # Two administrators, one member. The second must learn what the first did
  # rather than quietly undo it.
  Scenario: A status someone else has just changed is not changed again unseen
    Given an administrator has the member's record open
    And another administrator has suspended the member since
    When the first administrator changes the member's status to Resigned, with the union's code 2
    Then they are told when the member was moved to Suspended, and that nothing was changed
    And the member's status is Suspended

  Scenario: A member who returns keeps the record of having left
    Given the member has resigned
    When an administrator changes the member's status to Active
    Then the member's status is Active
    And the member's history shows the change to Resigned, with the union's code 2 and its name
    And the member's history shows the return to Active

  Scenario: Editing a member's record does not change their status
    When an administrator edits the member's record
    Then the status is shown and cannot be edited there, and the record says the way to change it

  Scenario: A member nobody has edited shows that they were captured, and nothing else
    When an administrator opens the member's history
    Then it shows only that the member was captured
