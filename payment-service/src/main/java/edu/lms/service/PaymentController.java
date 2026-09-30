package edu.lms.service;

import edu.lms.service.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;


@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService service;
    private final CallerResolver callers;

    public PaymentController(PaymentService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    // ---------- endpoints from the team API spec ----------

    @GetMapping("/summary")
    public Summary summary() { return service.summary(callers.current()); }

    @GetMapping("/upcoming")
    public List<UpcomingRow> upcoming() { return service.upcoming(callers.current()); }

    @GetMapping("/history")
    public List<HistoryRow> history() { return service.history(callers.current()); }

    @GetMapping("/methods")
    public List<PaymentMethod> methods() { return service.methods(callers.current()); }

    @GetMapping("/billing")
    public BillingProfile billing() { return service.billing(callers.current()); }

    @GetMapping("/invoices/{id}/details")
    public InvoiceDetails details(@PathVariable Long id) { return service.details(callers.current(), id); }

    @PostMapping("/pay")
    public PayResult pay(@RequestBody(required = false) PayRequest request) {
        return service.pay(callers.current(), request);
    }

    // ---------- plain CRUD: invoices (create/update/delete are admin only) ----------

    @GetMapping("/invoices")
    public List<InvoiceView> listInvoices() { return service.listInvoices(callers.current()); }

    @GetMapping("/invoices/{id}")
    public InvoiceView getInvoice(@PathVariable Long id) { return service.getInvoice(callers.current(), id); }

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceView> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        InvoiceView saved = service.createInvoice(callers.current(), request);
        return ResponseEntity.created(URI.create("/api/payments/invoices/" + saved.id())).body(saved);
    }

    @PutMapping("/invoices/{id}")
    public InvoiceView updateInvoice(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return service.updateInvoice(callers.current(), id, request);
    }

    @DeleteMapping("/invoices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInvoice(@PathVariable Long id) { service.deleteInvoice(callers.current(), id); }

    // ---------- plain CRUD: the caller's cards ----------

    @PostMapping("/methods")
    public ResponseEntity<PaymentMethod> addMethod(@Valid @RequestBody PaymentMethod card) {
        PaymentMethod saved = service.addMethod(callers.current(), card);
        return ResponseEntity.created(URI.create("/api/payments/methods/" + saved.getId())).body(saved);
    }

    @PutMapping("/methods/{id}")
    public PaymentMethod updateMethod(@PathVariable Long id, @Valid @RequestBody PaymentMethod card) {
        return service.updateMethod(callers.current(), id, card);
    }

    @DeleteMapping("/methods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMethod(@PathVariable Long id) { service.deleteMethod(callers.current(), id); }

    // ---------- plain CRUD: the caller's billing profile ----------

    @PutMapping("/billing")
    public BillingProfile saveBilling(@Valid @RequestBody BillingProfile profile) {
        return service.saveBilling(callers.current(), profile);
    }

    @DeleteMapping("/billing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBilling() { service.deleteBilling(callers.current()); }

    // ---------- profile demo ----------

    @GetMapping("/whoami")
    public String whoami(@Value("${lms.environment-label}") String label) { return label; }
}
