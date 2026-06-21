package com.eightbit.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByRollNumber(String rollNumber);
    Optional<User> findByUsername(String username);
    boolean existsByRollNumber(String rollNumber);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByIdIn(Collection<Long> ids);
}
