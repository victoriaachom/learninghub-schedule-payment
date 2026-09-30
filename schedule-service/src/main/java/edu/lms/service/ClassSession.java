package edu.lms.service;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "class_session", indexes = {
        @Index(columnList = "course_id"),
        @Index(columnList = "term_id")})
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Same database, so a real foreign key. Deleting a term deletes its sessions.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "term_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Term term;

    @Column(name = "course_id", nullable = false)
    private String courseId;

    @Column(nullable = false)
    private String subject;

    @Column(name = "grade_level")
    private String gradeLevel;

    @Column(nullable = false)
    private String color = "blue";

    private String teacher;

    @Column(name = "teacher_username")
    private String teacherUsername;

    /** e.g. ["Mon","Wed","Fri"], stored as jsonb */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> days = new ArrayList<>();

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /** Optional; when empty the term's dates apply. */
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    public LocalDate effectiveStart() { return startDate != null ? startDate : term.getStartDate(); }
    public LocalDate effectiveEnd() { return endDate != null ? endDate : term.getEndDate(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Term getTerm() { return term; }
    public void setTerm(Term term) { this.term = term; }
    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getGradeLevel() { return gradeLevel; }
    public void setGradeLevel(String gradeLevel) { this.gradeLevel = gradeLevel; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }
    public String getTeacherUsername() { return teacherUsername; }
    public void setTeacherUsername(String teacherUsername) { this.teacherUsername = teacherUsername; }
    public List<String> getDays() { return days; }
    public void setDays(List<String> days) { this.days = days; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
