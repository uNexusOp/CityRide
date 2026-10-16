package com.cityride.repository;

import com.cityride.model.UserAccount;
import com.cityride.model.Role;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserAccount> findByGoogleSubject(String googleSubject);
    List<UserAccount> findByRoleAndAvailableTrueOrderByFullNameAsc(Role role);
    List<UserAccount> findByRoleOrderByAverageRatingDescFullNameAsc(Role role);
    long countByRole(Role role);
}
