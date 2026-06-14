package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {
    Province findByCode(String code);
}
