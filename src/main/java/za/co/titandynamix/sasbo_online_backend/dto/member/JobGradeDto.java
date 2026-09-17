package za.co.titandynamix.sasbo_online_backend.dto.member;

import lombok.Data;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;

@Data
public class JobGradeDto {

    private Integer id;
    private Bank bank;
    private String grade;
    private String contractType;
    private String description;
    /** 02.6 -- BU, NBU, CONDITIONAL, or null when the union has not told us. */
    private String bargainingUnit;
    /** What else must be true when the classification is CONDITIONAL. */
    private String bargainingUnitCondition;
    private Boolean isBargainingCouncil;

}
