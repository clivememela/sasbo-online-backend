Feature: The first screen — every module of the system
  The first screen is where everybody starts. Since the 5 Oct Show & Tell it
  shows the modules of the system and nothing else -- the month's figures moved
  to Month end, and Subscriptions lives under Maintenance alone -- as cards, as
  a list or as a table, whichever a person chooses. The browser remembers the
  choice; a browser that has chosen nothing sees the cards. And it opens light.

  Scenario: The first screen shows every module as cards until another view is chosen
    When an administrator opens the first screen
    Then the modules are shown as cards
    And the first screen shows the modules "Admin Menu", "Members" and "Debit Order Deductions"
    And "Subscriptions" is not one of them
    And the first screen shows no figures

  Scenario: The modules can be shown as a table and as cards again, and the browser remembers the choice
    When an administrator opens the first screen
    And they show the modules as a table
    Then the modules are shown as a table
    And the table says what "Debit Order Deductions" is for, and where it sits in the menu
    When they open the first screen again
    Then the modules are still shown as a table
    When they show the modules as cards
    Then the modules are shown as cards

  Scenario: The first screen opens light, even on a computer that prefers dark
    When an administrator whose computer prefers dark opens the first screen
    Then the first screen is light, and its appearance control says so
