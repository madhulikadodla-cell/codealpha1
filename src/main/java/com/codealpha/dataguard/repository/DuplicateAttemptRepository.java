package com.codealpha.dataguard.repository;

import com.codealpha.dataguard.entity.DuplicateAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DuplicateAttemptRepository extends JpaRepository<DuplicateAttempt, Long> {
    List<DuplicateAttempt> findTop8ByOrderByAttemptedAtDesc();
    List<DuplicateAttempt> findTop250ByOrderByAttemptedAtDesc();
}
