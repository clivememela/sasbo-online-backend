export const FREQUENCY_OPTIONS = ['Monthly', 'Quarterly', 'Bi-annually', 'Annually'];
export const TITLE_OPTIONS = ['Mr', 'Ms', 'Mrs', 'Dr', 'Prof'];
export const RACE_OPTIONS = ['African', 'White', 'Coloured', 'Indian', 'Other'];
/**
 * 🔴 A6: the browser offered "Payroll" while the server's `PaymentType` declares
 * `PAYROLL("Payroll Deduction")`, so every rule written against the server's spelling compared
 * against a string no option could produce. The names come from `src/test/api-vocabulary.json`,
 * which the server generates from the enum itself -- `vocabulary.parity.test.js` asserts that
 * every option below is one the server accepts.
 *
 * `PAYMENT_TYPE_PAYROLL_LEGACY` is the older spelling, and it is not an option: it is what is
 * already STORED against members captured before the rename. `MemberDto.paymentType` is a String
 * passed straight through from storage, so it really does reach the browser, and
 * `normalizePaymentType` in `Members.jsx` folds it onto the current name. Without that fold every
 * legacy member would read as edited the moment their record was opened.
 */
export const PAYMENT_TYPE_DEBIT_ORDER = 'Debit Order';
export const PAYMENT_TYPE_PAYROLL = 'Payroll Deduction';
export const PAYMENT_TYPE_PAYROLL_LEGACY = 'Payroll';
export const PAYMENT_TYPE_OPTIONS = [PAYMENT_TYPE_DEBIT_ORDER, PAYMENT_TYPE_PAYROLL];
/**
 * ACB 00.5: the type of the account a debit order draws on -- the bureau file prints its digit on every debit (Q16).
 * The names are the server's `AccountType`, published as `accountTypes` in `src/test/api-vocabulary.json`, and
 * `vocabulary.parity.test.js` holds this list to that one. The member record stores exactly these words.
 */
export const ACCOUNT_TYPE_OPTIONS = ['Cheque', 'Savings', 'Transmission'];
export const STATUS_OPTIONS = ['Active', 'Pending', 'Suspended', 'Resigned', 'Retired', 'Terminated'];
/**
 * 🔴 A7: the passport rule compared against "Passport" while the only option is "Passport Number", so
 * "Date of Birth is required for a passport" never once ran, on either screen. Named once, here, and
 * compared by name -- a second spelling of a vocabulary value is how the rule went dead.
 */
export const IDENTITY_TYPE_ID = 'I.D. Number';
export const IDENTITY_TYPE_PASSPORT = 'Passport Number';
export const IDENTITY_TYPE_OPTIONS = [IDENTITY_TYPE_ID, IDENTITY_TYPE_PASSPORT];
export const GENDER_OPTIONS = ['Male', 'Female'];
export const YES_NO_OPTIONS = ['Y', 'N'];
export const SUSPENSION_CODE_OPTIONS = ['', 'NON-PAY', 'RESIGN'];
export const EMPLOYMENT_TYPE_OPTIONS = ['Permanent', 'Contract'];
// Job grades are now provided by the backend per-bank. Keep an empty default here so
// the front-end does not hard-code values. Components will fetch populated values
// from the API at runtime and fall back to this array only when necessary.
export const JOB_GRADE_OPTIONS = [];
export const MANAGER_OPTIONS = ['MGR-001'];
// ⚖️ `@__PURE__` (month end 01.3): no screen reads these three, and a bundler must keep an `Array.from(…, fn)` it cannot
// prove harmless -- so all three shipped, computed, on every load. Marked, they go wherever nothing imports them.
export const DAY_OPTIONS = /* @__PURE__ */ Array.from({ length: 31 }, (_, i) => (i + 1).toString().padStart(2, '0'));
export const MONTH_OPTIONS = /* @__PURE__ */ Array.from({ length: 12 }, (_, i) => (i + 1).toString().padStart(2, '0'));
export const YEAR_OPTIONS = /* @__PURE__ */ Array.from({ length: 21 }, (_, i) => (new Date().getFullYear() - 10 + i).toString());

export const BEC_STRUCTURE_OPTIONS = ['Shop Stewart', 'Branch Executive Committee'];

/*
 * 🗣️ SASBO'S STRUCTURES TREE, 2026-09-30 -- what the record's Structures tab asks under BEC Member = Yes and
 * COSATU Member = Yes. Each list is the server's (`BecStructure`, `BecExecutivePosition`, `CosatuPosition`),
 * published in `src/test/api-vocabulary.json`; `vocabulary.parity.test.js` holds these to it, in order, because
 * the server refuses anything else and writes the rest back in this spelling.
 *
 * The tree lists Locals and Region among COSATU's too: those are structures a member SITS ON, asked yes/no
 * (`isCosatuLocal` / `isCosatuRegional`, the stakeholder, 2026-09-17), not positions -- so not in this list.
 */
export const BEC_STRUCTURES_REPRESENTED = ['INC / IC', 'NCF / SJC', 'VP', 'President', 'MANCOM', 'Resolutions Committee'];
export const BEC_EXECUTIVE_POSITIONS = ['Chairperson', 'Deputy Chairperson', 'Secretary', 'Deputy Secretary', 'Treasurer'];
export const COSATU_POSITIONS = ['PEC', 'CEC', 'Chairperson', 'Secretary', 'Treasurer'];
/** COSATU's two executive committees, spelt out where they are offered -- as the record offered them before 2026-09-17. */
export const COSATU_POSITION_LABELS = {
  PEC: 'PEC (Provincial Executive Committee)',
  CEC: 'CEC (Central Executive Committee)',
};

export const BRANCH_OPTIONS = [
  'Cape Town', 'Durban', 'Head Office', 'Johannesburg', 'Johannesburg Branch', 'Midrand', 'Pretoria',
  'Representative Office', 'Rivonia', 'Rosebank', 'Sandton', 'South Africa Branch', 'Soweto', 'Stellenbosch',
];

export const REP_OPTIONS = [
  'Self', 'System', 'Achmat Adams', 'Alvaro Legoinha', 'Amanda Hlatshwayo', 'Amol Prabhu', 'Annerie Visser',
  'Ashley Cameron', 'Ashutosh Kumar', 'Benjamin Davis', 'Bob Jones', 'Chaoyang Zhang', 'Charles van Staden',
  'Cheslyn Jacobs', 'Cumesh Moodliar', 'Dale Wood', 'David Hodnett', 'David Kasinamunda', 'Dean Radbourne',
  'Graham Lee', 'Hannah Sadiki', 'Hanri Maritz', 'Hylton Kallner', 'J Wang', 'John Doe', 'Jonathan Fincham',
  'Karen Black', 'Kathryn Quayle', 'Kennedy Bungane', 'Kenny Fihla', 'Li Li', 'Lungisa Fuzile', 'M Luo',
  'Mark Selcraig', 'Mark Sherratt', 'Marna Sabrina Pfranger', 'Mary Vilakazi', 'Mauro Rogerio', 'Michael Shirley',
  'Micheal Sassoon', 'Nathalie Villette Py', 'Nthabeleng Likotsi', 'Paul Sayers', 'Peter Liu', 'Peter Taylor',
  'Phibeon Mutibura', 'Ramiro Sebastiao', 'Raphael Marlio-Marette', 'Saloshni Pillay', 'Sandile Shabalala',
  'Shabir Chohan', 'Shugo Suzuki', 'Shyamasish Acharya', 'Sidhambaram Perumal Pillay', 'Simon Denny',
  'Stefano Rausch', 'Thorsten Metz', 'Tinus Myburgh', 'Tsakani Clarence Nethengwe', 'Veronica Padi',
  'Viviana van Agtmaal', 'Yatin Narsai', 'Yongzhi Ban', 'Yvonne Holenstein',
];

/*
 * THE CAPTURE FORM'S TABS -- and only the capture form's.
 *
 * 🔑 THIS IS A CLOSED LIST WITH TWO THINGS HOLDING IT. `memberConstants.test.js` pins these
 * labels exactly, and the automation suite's capture catalogue drives each tab BY LABEL
 * (`pages/MemberCapturePage.js`), so renaming one here without moving that suite breaks its
 * scenarios. That is why these stay the long phrases: this is a wide form and they fit.
 *
 * 🔴 THE RECORD PANEL KEEPS ITS OWN LIST, AND FOR A PRODUCT REASON. The design branch had
 * briefly made `Members.jsx` read this constant too, on the argument that two lists could
 * drift. Main then removed "Sasbo Structures" from CAPTURE (client 4a7a67b) while keeping it
 * on the member's RECORD -- so the two lists are meant to differ, and one shared list would
 * have taken the Structures tab off every member's record. See `RECORD_TABS` in
 * `Members.jsx`.
 *
 * 🗣️ RECRUITMENT CHANNEL COMES BEFORE MEMBERSHIP & SUBSCRIPTIONS -- SASBO, 18 Sep 2026, asked twice
 * in the meeting and written into their notes ("Recruitment Channel -> membership & subs needs to
 * change"): who recruited the member is captured straight after where they work, and the
 * subscription, which the capturer cannot change anyway, comes last. An ORDER change only: no label
 * moved, so the suite (which opens each tab by name) is untouched. The record's own strip is NOT
 * reordered -- its arrangement is the owner's, settled over three rounds on 2026-09-10.
 */
export const MEMBER_TABS = [
  { id: 'personal', label: 'Personal Information' },
  { id: 'employment', label: 'Employment & Bank' },
  { id: 'representatives', label: 'Recruitment Channel' },
  { id: 'membership', label: 'Membership & Subscriptions' },
  // { id: 'history', label: 'History & Remarks' },
];

/**
 * THE TAB EACH FIELD IS ON, for both member screens: where a save that failed on a field sends the administrator, and
 * which tab is marked as holding an error. Capture and the record each carried an identical copy until month end 01.3,
 * which needed `workplaceId` in both -- one list now, so the two cannot disagree about where a field lives.
 */
export const MEMBER_FIELD_TABS = {
  title: 'personal',
  initials: 'personal',
  firstNames: 'personal',
  surname: 'personal',
  identityType: 'personal',
  identityNumber: 'personal',
  dateOfBirth: 'personal',
  personalEmail: 'personal',
  workEmail: 'personal',
  phone: 'personal',
  workTel: 'personal',
  homeTel: 'personal',
  employeeNumber: 'employment',
  ibtNo: 'employment',
  employmentType: 'employment',
  jobGrade: 'employment',
  bankName: 'employment',
  branch: 'employment',
  paymentType: 'employment',
  bankAccountNumber: 'employment',
  accountType: 'employment',
  debitDay: 'employment',
  workplaceId: 'employment',
  // V55: what a structure's Yes needs beside it -- a refused save opens the tab that asks. The record's
  // only: capture asks no structure (its Structures tab came off in 2026-09).
  shopStewardElectionDate: 'structures',
  becCommitteeCode: 'structures',
  becStructures: 'structures',
  becExecutivePosition: 'structures',
};

/**
 * 🔴 E9a/E9b/A32: the form offered three days -- 20, 25 and 31 -- while the signed spec and the
 * server both take the whole 20th-to-31st range (`DebitDay.EARLIEST` = 20, `DebitDay.LATEST` = 31,
 * and `isValid` accepts everything between). Thirteen of the fifteen banks' own ACB days fall
 * outside the three, so most debit-order members held a day the form could not re-offer: opening
 * their record and saving anything at all meant silently moving their debit day.
 *
 * Derived from the two bounds rather than typed out, so the range has one shape and not twelve.
 */
export const DEBIT_DAY_EARLIEST = 20;
export const DEBIT_DAY_LATEST = 31;
export const DEBIT_DAY_OPTIONS = Array.from(
  { length: DEBIT_DAY_LATEST - DEBIT_DAY_EARLIEST + 1 },
  (_, i) => String(DEBIT_DAY_EARLIEST + i),
);
export const isDebitDayInRange = (day) => {
  const n = Number(String(day ?? '').trim());
  return Number.isInteger(n) && n >= DEBIT_DAY_EARLIEST && n <= DEBIT_DAY_LATEST;
};

export const MEMBER_FIELD_LABEL_MAP = {
  title: 'Title',
  initials: 'Initials',
  firstNames: 'First Names',
  surname: 'Surname',
  identityNumber: 'I.D. Number',
  dateOfBirth: 'Date of Birth',
  gender: 'Gender',
  race: 'Race',
  email: 'Email Address',
  phone: 'Cell Tel',
  cellTel: 'Cell Tel',
  workTel: 'Work Tel',
  homeTel: 'Home Tel',
  address: 'Address',
  joinDate: 'Date Joined',
  status: 'Status',
  monthlySubscription: 'Monthly Sasbo Subscription',
  subscriptionFrequency: 'Frequency of Sasbo Subscription',
  endowmentSubs: 'Endowment Fund Subs',
  endowmentFrequency: 'Frequency of Endowment Fund',
  endowmentPolicyNo: 'Endowment Fund Policy Number',
  lastMonthBalance: 'Last Months Balance',
  currentBalance: 'Current Balance',
  arrearsBalance: 'Arrears Balance',
  dateLastChanged: 'Date Last Changed',
  employeeNumber: 'Employee Number',
  jobGrade: 'Job Grade',
  bankName: 'Bank Where Member Is Employed',
  bankAccountNumber: 'Bank Account Number',
  accountType: 'Account Type',
  branch: 'Bank Branch Where Member Works',
  ibtNo: 'IBT No.',
  paymentType: 'Payment Type',
  debitDay: 'Day to be debited',
  bankAddress: 'Bank Address',
  workBankAddress: 'Work Bank Address',
  recruitmentRep: 'Recruitment Rep',
  areasRep: "Area's Rep",
  recruitmentChannel: 'Recruitment Channel',
  repElectionDay: 'Sasbo Rep Election Day',
  repElectionMonth: 'Sasbo Rep Election Month',
  repElectionYear: 'Sasbo Rep Election Year',
  electedRep: 'Elected Sasbo Recruiter (Y/N)',
  remarks: 'Remarks / Comments',
  remarks2: '2nd Remarks Field',
  remarks3: '3rd Remarks Field',
  remarks4: '4th Remarks Field',
  memnetMember: 'MEMNET Member (Y/N)',
  printLabelForPostage: 'Print Label for Postage (Y/N)',
  suspensionCode: 'Suspension Code',
  useAltAddress: 'Use Alternate Address (Y/N)',
  // "Debit Order", not "ACB" (Stephne, DOD). The key is the server's field and keeps its name.
  deductACB: 'Deduct Subs through Debit Order (Y/N)',
  printMembershipCards: 'Print Membership Cards (Y/N)',
  homeLoanReducedRateOption: 'Home Loan Reduced Interest Rate Option (Y/N)',
  isRepresentative: 'Is Representative',
  roleId: 'Representative Role',
  structureId: 'Union Structure / Position',
  electionDate: 'Election Date',
};
