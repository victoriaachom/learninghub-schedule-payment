package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingProfileRepository extends JpaRepository<BillingProfile, Long> {
    Optional<BillingProfile> findByUsername(String username);
}
