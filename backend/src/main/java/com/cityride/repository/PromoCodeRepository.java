package com.cityride.repository;

import com.cityride.model.PromoCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromoCode p where upper(p.code) = upper(:code)")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    List<PromoCode> findAllByOrderByCodeAsc();
}
