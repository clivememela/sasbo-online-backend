package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.codes.DeletionCode;

import java.util.List;

@Repository
public interface DeletionCodeRepository extends JpaRepository<DeletionCode, Long> {
    // Fetches only the main categories (where parent_id IS NULL)
    List<DeletionCode> findByParentIsNull();
}
