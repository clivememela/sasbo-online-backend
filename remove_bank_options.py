#!/usr/bin/env python3
"""Remove hard-coded BANK_OPTIONS from the frontend and source bank names from GET /api/banks."""
import io, sys

FE = "/Users/clivememela/WebstormProjects/sasbo-online/src"

def apply(path, replacements):
    with io.open(path, "r", encoding="utf-8") as f:
        content = f.read()
    for old, new in replacements:
        count = content.count(old)
        if count != 1:
            raise SystemExit(f"[FAIL] {path}: expected 1 occurrence, found {count} for:\n-----\n{old}\n-----")
        content = content.replace(old, new)
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"[OK] {path}: {len(replacements)} replacement(s) applied")

# ---------------------------------------------------------------------------
# 1. bankService.js — add getBankNames() sourcing from the banks table (GET /api/banks)
# ---------------------------------------------------------------------------
apply(f"{FE}/services/bankService.js", [
    (
        "      }),\n\n  getBanks: () =>",
        "      }),\n\n"
        "  getBankNames: () =>\n"
        "    fetch(apiUrl('/api/banks'), { credentials: 'include' })\n"
        "      .then(r => {\n"
        "        if (!r.ok) {\n"
        "          throw new Error(`HTTP ${r.status}: ${r.statusText}`);\n"
        "        }\n"
        "        return r.json();\n"
        "      })\n"
        "      .then(payload => {\n"
        "        const banks = normalizeListPayload(payload);\n"
        "        return banks\n"
        "          .map(bank => bank.name ?? bank.bankName ?? '')\n"
        "          .filter(Boolean)\n"
        "          .sort((a, b) => a.localeCompare(b));\n"
        "      })\n"
        "      .catch(err => {\n"
        "        console.error('Failed to fetch bank names:', err);\n"
        "        return [];\n"
        "      }),\n\n"
        "  getBanks: () =>",
    ),
])

# ---------------------------------------------------------------------------
# 2. Subscriptions.jsx
# ---------------------------------------------------------------------------
apply(f"{FE}/pages/maintainance/Subscriptions.jsx", [
    (
        "import { subscriptionsService } from '../../services/subscriptionsService';\n"
        "import { useState, useEffect, useMemo } from 'react';\n"
        "import { BANK_OPTIONS } from '../../config/memberConstants.js';\n"
        "import { useLocation } from 'react-router-dom';",
        "import { subscriptionsService } from '../../services/subscriptionsService';\n"
        "import { bankService } from '../../services/bankService.js';\n"
        "import { useState, useEffect, useMemo } from 'react';\n"
        "import { useLocation } from 'react-router-dom';",
    ),
    (
        "    const [editingId, setEditingId] = useState(null);\n"
        "    const [editData, setEditData] = useState({});",
        "    const [editingId, setEditingId] = useState(null);\n"
        "    const [editData, setEditData] = useState({});\n"
        "    const [bankNames, setBankNames] = useState([]);",
    ),
    (
        "    useEffect(() => {\n"
        "        setLoading(true);\n"
        "        subscriptionsService.getAll()\n"
        "            .then(data => setAllSubscriptions(data))\n"
        "            .catch(err => setError(String(err)))\n"
        "            .finally(() => setLoading(false));\n"
        "    }, []);",
        "    useEffect(() => {\n"
        "        setLoading(true);\n"
        "        subscriptionsService.getAll()\n"
        "            .then(data => setAllSubscriptions(data))\n"
        "            .catch(err => setError(String(err)))\n"
        "            .finally(() => setLoading(false));\n"
        "    }, []);\n\n"
        "    useEffect(() => {\n"
        "        bankService.getBankNames()\n"
        "            .then(setBankNames)\n"
        "            .catch(() => setBankNames([]));\n"
        "    }, []);",
    ),
    (
        "options={[{ value: '', label: 'All Banks' }, ...BANK_OPTIONS.map(b => ({ value: b, label: b }))]}",
        "options={[{ value: '', label: 'All Banks' }, ...bankNames.map(b => ({ value: b, label: b }))]}",
    ),
])

# ---------------------------------------------------------------------------
# 3. CaptureMember.jsx
# ---------------------------------------------------------------------------
apply(f"{FE}/pages/members/CaptureMember.jsx", [
    (
        "    SUSPENSION_CODE_OPTIONS, JOB_GRADE_OPTIONS, MEMBER_TABS,\n"
        "    BANK_OPTIONS, BRANCH_OPTIONS, EMPLOYMENT_TYPE_OPTIONS, DEBIT_DAY_OPTIONS\n"
        "} from \"../../config/memberConstants.js\";",
        "    SUSPENSION_CODE_OPTIONS, JOB_GRADE_OPTIONS, MEMBER_TABS,\n"
        "    BRANCH_OPTIONS, EMPLOYMENT_TYPE_OPTIONS, DEBIT_DAY_OPTIONS\n"
        "} from \"../../config/memberConstants.js\";",
    ),
    (
        "    const [bankListing, setBankListing] = useState([]);\n\n"
        "    // Load bank listing on mount so we can resolve a selected bank name to a bank code\n"
        "    useEffect(() => {\n"
        "        let mounted = true;\n"
        "        bankService.getBankListing()\n"
        "            .then(list => { if (mounted) setBankListing(list || []); })\n"
        "            .catch(() => { if (mounted) setBankListing([]); });\n"
        "        return () => { mounted = false; };\n"
        "    }, []);",
        "    const [bankListing, setBankListing] = useState([]);\n"
        "    const [bankNames, setBankNames] = useState([]);\n\n"
        "    // Load bank listing and bank names on mount so we can resolve a selected bank name to a bank code\n"
        "    useEffect(() => {\n"
        "        let mounted = true;\n"
        "        bankService.getBankListing()\n"
        "            .then(list => { if (mounted) setBankListing(list || []); })\n"
        "            .catch(() => { if (mounted) setBankListing([]); });\n"
        "        bankService.getBankNames()\n"
        "            .then(names => { if (mounted) setBankNames(names || []); })\n"
        "            .catch(() => { if (mounted) setBankNames([]); });\n"
        "        return () => { mounted = false; };\n"
        "    }, []);",
    ),
    (
        "options={BANK_OPTIONS} error={fieldErrors.bankName}",
        "options={bankNames} error={fieldErrors.bankName}",
    ),
])

# ---------------------------------------------------------------------------
# 4. Members.jsx
# ---------------------------------------------------------------------------
apply(f"{FE}/pages/members/Members.jsx", [
    (
        "import { jobGradeService } from \"../../services/jobGradeService.js\";\n"
        "import { TextInput, SearchableSelect, Button } from \"../../components/form\";",
        "import { jobGradeService } from \"../../services/jobGradeService.js\";\n"
        "import { bankService } from \"../../services/bankService.js\";\n"
        "import { TextInput, SearchableSelect, Button } from \"../../components/form\";",
    ),
    (
        "    JOB_GRADE_OPTIONS, DAY_OPTIONS, MONTH_OPTIONS, YEAR_OPTIONS,\n"
        "    BANK_OPTIONS, BRANCH_OPTIONS, REP_OPTIONS, MEMBER_FIELD_LABEL_MAP, EMPLOYMENT_TYPE_OPTIONS,\n"
        "    DEBIT_DAY_OPTIONS,",
        "    JOB_GRADE_OPTIONS, DAY_OPTIONS, MONTH_OPTIONS, YEAR_OPTIONS,\n"
        "    BRANCH_OPTIONS, REP_OPTIONS, MEMBER_FIELD_LABEL_MAP, EMPLOYMENT_TYPE_OPTIONS,\n"
        "    DEBIT_DAY_OPTIONS,",
    ),
    (
        "    const [roles, setRoles] = useState([]);\n"
        "    const [membersList, setMembersList] = useState([]);",
        "    const [roles, setRoles] = useState([]);\n"
        "    const [membersList, setMembersList] = useState([]);\n"
        "    const [bankNames, setBankNames] = useState([]);",
    ),
    (
        "        memberService.getMembers().then(res => {\n"
        "            const list = Array.isArray(res) ? res : (res.data || res.members || []);\n"
        "            setMembersList(list);\n"
        "        }).catch(err => console.error(\"Failed fetching records array\", err));\n"
        "    }, []);",
        "        memberService.getMembers().then(res => {\n"
        "            const list = Array.isArray(res) ? res : (res.data || res.members || []);\n"
        "            setMembersList(list);\n"
        "        }).catch(err => console.error(\"Failed fetching records array\", err));\n"
        "        bankService.getBankNames().then(setBankNames).catch(err => console.error(\"Failed to load banks\", err));\n"
        "    }, []);",
    ),
    (
        "    const bankOptions = BANK_OPTIONS;",
        "    const bankOptions = bankNames;",
    ),
])

# ---------------------------------------------------------------------------
# 5. memberConstants.js — remove the BANK_OPTIONS export
# ---------------------------------------------------------------------------
BANK_BLOCK = (
    "export const BANK_OPTIONS = [\n"
    "  'Absa Bank', 'Absa Bank Limited', 'Access Bank (South Africa) Limited', 'AfrAsia Bank Limited', 'African Bank Limited',\n"
    "  'Albaraka Bank Limited', 'Banco Internacional de Credito', 'Bank Julius Baer & Co. Ltd', 'Bank Lombard Odier & Co Limited',\n"
    "  'Bank Of China Limited', 'Bank Of Communication Co., Ltd', 'Bank Vontobel AG', 'Bank Zero Mutual Bank', 'Bank of America N.A.',\n"
    "  'Bank of Taiwan', 'Banque SYZ SA', 'Barclays Bank PLC', 'Bidvest Bank Limited', 'CBZ Bank Limited', 'Caixa Geral de Depositos',\n"
    "  'CaixaBank', 'Capital International Bank Limited', 'Capitec Bank', 'Capitec Bank Limited', 'China Construction Bank Corporation',\n"
    "  'Citibank', 'Commerzbank AG', 'Deutsche Bank (Suisse) SA', 'Deutsche Bank AG', 'Discovery Bank Limited',\n"
    "  'Ditsobotla Primary Savings and Credit Co-operative Bank', 'Doha Bank', 'Ecobank', 'Export Import Bank of India',\n"
    "  'First National Bank', 'FirstRand Limited', 'GBS Mutual Bank', 'GIG Co-operative Bank', 'GoTyme Bank Limited',\n"
    "  'Goldman Sachs International Bank', 'HBZ Bank Limited', 'HSBC Bank Plc', 'Industrial and Commercial Bank of China',\n"
    "  'Investec Bank', 'Investec Bank Limited', 'KSK Kooperatiewe Bank Beperk', 'KfW IPEX-Bank GmbH', 'Macquarie Bank Limited',\n"
    "  'Metbank Limited', 'Mitsubishi UFJ Financial Group Bank, Ltd.', 'Mizuho Bank Ltd', 'Nedbank', 'OM Bank Limited',\n"
    "  'OSK Kooperatiewe Bank Beperk', 'Sasfin Bank Limited', 'Societe Generale', 'Standard Bank', 'Standard Bank South Africa',\n"
    "  'State Bank of India', 'Sumitomo Mitsui Banking', 'The Export & Import Bank of China', 'The Mauritius Commercial Bank Limited',\n"
    "  'The Standard Bank of South Africa Limited', 'Ubank (under curatorship)', 'Union Bancaire Privee, UBP SA',\n"
    "  'Ziphakamise Savings and Credit Co-operative Bank', 'eNL Mutual Bank',\n"
    "];\n\n"
    "export const BRANCH_OPTIONS = ["
)
apply(f"{FE}/config/memberConstants.js", [
    (BANK_BLOCK, "export const BRANCH_OPTIONS = ["),
])

print("\nAll replacements applied successfully.")
