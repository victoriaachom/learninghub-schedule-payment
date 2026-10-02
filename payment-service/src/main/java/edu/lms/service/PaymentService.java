package edu.lms.service;

import edu.lms.service.Dtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    static final BigDecimal PRICE_PER_SUBJECT = new BigDecimal("320.00");
    static final int ISSUED_DAYS_BEFORE_DUE = 30;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final InvoiceRepository invoices;
    private final PaymentMethodRepository methods;
    private final BillingProfileRepository billing;
    private final EnrollmentClient enrollment;
    private final ScheduleClient schedule;

    public PaymentService(InvoiceRepository invoices, PaymentMethodRepository methods,
                          BillingProfileRepository billing, EnrollmentClient enrollment,
                          ScheduleClient schedule) {
        this.invoices = invoices;
        this.methods = methods;
        this.billing = billing;
        this.enrollment = enrollment;
        this.schedule = schedule;
    }

    // ================= views from the API spec =================

    public BillingProfile billing(Caller caller) {
        return billing.findByUsername(caller.username())
                .orElseThrow(() -> notFound("No billing profile for " + caller.username()));
    }

    public List<PaymentMethod> methods(Caller caller) {
        return methods.findByUsernameOrderByIdAsc(caller.username());
    }

    public List<HistoryRow> history(Caller caller) {
        return visibleInvoices(caller).stream()
                .filter(Invoice::isPaid)
                .sorted(Comparator.comparing(Invoice::getPaidOn, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(i -> new HistoryRow(i.getId(), fmt(i.getPaidOn()), i.getDescription(),
                        money(i.getAmount()), Invoice.PAID, caller.isAdmin() ? i.getUsername() : null))
                .toList();
    }

    public List<UpcomingRow> upcoming(Caller caller) {
        List<Invoice> all = visibleInvoices(caller);
        Map<String, Long> firstOpen = firstOpenByUser(all);
        List<Invoice> open = all.stream()
                .filter(i -> !i.isPaid())
                .sorted(Comparator.comparing(Invoice::getDueDate))
                .toList();
        List<UpcomingRow> rows = new ArrayList<>();
        for (int n = 0; n < open.size(); n++) {
            Invoice i = open.get(n);
            rows.add(new UpcomingRow(i.getId(), fmt(i.getDueDate()), i.getDescription(),
                    money(i.getAmount()), displayStatus(i, firstOpen), n == 0 ? "Pay Now" : "View Details",
                    caller.isAdmin() ? i.getUsername() : null));
        }
        return rows;
    }

    @Transactional
    public Summary summary(Caller caller) {
        raiseTermInvoiceIfMissing(caller);
        List<Invoice> all = visibleInvoices(caller);
        LocalDate today = LocalDate.now();

        List<Invoice> paid = all.stream().filter(Invoice::isPaid).toList();
        List<Invoice> open = all.stream().filter(i -> !i.isPaid()).toList();
        List<Invoice> overdue = open.stream().filter(i -> i.getDueDate().isBefore(today)).toList();

        if (caller.isAdmin()) {
            long students = all.stream().map(Invoice::getUsername).distinct().count();
            return new Summary(List.of(
                    new Stat("Total Collected", money(sum(paid)), count(paid.size(), "payment"), "blue", "paid"),
                    new Stat("Outstanding", money(sum(open)), count(open.size(), "open invoice"), "purple", "upcoming"),
                    new Stat("Overdue", money(sum(overdue)), count(overdue.size(), "invoice"), "amber", "pending"),
                    new Stat("Students Billed", String.valueOf(students), "across all terms", "green", "students")
            ), "school");
        }

        Optional<Invoice> next = open.stream()
                .filter(i -> !i.getDueDate().isBefore(today))
                .min(Comparator.comparing(Invoice::getDueDate));
        // Amount due now = anything overdue + the next invoice if it is due within 30 days.
        BigDecimal dueNow = sum(overdue).add(next
                .filter(i -> !i.getDueDate().isAfter(today.plusDays(30)))
                .map(Invoice::getAmount).orElse(BigDecimal.ZERO));

        return new Summary(List.of(
                new Stat("Total Paid", money(sum(paid)), count(paid.size(), "payment"), "blue", "paid"),
                new Stat("Upcoming Payment", money(next.map(Invoice::getAmount).orElse(BigDecimal.ZERO)),
                        next.map(i -> "Due " + fmt(i.getDueDate())).orElse("Nothing scheduled"), "purple", "upcoming"),
                new Stat("Pending", money(sum(overdue)),
                        overdue.isEmpty() ? "No pending payments" : count(overdue.size(), "overdue invoice"), "amber", "pending"),
                new Stat("Amount Due", money(dueNow),
                        dueNow.signum() == 0 ? "Nothing due right now" : "Due within 30 days", "green", "due")
        ), null);
    }

    public InvoiceDetails details(Caller caller, Long id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> notFound("Unknown invoice " + id));
        if (!caller.canSee(inv.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of invoice " + id);
        }
        String status = displayStatus(inv, firstOpenByUser(invoices.findByUsernameOrderByDueDateAsc(inv.getUsername())));
        LocalDate issued = inv.getDueDate().minusDays(ISSUED_DAYS_BEFORE_DUE);

   
        Map<String, EnrollmentClient.EnrolledClass> catalog =
                inv.getLines().isEmpty() ? Map.of() : enrollment.courses();
        List<BreakdownLine> lines = inv.getLines().stream()
                .map(l -> {
                    EnrollmentClient.EnrolledClass c = catalog.get(l.getCourseId());
                    return new BreakdownLine(l.getCourseId(), l.getDescription(),
                            c == null ? null : c.gradeLevel(), c == null ? null : c.credits(), money(l.getAmount()));
                })
                .toList();
        BigDecimal total = inv.getLines().stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (lines.isEmpty()) {   // an invoice without lines: one tuition line
            lines = List.of(new BreakdownLine(null, "Tuition", null, null, money(inv.getAmount())));
            total = inv.getAmount();
        }

        PaymentMethod m = inv.getMethod();
        return new InvoiceDetails(
                inv.getId(), inv.getDescription(), term(inv.getDescription()),
                money(inv.getAmount()), inv.getAmount().doubleValue(),
                fmt(inv.getDueDate()), fmt(issued), status,
                ChronoUnit.DAYS.between(LocalDate.now(), inv.getDueDate()),
                fmt(inv.getPaidOn()),
                m == null ? null : m.getBrand() + " ending in " + m.getLast4(),
                inv.isPaid() ? String.format("RCPT-%d-%05d", inv.getPaidOn().getYear(), inv.getId()) : null,
                !inv.isPaid(), lines, money(total),
                List.of(new TimelineStep("Invoice issued", fmt(issued), !issued.isAfter(LocalDate.now())),
                        new TimelineStep("Due", fmt(inv.getDueDate()), inv.isPaid() || !inv.getDueDate().isAfter(LocalDate.now())),
                        new TimelineStep("Paid", fmt(inv.getPaidOn()), inv.isPaid())));
    }

    /** Finds the caller's invoice by id, else by description, else the next unpaid one, and pays it. */
    @Transactional
    public PayResult pay(Caller caller, PayRequest req) {
        PayRequest r = req == null ? new PayRequest(null, null, null, null) : req;
        Invoice inv;
        if (r.id() != null) {
            inv = invoices.findById(r.id()).orElseThrow(() -> notFound("Unknown invoice " + r.id()));
            if (!inv.getUsername().equals(caller.username())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of invoice " + r.id());
            }
        } else if (r.description() != null && !r.description().isBlank()) {
            inv = invoices.findFirstByUsernameAndDescription(caller.username(), r.description())
                    .orElseThrow(() -> notFound("No invoice named '" + r.description() + "'"));
        } else {
            inv = invoices.findByUsernameOrderByDueDateAsc(caller.username()).stream()
                    .filter(i -> !i.isPaid()).findFirst()
                    .orElseThrow(() -> notFound("Nothing left to pay"));
        }
        if (inv.isPaid()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice " + inv.getId() + " is already paid");
        }

        PaymentMethod card = r.methodId() != null
                ? methods.findById(r.methodId())
                        .filter(m -> m.getUsername().equals(caller.username()))
                        .orElseThrow(() -> badRequest("Unknown card " + r.methodId()))
                : methods.findFirstByUsernameAndDefaultMethodTrue(caller.username()).orElse(null);

        inv.setStatus(Invoice.PAID);
        inv.setPaidOn(LocalDate.now());
        inv.setMethod(card);
        invoices.save(inv);
        return new PayResult(inv.getId(), inv.getDescription(), money(inv.getAmount()),
                fmt(inv.getPaidOn()), Invoice.PAID, card == null ? null : card.getId());
    }

    // ================= plain CRUD (Phase 1 requirement) =================

    public List<InvoiceView> listInvoices(Caller caller) {
        return visibleInvoices(caller).stream().map(InvoiceView::of).toList();
    }

    public InvoiceView getInvoice(Caller caller, Long id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> notFound("Unknown invoice " + id));
        if (!caller.canSee(inv.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner of invoice " + id);
        }
        return InvoiceView.of(inv);
    }

    @Transactional
    public InvoiceView createInvoice(Caller caller, InvoiceRequest req) {
        requireAdmin(caller);
        if (invoices.existsByUsernameAndTermId(req.username(), req.termId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    req.username() + " already has an invoice for term " + req.termId());
        }
        Invoice inv = new Invoice();
        apply(inv, req);
        return InvoiceView.of(invoices.save(inv));
    }

    @Transactional
    public InvoiceView updateInvoice(Caller caller, Long id, InvoiceRequest req) {
        requireAdmin(caller);
        Invoice inv = invoices.findById(id).orElseThrow(() -> notFound("Unknown invoice " + id));
        boolean keyChanged = !inv.getUsername().equals(req.username()) || !inv.getTermId().equals(req.termId());
        if (keyChanged && invoices.existsByUsernameAndTermId(req.username(), req.termId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    req.username() + " already has an invoice for term " + req.termId());
        }
        apply(inv, req);
        return InvoiceView.of(invoices.save(inv));
    }

    @Transactional
    public void deleteInvoice(Caller caller, Long id) {
        requireAdmin(caller);
        if (!invoices.existsById(id)) throw notFound("Unknown invoice " + id);
        invoices.deleteById(id);
    }

    @Transactional
    public PaymentMethod addMethod(Caller caller, PaymentMethod card) {
        card.setId(null);
        card.setUsername(caller.username());
        boolean first = methods.findByUsernameOrderByIdAsc(caller.username()).isEmpty();
        if (first) card.setDefaultMethod(true);
        if (card.isDefaultMethod()) clearDefault(caller.username());
        return methods.save(card);
    }

    @Transactional
    public PaymentMethod updateMethod(Caller caller, Long id, PaymentMethod incoming) {
        PaymentMethod card = ownedMethod(caller, id);
        card.setBrand(incoming.getBrand());
        card.setLast4(incoming.getLast4());
        card.setExpires(incoming.getExpires());
        if (incoming.isDefaultMethod() && !card.isDefaultMethod()) {
            clearDefault(card.getUsername());
            card.setDefaultMethod(true);
        }
        return methods.save(card);
    }

    @Transactional
    public void deleteMethod(Caller caller, Long id) {
        methods.delete(ownedMethod(caller, id));
    }

    @Transactional
    public BillingProfile saveBilling(Caller caller, BillingProfile incoming) {
        BillingProfile profile = billing.findByUsername(caller.username()).orElseGet(BillingProfile::new);
        profile.setUsername(caller.username());
        profile.setName(incoming.getName());
        profile.setEmail(incoming.getEmail());
        profile.setAddress(incoming.getAddress());
        return billing.save(profile);
    }

    @Transactional
    public void deleteBilling(Caller caller) {
        billing.delete(billing(caller));
    }

    // ================= term invoice raising =================

    /**
     * A student who has no invoice for the current term gets one, built from their
     * enrolments: one $320 line per course. Does nothing for teachers and admins, or
     * if schedule-service or enrollment-service cannot be reached.
     */
    private void raiseTermInvoiceIfMissing(Caller caller) {
        if (!"student".equals(caller.role())) return;
        LocalDate today = LocalDate.now();
        Optional<ScheduleClient.TermRef> term = schedule.currentTerm(today);
        if (term.isEmpty() || invoices.existsByUsernameAndTermId(caller.username(), term.get().id())) return;

        List<EnrollmentClient.EnrolledClass> classes = enrollment.enrolledClasses(caller.username());
        if (classes.isEmpty()) return;

        ScheduleClient.TermRef t = term.get();
        Invoice inv = new Invoice();
        inv.setUsername(caller.username());
        inv.setTermId(t.id());
        inv.setDescription(t.name() + " Tuition");
        inv.setAmount(PRICE_PER_SUBJECT.multiply(BigDecimal.valueOf(classes.size())));
        LocalDate due = t.startDate() == null ? today.plusDays(30) : t.startDate().plusDays(30);
        inv.setDueDate(due.isBefore(today.plusDays(7)) ? today.plusDays(14) : due);
        inv.replaceLines(classes.stream()
                .map(c -> new InvoiceLine(c.id(), c.name() == null ? c.id() : c.name(), PRICE_PER_SUBJECT))
                .toList());
        invoices.save(inv);
    }

    // ================= helpers =================

    private List<Invoice> visibleInvoices(Caller caller) {
        return caller.isAdmin()
                ? invoices.findAllByOrderByDueDateAsc()
                : invoices.findByUsernameOrderByDueDateAsc(caller.username());
    }

    /** For each user, the id of their earliest unpaid invoice that is not overdue. */
    private Map<String, Long> firstOpenByUser(List<Invoice> list) {
        LocalDate today = LocalDate.now();
        return list.stream()
                .filter(i -> !i.isPaid() && !i.getDueDate().isBefore(today))
                .collect(Collectors.groupingBy(Invoice::getUsername,
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparing(Invoice::getDueDate)),
                                o -> o.map(Invoice::getId).orElse(null))));
    }

    private String displayStatus(Invoice i, Map<String, Long> firstOpen) {
        if (i.isPaid()) return "Paid";
        if (i.getDueDate().isBefore(LocalDate.now())) return "Overdue";
        return i.getId().equals(firstOpen.get(i.getUsername())) ? "Upcoming" : "Scheduled";
    }

    private void apply(Invoice inv, InvoiceRequest req) {
        inv.setUsername(req.username());
        inv.setTermId(req.termId());
        inv.setDescription(req.description());
        if (req.lines() != null) {
            inv.replaceLines(req.lines().stream()
                    .map(l -> new InvoiceLine(l.courseId(), l.description(), l.amount()))
                    .toList());
        }
        BigDecimal amount = req.amount() != null ? req.amount()
                : inv.getLines().stream().map(InvoiceLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.signum() <= 0) throw badRequest("amount (or lines) required");
        inv.setAmount(amount);
        inv.setDueDate(req.dueDate());
        boolean paid = Invoice.PAID.equalsIgnoreCase(req.status());
        inv.setStatus(paid ? Invoice.PAID : Invoice.UNPAID);
        inv.setPaidOn(paid ? (req.paidOn() != null ? req.paidOn() : LocalDate.now()) : null);
        inv.setMethod(req.methodId() == null ? null
                : methods.findById(req.methodId()).orElseThrow(() -> badRequest("Unknown card " + req.methodId())));
    }

    private PaymentMethod ownedMethod(Caller caller, Long id) {
        PaymentMethod card = methods.findById(id).orElseThrow(() -> notFound("Unknown card " + id));
        if (!caller.canSee(card.getUsername())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your card");
        }
        return card;
    }

    private void clearDefault(String username) {
        methods.findByUsernameOrderByIdAsc(username).forEach(m -> m.setDefaultMethod(false));
    }

    private static void requireAdmin(Caller caller) {
        if (!caller.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
    }

    private static BigDecimal sum(List<Invoice> list) {
        return list.stream().map(Invoice::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount == null ? BigDecimal.ZERO : amount);
    }

    static String fmt(LocalDate date) {
        return date == null ? null : date.format(DATE);
    }

    private static String count(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    /** "Q2 · Winter 2026 Tuition" -> "Q2 · Winter 2026" */
    private static String term(String description) {
        return description.endsWith(" Tuition") ? description.substring(0, description.length() - 8) : description;
    }

    private static ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
