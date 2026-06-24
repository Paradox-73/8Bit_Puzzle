package com.eightbit.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByRollNumber(String rollNumber);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByRollNumber(String rollNumber);
    boolean existsByUsername(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByEmail(String email);
    List<User> findByIdIn(Collection<Long> ids);

    /** Registered users grouped by cohort (program, batch year) — Batch War denominator. */
    @org.springframework.data.jpa.repository.Query(
            "select u.program, u.batchYear, count(u) from User u group by u.program, u.batchYear")
    List<Object[]> countByCohort();
}
