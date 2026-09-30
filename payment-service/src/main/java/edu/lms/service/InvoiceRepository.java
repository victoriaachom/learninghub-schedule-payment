package edu.lms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByUsernameOrderByDueDateAsc(String username);
    List<Invoice> findAllByOrderByDueDateAsc();
    Optional<Invoice> findFirstByUsernameAndDescription(String username, String description);
}
