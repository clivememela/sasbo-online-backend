/**
 * What a signed-in user may do -- the same names as the server's `security.Permission` enum.
 *
 * A role is a set of these, kept as data on the server and edited by a System Administrator; this list is only the
 * catalogue the screens ask about. Organisers never see unpaids or payroll discrepancies (Stephne's answers, 6 Oct),
 * which is a fact about roles, not about this file.
 */
export const PERMISSIONS = Object.freeze({
  USERS_ADMIN: 'USERS_ADMIN',
  AUDIT_VIEW: 'AUDIT_VIEW',
  MEMBERS_VIEW: 'MEMBERS_VIEW',
  MEMBERS_CAPTURE: 'MEMBERS_CAPTURE',
  MEMBERS_EDIT: 'MEMBERS_EDIT',
  REFERENCE_MAINTAIN: 'REFERENCE_MAINTAIN',
  SUBSCRIPTIONS_MAINTAIN: 'SUBSCRIPTIONS_MAINTAIN',
  MONTH_END_VIEW: 'MONTH_END_VIEW',
  MONTH_END_TAKE: 'MONTH_END_TAKE',
  MONTH_END_SEND: 'MONTH_END_SEND',
  DOD_PREPARE: 'DOD_PREPARE',
  DOD_FILE: 'DOD_FILE',
  DOD_SETTINGS: 'DOD_SETTINGS',
  PAYROLL_PROCESS: 'PAYROLL_PROCESS',
  PAYROLL_VIEW: 'PAYROLL_VIEW',
  VISITS_CAPTURE: 'VISITS_CAPTURE',
  REPORTS_PRINT: 'REPORTS_PRINT',
  MIGRATION_RUN: 'MIGRATION_RUN',
})

/** What a refused screen says is missing, in words an administrator reads. */
export const PERMISSION_LABELS = Object.freeze({
  USERS_ADMIN: 'manage users',
  AUDIT_VIEW: 'read the record of who did what',
  MEMBERS_VIEW: 'see members',
  MEMBERS_CAPTURE: 'capture members',
  MEMBERS_EDIT: 'edit members',
  REFERENCE_MAINTAIN: 'maintain lists',
  SUBSCRIPTIONS_MAINTAIN: 'change subscription amounts',
  MONTH_END_VIEW: 'read month end',
  MONTH_END_TAKE: 'take the month',
  MONTH_END_SEND: 'send the month\'s packs and files',
  DOD_PREPARE: 'prepare debit order runs',
  DOD_FILE: 'download the debit order file',
  DOD_SETTINGS: 'change debit order settings',
  PAYROLL_PROCESS: 'process payroll files',
  PAYROLL_VIEW: 'read payroll reconciliation',
  VISITS_CAPTURE: 'capture visits',
  REPORTS_PRINT: 'print organisers\' reports',
  MIGRATION_RUN: 'run data migration',
})
