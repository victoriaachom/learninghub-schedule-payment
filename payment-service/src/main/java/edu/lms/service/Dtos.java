package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes. Field names match the team's API spec. */
public final class Dtos {

    private Dtos() {}

  

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryRow(Long id, String date, String description, String amount,
                             String status, String username) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpcomingRow(Long id, String dueDate, String description, String amount,
                              String status, String action, String username) {}

    public record Stat(String label, String value, String sub, String color, String icon) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(List<Stat> stats, String scope) {}

    public record BreakdownLine(String courseId, String subject, String gradeLevel, Integer credits, String amount) {}

    public record TimelineStep(String label, String date, boolean done) {}

    public record InvoiceDetails(Long id, String description, String term, String amount,
                                 double amountValue, String dueDate, String issuedOn, String status,
                                 long daysUntilDue, String paidOn, String method, String receiptNumber,
                                 boolean canPay, List<BreakdownLine> breakdown, String breakdownTotal,
                                 List<TimelineStep> timeline) {}

    public record PayRequest(Long id, String description, String amount, Long methodId) {}

    public record PayResult(Long id, String description, String amount, String date,
                            String status, Long methodId) {}

    // ---------- plain CRUD ----------

    public record LineRequest(@NotBlank String courseId, @NotBlank String description,
                              @NotNull @Positive BigDecimal amount) {}

    /** amount may be omitted when lines are given; it is then the sum of the lines. */
    public record InvoiceRequest(@NotBlank String username,
                                 @NotBlank String termId,
                                 @NotBlank String description,
                                 @Positive BigDecimal amount,
                                 @NotNull LocalDate dueDate,
                                 String status,
                                 LocalDate paidOn,
                                 Long methodId,
                                 List<@Valid LineRequest> lines) {}

    public record LineView(Long id, String courseId, String description, BigDecimal amount) {}

    public record InvoiceView(Long id, String username, String termId, String description, BigDecimal amount,
                              LocalDate dueDate, String status, LocalDate paidOn, Long methodId,
                              List<LineView> lines) {
        static InvoiceView of(Invoice i) {
            return new InvoiceView(i.getId(), i.getUsername(), i.getTermId(), i.getDescription(), i.getAmount(),
                    i.getDueDate(), i.getStatus(), i.getPaidOn(),
                    i.getMethod() == null ? null : i.getMethod().getId(),
                    i.getLines().stream()
                            .map(l -> new LineView(l.getId(), l.getCourseId(), l.getDescription(), l.getAmount()))
                            .toList());
        }
    }
}
