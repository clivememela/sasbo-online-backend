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

}
