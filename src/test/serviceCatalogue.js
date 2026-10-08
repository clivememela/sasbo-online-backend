import { acbRunService } from '../services/acbRunService'
import { adminService } from '../services/adminService'
import { auditService } from '../services/auditService'
import { authService } from '../services/authService'
import { bankService } from '../services/bankService'
import { debounceService } from '../services/debounceService'
import { jobGradeService } from '../services/jobGradeService'
import { locationService } from '../services/locationService'
import { memberService } from '../services/memberService'
import { memberHistoryService } from '../services/memberHistoryService'
import { memberStatusService } from '../services/memberStatusService'
import { monthEndService } from '../services/monthEndService'
import { recruiterService } from '../services/recruiterService'
import { recruitmentChannelService } from '../services/recruitmentChannelService'
import { referenceService } from '../services/referenceService'
import { signInService } from '../services/signInService'
import { subscriptionsService } from '../services/subscriptionsService'
import { summaryService } from '../services/summaryService'
import { workplaceService } from '../services/workplaceService'
import { workplaceVisitService } from '../services/workplaceVisitService'
import * as api from '../services/api'

/**
 * Every service module, and every method on it, DISCOVERED rather than listed.
 *
 * WHY DISCOVERY
 * The error contract is applied by iterating this, not by naming the four
 * services that are wrong today. A tenth service -- or a tenth method on an
 * existing one -- is covered the day it is written, and nobody has to remember
 * to add it to a list. The guard has no list of its own to forget.
 *
 * `api.js` is here as a module but exports no methods that fetch: `apiUrl` is
 * a pure string function and is tested adversarially in its own file.
 */
export const SERVICE_MODULES = [
  { name: 'acbRunService', module: acbRunService },
  { name: 'adminService', module: adminService },
  { name: 'auditService', module: auditService },
  { name: 'authService', module: authService },
  { name: 'bankService', module: bankService },
  { name: 'debounceService', module: debounceService },
  { name: 'jobGradeService', module: jobGradeService },
  { name: 'locationService', module: locationService },
  { name: 'memberService', module: memberService },
  { name: 'memberHistoryService', module: memberHistoryService },
  { name: 'memberStatusService', module: memberStatusService },
  { name: 'monthEndService', module: monthEndService },
  { name: 'recruiterService', module: recruiterService },
  { name: 'recruitmentChannelService', module: recruitmentChannelService },
  { name: 'referenceService', module: referenceService },
  { name: 'signInService', module: signInService },
  { name: 'subscriptionsService', module: subscriptionsService },
  { name: 'summaryService', module: summaryService },
  { name: 'workplaceService', module: workplaceService },
  { name: 'workplaceVisitService', module: workplaceVisitService },
]

/**
 * The one module that exports no method that performs a request. Since sign-in it also installs the `fetch` wrapper
 * that carries the session's token (`installSessionFetch`) -- which changes requests, and makes none of its own.
 */
export const PURE_MODULES = [{ name: 'api', module: api }]

/**
 * Arguments to call each method with.
 *
 * These are SYNTHETIC and contain no real member data -- the ID and account
 * numbers here are constructed, and coverage reports and failure output are
 * artefacts that get forwarded.
 *
 * A method with no entry gets the default vector, which is deliberately
 * generous: an id and a small object covers `(id)`, `(data)` and `(id, data)`.
 * Getting the arity wrong is harmless here because the contract asserts what
 * the method does with a RESPONSE, not what it does with its arguments.
 */
const DEFAULT_ARGS = ['7', { name: 'Synthetic', id: '7' }]

const ARGS = {
  'memberService.updateMember': [{ id: '7', surname: 'Synthetic' }],
  'memberService.updateBanking': ['7', { bankAccountNumber: '0000000000' }],
  'memberService.createMember': [{ surname: 'Synthetic', identityNumber: '0000000000000' }],
  'memberService.searchRegister': [{ q: 'Synthetic', page: 0, size: 50, sort: 'surname,asc' }],
  'memberService.getRegisterOverview': [],
  'debounceService.useDebounce': ['search', 200],
  'jobGradeService.getByBank': ['ABSA'],
  'locationService.search': [{ bankName: 'Synthetic Bank' }],
  'locationService.getDistricts': ['1'],
  'locationService.getAreas': ['1'],
  'bankService.createBank': [{ bankName: 'Synthetic Bank', branchCode: '000000' }],
  'bankService.updateBank': ['7', { bankName: 'Synthetic Bank' }],
  'acbRunService.prepare': ['2026-10'],
  'acbRunService.cancel': ['7', 3, 'Synthetic reason'],
  'acbRunService.holidayCalendar': [2026],
  'acbRunService.rates': [],
  'acbRunService.unpaids': ['7', null, 0, 50],
  'acbRunService.decide': ['7', '3', 'RE_PRESENT'],
  'acbRunService.heldOver': ['7', 0, 50],
  'acbRunService.terminationReport': [0, 50],
  'acbRunService.permit': ['7', '3', 'Synthetic permission note'],
  'acbRunService.heldLetters': ['7', 0, 50],
  'acbRunService.markHandled': ['7', '21', 'Synthetic handled note'],
  'acbRunService.sendLetter': ['7', '21'],
  'acbRunService.searchMembers': [{ surname: 'Synthetic' }, 0, 25],
  'memberStatusService.changeStatus': ['7', {
    toStatus: 'Resigned', expectedStatus: 'Active', reason: 'DELETION_CODE', deletionCode: 2,
  }],
  'summaryService.getSummary': [{ month: '2026-09', by: 'employer' }],
  'monthEndService.getMonths': [],
  'monthEndService.getNewAndDeleted': ['2026-08', { area: 'A' }],
  'monthEndService.getMembership': ['2026-08', {}],
  'monthEndService.getLeavers': ['2026-08', { region: 'NORTH' }],
  'monthEndService.getBranches': ['2026-08'],
  'monthEndService.getRecruitment': ['2026-09', {}],
  'monthEndService.getRepresentatives': ['2026-08', {}],
  'monthEndService.getVisits': ['2026-08', { area: 'B' }],
  'monthEndService.askPack': ['2026-08'],
  'monthEndService.getPack': [7],
  'memberHistoryService.getMemberHistory': ['7', 0, 50],
  'workplaceVisitService.getVisits': ['7', 0, 20],
  'workplaceVisitService.recordVisit': ['7', { visitedOn: '2026-09-10', complementFound: 40 }],
  // Placeholders the secret scan clears by shape (`committedSecrets.js`) -- never a credential-looking literal.
  'signInService.signIn': [{ username: 'synthetic.user', password: 'your_password_here' }],
  'signInService.changePassword': [{ currentPassword: 'your_password_here', newPassword: 'your_new_password' }],
  'adminService.getUsers': [{ page: 0, size: 50, q: 'Synthetic' }],
  'adminService.createUser': [{ username: 'synthetic.user', displayName: 'Synthetic User', positionCode: 'ORGANISER', scope: { type: 'AREA', codes: ['A'] } }],
  'adminService.updateUser': ['7', { displayName: 'Synthetic User', positionCode: 'ORGANISER', active: true, version: 3 }],
  'adminService.updatePosition': ['ORGANISER', { code: 'ORGANISER', name: 'Organiser', defaultRoleCode: 'ORGANISER', active: true, ordinal: 6 }],
  'adminService.updateRole': ['ORGANISER', { code: 'ORGANISER', name: 'Organiser', permissions: ['MEMBERS_VIEW'] }],
  'auditService.getAudit': [{ username: 'synthetic.user', from: '2026-10-01', to: '2026-10-06', page: 0, size: 50 }],
  'subscriptionsService.create': [{ bankName: 'Synthetic Bank Limited', acbDebitDay: 25, subscriptionAmount: 174 }],
  'recruitmentChannelService.summary': [],
  'recruitmentChannelService.members': ['7', { page: 0, size: 20, name: 'Synthetic' }],
  'recruitmentChannelService.candidates': [{ q: 'Synthetic', excludeChannel: 7 }],
  'recruitmentChannelService.addMember': ['7', '41'],
  'recruitmentChannelService.removeMember': ['7', '41'],
  'workplaceVisitService.getMyDrafts': ['DRAFT', 0, 20],
  'workplaceVisitService.getDraft': ['7'],
  'workplaceVisitService.createDraft': [{ raNumber: 900001, visitedOn: '2026-09-10' }],
  'workplaceVisitService.updateDraft': ['7', { raNumber: 900001, visitedOn: '2026-09-10', version: 0 }],
  'workplaceVisitService.submitDraft': ['7', 0],
  'locationService.getListings': [['900001', '900002']],
  'locationService.getMembersOf': [['7', '8']],
  'workplaceVisitService.discardDraft': ['7', 0],
}

export const argsFor = (qualifiedName) => ARGS[qualifiedName] ?? DEFAULT_ARGS

/**
 * Every exported function across every service module, flattened.
 * Shape: { service, method, qualified, fn, args }
 */
export function allServiceMethods() {
  return SERVICE_MODULES.flatMap(({ name, module }) =>
    Object.keys(module)
      .filter((key) => typeof module[key] === 'function')
      .map((method) => {
        const qualified = `${name}.${method}`
        return {
          service: name,
          method,
          qualified,
          fn: module[method].bind(module),
          args: argsFor(qualified),
        }
      }),
  )
}

/**
 * Methods that perform no request at all, and so are outside the error
 * contract. Each is asserted to be genuinely request-free rather than simply
 * declared so -- otherwise this list becomes a place to hide a method that is
 * failing the contract.
 */
export const NON_FETCHING = new Set(['bankService.getPageSize'])
