package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.workplace.Workplace;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.WorkplaceEntityRepository;

import java.util.Map;

/**
 * A member's workplace (month end 01.3): what a save names, and what an answer says.
 *
 * <h2>A save</h2>
 * The workplace is named by its id on record -- the transient {@code workplaceId} -- and resolved here, as
 * {@code structureId} and {@code roleId} are; a workplace OBJECT in a save is never bound (BR66). It is looked for
 * BEFORE anything is written, so the database's foreign key is never what refuses it: one that is not on record here,
 * or any where the deployment's switch serves none (MD62), is {@code WORKPLACE_UNKNOWN}, answered by the global
 * handler because the save runs under {@code MemberController} (ME-12). A save that names none leaves the member's
 * workplace as it was (MD58). Nothing else of the member is touched: {@code bankName}, {@code branch} and
 * {@code ibtNo} stay exactly as they were sent (BR21).
 *
 * <h2>An answer</h2>
 * AS THE SWITCH ALLOWS (MD62): at {@code none} no answer names a workplace, whatever the table holds -- a switch set
 * back stops what was published. The list of members carries three scalars and no more; the answer for one member
 * carries the workplace's lineage too, read by the workplace's own statement (MR-PERF-9).
 */
@Service
public class MemberWorkplaces {

    private final WorkplaceEntityRepository workplaces;
    private final WorkplaceService lineages;
    private final WorkplaceSwitch workplaceSwitch;

    public MemberWorkplaces(WorkplaceEntityRepository workplaces, WorkplaceService lineages,
                            WorkplaceSwitch workplaceSwitch) {
        this.workplaces = workplaces;
        this.lineages = lineages;
        this.workplaceSwitch = workplaceSwitch;
    }

    /**
     * Sets the workplace a save names on {@code target}. {@code null} leaves it as it was.
     *
     * @throws MonthEndRefusal {@code WORKPLACE_UNKNOWN} -- before anything is written
     */
    public void resolve(Long workplaceId, Member target) {
        if (workplaceId == null) {
            return;
        }
        Workplace found = workplaceSwitch.workplacesOnRecord() ? workplaces.findById(workplaceId).orElse(null) : null;
        if (found == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_UNKNOWN,
                    "That workplace is not on record here. Choose another.",
                    Map.of("workplaceId", String.valueOf(workplaceId)));
        }
        target.setWorkplace(found);
    }

    /** An answer in a LIST: the three scalars as the switch allows, and no lineage. */
    public void inAList(MemberDto answer) {
        if (answer == null) {
            return;
        }
        answer.setWorkplace(null);
        if (!workplaceSwitch.workplacesOnRecord()) {
            answer.setWorkplaceId(null);
            answer.setWorkplaceName(null);
            answer.setWorkplaceRaNumber(null);
        }
    }

    /** The answer for ONE member: the three scalars as the switch allows, and the workplace's lineage. */
    public void forOne(MemberDto answer) {
        inAList(answer);
        if (answer != null && answer.getWorkplaceId() != null) {
            answer.setWorkplace(lineages.one(answer.getWorkplaceId()).orElse(null));
        }
    }
}
