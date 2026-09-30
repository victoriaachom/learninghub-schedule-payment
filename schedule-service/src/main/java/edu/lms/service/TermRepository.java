package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermRepository extends JpaRepository<Term, String> {
    List<Term> findAllByOrderByStartDateAsc();
    Optional<Term> findFirstByCurrentTrue();
}
