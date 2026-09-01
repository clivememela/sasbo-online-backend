package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;

import java.util.List;

public interface JobGradeRepository extends JpaRepository<JobGrade, Integer> {
    List<JobGrade> findByBank_Id(Integer bankId);
    List<JobGrade> findByBank_UniversalBranchCode(String universalBranchCode);
}

