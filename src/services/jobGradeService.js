import {apiUrl} from './api';
import {requestJson} from './parseApiError';
import {bankService} from './bankService.js';
import {nameKey} from '../utils/nameKey.js';

/*
 * ✅ A25 IS CLOSED HERE, AND `JOB_GRADE_OPTIONS` IS WHAT WENT WITH IT. `06.1b`.
 *
 * This module used to end in `.catch(() => JOB_GRADE_OPTIONS)`. That was written as a
 * graceful degradation and it was one, once: the constant held a real list of grades, so a
 * backend that was down still let a capture proceed. `JOB_GRADE_OPTIONS` was later EMPTIED
 * -- grades now come from the backend, per bank -- and the fallback became a swallow. Every
 * failure resolved as `[]`, which every caller rendered as "this bank has no job grades".
 * Neither change was wrong on its own, which is why the defect survived both reviews.
 *
 * 🔴 THE TWO HALVES COULD NOT LAND SEPARATELY, and that is the whole reason this row waited
 * for `06.1b` rather than riding `06.1a` with the other three. The register's criterion is
 * *"the method rejects AND the caller renders an error"*. Deleting the `catch` alone would
 * have rejected into `CaptureMember` and `Members`, neither of which had an error state for
 * a reference list -- an unhandled rejection where a (useless, but quiet) fallback used to
 * be. The commit that deletes it is the commit that gives both callers a per-panel error
 * state with retry (EH-D9).
 *
 * ⚠️ AND THE EMPTY LIST IS STILL A REAL ANSWER. A bank with no grades on file resolves `[]`
 * and the field says so. What changed is that a 500, a dropped connection, an HTML gateway
 * page and a ten-second hang no longer say the same thing.
 */

const normalizeList = (payload) => {
  if (!payload) return [];
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.items)) return payload.items;
  if (Array.isArray(payload?.content)) return payload.content;
  return [];
};

export const jobGradeService = {
  // Fetches the job grades a given bank has on file.
  // bankIdentifier may be a bank code (preferred) or a human-readable bank name.
  // When given a human name we attempt to resolve it to a bank code using bankService.
  // Resolves the grades -- possibly none -- or REJECTS in conventions.md §16's shape.
  getByBank: async (bankIdentifier) => {
    // No bank chosen is not a failure and not an error state: there is nothing to ask for
    // yet. The caller's field is disabled until a bank is picked.
    if (!bankIdentifier) return [];
    // If the identifier looks like a code (no spaces, short), use as-is; otherwise try to resolve.
    let bankCode = String(bankIdentifier || '').trim();
    if (bankCode.includes(' ')) {
      // 🔴 THE NAME->CODE LOOKUP NO LONGER SWALLOWS ITS FAILURE (`06.4`, F-P54). It fell through to the
      // query below with the bank's NAME, which no bank code matches -- so a failed lookup rendered as
      // "this bank has no job grades", A25 again one call earlier. Without the code the grades cannot be
      // asked for, so the rejection goes to the caller's error state and its retry. A name the listing
      // does not know is still queried as given: that is an answer, not a failure.
      const { branches, codes, codesError } = await bankService.getBankListingDetailed();
      if (codes === 'failed') throw codesError;
      const normalized = nameKey(bankCode);
      const found = branches.find((b) => {
        const bn = nameKey(b.bankName || b.name);
        return bn === normalized || bn.includes(normalized) || normalized.includes(bn);
      });
      if (found && found.universalBranchCode) bankCode = found.universalBranchCode;
    }

    const qs = `?bankCode=${encodeURIComponent(String(bankCode))}`;

    return requestJson((signal) => fetch(apiUrl(`/api/job-grades${qs}`), {
      credentials: 'include',
      signal,
    }))
        .then((payload) => {
            const list = normalizeList(payload);

          // ⚠️ NO `.catch()` BELOW, AND ITS ABSENCE IS THE FIX. A rejection travels to the
          // caller, which renders it (EH-D9). See the header.
          return list
              .map((it) => {
                  if (!it || typeof it !== 'object') return null;

                  // If the backend sent standard React { value, label } fields,
                  // still force label to grade-only text.
                  if (it.value != null) {
                      const grade = String(it.grade ?? it.value ?? '').trim();
                      if (!grade) return null;
                      return {
                          ...it,
                          value: grade,
                          label: grade,
                      };
                  }

                  // Map your Java JobGradeResponseDto fields structurally
                  const grade = String(it.grade || '').trim();
                  if (!grade) return null;
                  return {
                      value: grade,
                      label: grade
                  };
              })
              .filter(Boolean);
        });
  }
};
