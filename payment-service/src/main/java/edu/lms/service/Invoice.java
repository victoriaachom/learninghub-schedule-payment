package edu.lms.service;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice",
       uniqueConstraints = @UniqueConstraint(name = "uq_invoice_user_term", columnNames = {"username", "term_id"}))
public class Invoice {

    public static final String PAID = "Paid";
    public static final String UNPAID = "Unpaid";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "term_id", nullable = false)
    private String termId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status = UNPAID;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    // Same database, so a real foreign key. Deleting a card keeps the invoice.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "method_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PaymentMethod method;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InvoiceLine> lines = new ArrayList<>();

    public boolean isPaid() { return PAID.equals(status); }

    public void replaceLines(List<InvoiceLine> newLines) {
        lines.clear();
        newLines.forEach(l -> { l.setInvoice(this); lines.add(l); });
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDate paidOn) { this.paidOn = paidOn; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public List<InvoiceLine> getLines() { return lines; }
}
