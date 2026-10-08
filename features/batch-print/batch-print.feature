Feature: Batch print — every branch's location listing at once, each bank on a new page
  An organiser with twenty branches to visit in a month used to type one R/A
  number, print, and type the next. Since the 5 Oct Show & Tell they type or
  paste them all at once, and every location listing prints together, grouped
  by bank, each bank starting a new page.

  No workplace is named here: each scenario reads from the server, when it
  runs, the workplaces it prints.

  Scenario: Several R/A numbers print together, grouped by bank, each bank starting a new page
    Given the Organiser is signed in
    And three workplaces in the Organiser's area, two at one bank and one at another
    When the Organiser prints their three R/A numbers in one batch, the two banks' numbers interleaved
    Then the listings are grouped by bank, each bank once, in the order its first number was typed
    And each bank's heading names the Organiser's area and counts its branches
    And each bank after the first starts a new printed page

  Scenario: A number that is no workplace is named, and the others still print
    Given two workplaces on record, and a number that is no workplace
    When the three numbers are printed in one batch
    Then the number that is no workplace is said to be not on record
    And the two workplaces' listings are shown
