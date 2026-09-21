package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCode;

public interface AcbUnpaidCodeRepository extends JpaRepository<AcbUnpaidCode, String> {
}
