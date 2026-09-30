package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUsernameOrderByIdAsc(String username);
    Optional<PaymentMethod> findFirstByUsernameAndDefaultMethodTrue(String username);
}
