package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;


@Entity
@Table(name = "term")
public class Term {

    @Id
    @NotBlank
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    @JsonProperty("isCurrent")
    private boolean current;

    @Column(name = "max_credits", nullable = false)
    private int maxCredits = 30;

    private Integer quarter;

    @Column(name = "academic_year")
    private String academicYear;

    public boolean contains(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    @JsonProperty("isCurrent")
    public boolean isCurrent() { return current; }
    @JsonProperty("isCurrent")
    public void setCurrent(boolean current) { this.current = current; }
    public int getMaxCredits() { return maxCredits; }
    public void setMaxCredits(int maxCredits) { this.maxCredits = maxCredits; }
    public Integer getQuarter() { return quarter; }
    public void setQuarter(Integer quarter) { this.quarter = quarter; }
    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }
}
