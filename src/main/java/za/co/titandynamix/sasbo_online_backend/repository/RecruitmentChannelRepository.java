package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.RecruitmentChannel;

public interface RecruitmentChannelRepository extends JpaRepository<RecruitmentChannel, Long> {
    boolean existsByNameIgnoreCase(String name);
}
