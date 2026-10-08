Feature: Banks and branches
  SASBO's branch register is the reference data the rest of the system leans on:
  a member belongs to a branch, and a branch belongs to a bank. An administrator
  needs to find a branch, read its details, and correct them when they change.

  Background:
    Given the branch register is available

  Scenario: The bank listing shows the banks on record
    When an administrator opens the bank listing
    Then the listing reports every bank on record
    And the listing shows "Absa Bank Limited"

  Scenario: Searching the listing narrows it to one bank
    When an administrator opens the bank listing
    And they search the listing for the bank name "Capitec"
    Then the listing shows "Capitec Bank Limited"
    And the listing no longer shows "Absa Bank Limited"

  Scenario: A branch's details can be read from the branch register
    When an administrator opens the branch register
    And they open the first branch on record
    Then the branch details show its bank name
    And the branch details show its area

  Scenario: A corrected branch manager appears in the register
    When the manager's name on the first branch is corrected
    And an administrator opens the branch register
    Then the register shows the new manager's name

  Scenario: A branch cannot be added without a bank name and a branch
    When an administrator opens the branch register
    And they begin adding a branch but leave the bank name and branch empty
    And they try to save it
    Then they are told the bank name and branch are required
    And no branch has been added

  # Was A29 — this scenario asserted the crash: Edit handed the branch record to
  # the form as it came, the address field called .trim() on an object, and the
  # screen fell to its error page. Fixed in the October build (client oct/bugs
  # 5bc3fc1: the record becomes the form's strings, and a save sends the keys the
  # server reads; e6499ba: pressing Edit no longer saves the form it opens), so
  # it now asserts the edit the fix makes possible. The register strikes A29
  # when this is green.
  Scenario: Editing a branch opens its form, and a corrected manager is kept
    When an administrator opens the branch register
    And they open the first branch on record
    And they choose to edit it
    Then the branch's edit form shows its bank and its branch
    When they correct the branch manager's name on the form and save it
    Then the branch's details show the new manager's name, and so does the register
