package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


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

    public record BreakdownLine(String subject, String gradeLevel, Integer credits, String amount) {}

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

    public record InvoiceRequest(@NotBlank String username,
                                 @NotBlank String description,
                                 @NotNull @Positive BigDecimal amount,
                                 @NotNull LocalDate dueDate,
                                 String status,
                                 LocalDate paidOn,
                                 Long methodId) {}

    public record InvoiceView(Long id, String username, String description, BigDecimal amount,
                              LocalDate dueDate, String status, LocalDate paidOn, Long methodId) {
        static InvoiceView of(Invoice i) {
            return new InvoiceView(i.getId(), i.getUsername(), i.getDescription(), i.getAmount(),
                    i.getDueDate(), i.getStatus(), i.getPaidOn(),
                    i.getMethod() == null ? null : i.getMethod().getId());
        }
    }
}
