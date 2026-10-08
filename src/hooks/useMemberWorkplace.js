import { memberService } from '../services/memberService';
import { NOT_AVAILABLE, answerFailed, memberWorkplace } from '../utils/workplaces';
import useAsyncOptions from './useAsyncOptions';

/**
 * THE MEMBER'S WORKPLACE ON THE RECORD -- its lineage, which only the answer for ONE member carries (`01.3`).
 *
 * The register's list gives each member three scalars and no more -- `workplaceId`, `workplaceName`,
 * `workplaceRaNumber` (MR-PERF-9: a lineage per row would make the listing a join of seventeen tables) -- so the
 * record reads `GET /api/members/{id}` for the area and the committee, and only while its Employment tab is open.
 *
 *   no `workplaceId` key at all  -- a server built before month end says nothing of workplaces: `{ why }`, and
 *                                   nothing is asked of it;
 *   `workplaceId: null`          -- `{ none: true }`: *no workplace on record*, a value a member may honestly have
 *                                   (every member, where the deployment's switch serves no workplaces, MD62);
 *   an id                        -- asked for: `{ loading }`, then `{ workplace }` · `{ none }` · `{ why }` (a 404,
 *                                   or an answer of the wrong shape) · `{ failure, retry }`.
 *
 * `useAsyncOptions` sequences it: an answer for a member the record has moved on from is dropped.
 */
export default function useMemberWorkplace(member, open) {
  const id = member?.id;
  const workplaceId = member?.workplaceId;
  const read = useAsyncOptions(
    () => memberService.getMemberById(id).then((one) => [memberWorkplace(one)], answerFailed('workplaces.member')),
    [id, workplaceId],
    { enabled: Boolean(open) && workplaceId != null },
  );
  if (!member) return null;
  if (!('workplaceId' in member)) return { why: NOT_AVAILABLE.NOT_HERE };
  if (workplaceId == null) return { none: true };
  return read.loading ? { loading: true } : { ...(read.error ? { failure: read.error } : read.options[0]), retry: read.refetch };
}
