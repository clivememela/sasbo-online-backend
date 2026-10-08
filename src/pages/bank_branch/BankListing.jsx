import { useCallback, useEffect, useMemo, useState } from "react";
import { Printer } from "lucide-react";
import { bankService } from "../../services/bankService.js";
import { TextInput, Button } from "../../components/form";
import PageTitle from '../../components/common/PageTitle';
import Criteria, { CRITERION, CRITERION_WIDE } from '../../components/common/Criteria.jsx';
import Pager from '../../components/common/Pager.jsx';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import Table from "../../components/table/Table.jsx";
import DataState from "../../components/common/DataState";
import reportError from "../../lib/reportError";

const PAGE_SIZE = bankService.getPageSize();

export default function BankListing() {
  const [banks, setBanks] = useState([]);
  const [filters, setFilters] = useState({
    bankName: "",
    abbreviation: "",
    universalBranchCode: "",
  });
  const [page, setPage] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState("");
  // Whether the universal branch codes arrived with the listing. They are an enrichment: without them
  // every bank still lists, and this page SAYS the codes are missing rather than printing a dash for each
  // one and answering a search by code with "no banks match" (F-P54, `06.4`).
  const [codes, setCodes] = useState("ready");

  const normalizeBankResponse = (payload) => {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.content)) return payload.content;
    if (Array.isArray(payload?.data)) return payload.data;
    if (Array.isArray(payload?.items)) return payload.items;
    return [];
  };

  /*
   * 🔴 THE TWO RESETS MOVED TO THE RETRY PATH (`10.4`). `react-hooks/set-state-in-effect` was
   * right: the effect below called this, and this set state synchronously before any await.
   *
   * 🔑 BOTH WERE NO-OPS ON MOUNT -- `isLoading` initialises to `true` and `error` to `""` -- so
   * taking them out of the mount path changes nothing that renders. They matter only when a
   * human presses Try again, which is what `retryBanks` is.
   *
   * ⚠️ `setError("")` IS NOT OPTIONAL ON THAT PATH. Nothing else ever clears it, so a retry that
   * succeeded without it would draw the refreshed listing underneath the old failure message.
   */
  const loadBanks = useCallback(() => {
    return bankService
      .getBankListingDetailed()
      .then(({ branches, codes: codesState, codesError }) => {
        setBanks(normalizeBankResponse(branches));
        setCodes(codesState);
        if (codesState === "failed") reportError("BankListing.codes", codesError);
      })
      .catch(() => {
        setError("Unable to load bank listing at the moment.");
      })
      .finally(() => {
        setIsLoading(false);
      });
  }, []);

  // The table's retry: it resets what the mount never needed to, then re-runs exactly the fetch
  // the mount ran. An error state whose button cannot repeat the request is a dead end.
  const retryBanks = useCallback(() => {
    setIsLoading(true);
    setError("");
    loadBanks();
  }, [loadBanks]);

  useEffect(() => {
    loadBanks();
  }, [loadBanks]);

  const listingRows = useMemo(() => {
    return banks.map((bank) => ({
      id: bank.id,
      bankName: bank.bankName ?? bank.name ?? "-",
      abbreviation: bank.bankAbbreviation ?? bank.abbreviation ?? "-",
      universalBranchCode:
        bank.universalBranchCode
        ?? bank.universal_branch_code
        ?? bank.bank?.universalBranchCode
        ?? bank.bank?.universal_branch_code
        ?? "-",
    }));
  }, [banks]);

  const filteredRows = useMemo(() => {
    const bankNameQuery = filters.bankName.trim().toLowerCase();
    const abbreviationQuery = filters.abbreviation.trim().toLowerCase();
    const branchCodeQuery = filters.universalBranchCode.trim().toLowerCase();

    return listingRows.filter((bank) => {
      if (bankNameQuery && !bank.bankName.toLowerCase().includes(bankNameQuery)) return false;
      if (abbreviationQuery && !bank.abbreviation.toLowerCase().includes(abbreviationQuery)) return false;
      // A search by code cannot be answered without the codes; the field is disabled while they are missing.
      return !(branchCodeQuery && codes !== "failed" && !bank.universalBranchCode.toLowerCase().includes(branchCodeQuery));
    });
  }, [filters, listingRows, codes]);

  const handleInputChange = (field) => (event) => {
    setFilters((prev) => ({ ...prev, [field]: event.target.value }));
    setPage(1);
  };

  const totalPages = Math.max(1, Math.ceil(filteredRows.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const startIndex = (safePage - 1) * PAGE_SIZE;
  const endIndex = Math.min(startIndex + PAGE_SIZE, filteredRows.length);
  const visibleRows = filteredRows.slice(startIndex, endIndex);

  const bankColumns = useMemo(() => [
    { key: 'bankName', header: 'Bank Name', priority: 'essential', truncate: true, cell: (b) => b.bankName || '\u2014' },
    {
      key: 'abbreviation',
      header: 'Abbreviation',
      priority: 'essential',
      cell: (b) => <span className="font-semibold text-brand-ink">{b.abbreviation || '\u2014'}</span>,
    },
    // A code the listing never received reads "Unavailable", not a dash: the dash says the bank has none (F-P54).
    { key: 'universalBranchCode', header: 'Universal Branch Code', priority: 'essential', cell: (b) => codes === "failed" && !b.universalBranchCode ? <span className="text-ink-muted">Unavailable</span> : b.universalBranchCode || '\u2014' },
  ], [codes]);

  return (
    <Page>
      <PageTitle
        title="Bank Listing"
        subtitle="Every bank, with its abbreviation and universal branch code, for reference."
        actions={
          <Button onClick={() => globalThis.print()} title="Print the listing, or save it as a PDF">
            <Printer size={16} strokeWidth={1.75} aria-hidden="true" /> Print
          </Button>
        }
      />

      <Criteria className="mt-5">
        <TextInput
          className={CRITERION_WIDE}
          label="Bank"
          value={filters.bankName}
          onChange={handleInputChange("bankName")}
          placeholder="Any"
        />
        <TextInput
          className={CRITERION}
          label="Abbreviation"
          value={filters.abbreviation}
          onChange={handleInputChange("abbreviation")}
          placeholder="Any"
        />
        <TextInput
          className={CRITERION}
          label="Universal branch code"
          kind="identifier"
          value={filters.universalBranchCode}
          onChange={handleInputChange("universalBranchCode")}
          placeholder="Any"
          disabled={codes === "failed"}
        />
      </Criteria>

      {codes === "failed" && !isLoading && !error && (
        <div className="mt-4 rounded-lg bg-surface shadow-(--shadow-1)">
          <DataState
            state="partial"
            layout="row"
            compact
            onRetry={retryBanks}
            title="Universal branch codes could not be loaded."
            message="Every bank below is listed, but not its code, so a search by code cannot be answered until they load."
          />
        </div>
      )}

      <Sheet className="mt-4">
        {/* 🔴 A `grid-cols-3` PSEUDO-TABLE, AND THE LAST OF THE SEVEN run 7 counted.
            Its header was three `<div>`s with no relationship to the cells beneath, so a
            screen-reader user met a stream of values with no column names -- and this is a
            reference table whose entire job is "which code belongs to which bank".

            🔑 IT ALREADY HAD ALL THREE STATES, hand-rolled, and they were CORRECT --
            loading, error and empty as separate branches with error never rendering as
            empty. That is rarer than it sounds (A27 is the same page one module over
            getting it wrong), so this is not a fix, it is the same three states expressed
            through the primitive that now owns them. The retry is new: the hand-rolled
            error branch printed the message and offered no way out. */}
        <div className="print-area">
          <Table
            caption="Banks, with their abbreviations and universal branch codes"
            columns={bankColumns}
            rows={visibleRows}
            rowKey="id"
            loading={isLoading}
            error={error || null}
            onRetry={retryBanks}
            emptyTitle="No banks match these criteria"
            emptyMessage="Try a different bank name, abbreviation or branch code."
          />
        </div>
        <Pager
          page={safePage}
          totalPages={totalPages}
          onPageChange={setPage}
          count={filteredRows.length}
          error={Boolean(error)}
          start={startIndex + 1}
          end={endIndex}
          noun="banks"
          loading={isLoading}
        />
      </Sheet>
    </Page>
  );
}