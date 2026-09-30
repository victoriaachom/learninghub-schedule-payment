package edu.lms.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

/** Request and response shapes. Field names match the team's API spec. */
public final class ScheduleDtos {

    private ScheduleDtos() {}

    public record TermInfo(String start, String end, String name) {}

    // GET /month
    public record MonthClass(int day, String subject, String color, String start, String end, String gradeLevel) {}
    public record MonthView(String month, int year, int monthNumber, int firstDow, int days,
                            Integer today, List<MonthClass> classes) {}

    // GET /term
    public record TermBar(String subject, String color, String label, int startPct, int endPct) {}
    public record TermView(TermInfo term, List<String> months, List<TermBar> bars) {}

    // GET /today
    public record TodayClass(String start, String end, String subject, String detail, String gradeLevel,
                             String color, String teacher, String teacherUsername, String courseId) {}

    // GET /week
    public record WeekDay(String name, String date) {}
    public record WeekBlock(String subject, String gradeLevel, String color, String teacher,
                            String teacherUsername, List<String> days, String start, String end) {}
    public record MiniCalendar(String month, int selected, int highlight, List<Integer> dotDays) {}
    public record UpcomingClass(String subject, String when, String time, String gradeLevel, String teacher,
                                String teacherUsername, String courseId, String color) {}
    public record WeekView(TermInfo term, String weekLabel, List<WeekDay> days, List<String> hours,
                           List<WeekBlock> blocks, MiniCalendar miniCalendar, List<UpcomingClass> upcoming) {}

    // PUT /sessions/by-course/{courseId} and plain session CRUD
    public record SessionRequest(String courseId,
                                 @NotBlank String subject,
                                 String gradeLevel,
                                 String teacher,
                                 String teacherUsername,
                                 String color,
                                 @NotEmpty List<String> days,
                                 @NotBlank String startTime,
                                 String endTime,
                                 @NotBlank String termId,
                                 LocalDate startDate,
                                 LocalDate endDate) {}

    public record SessionView(Long id, String termId, String courseId, String subject, String gradeLevel,
                              String color, String teacher, String teacherUsername, List<String> days,
                              String startTime, String endTime, LocalDate startDate, LocalDate endDate) {
        static SessionView of(ClassSession s) {
            return new SessionView(s.getId(), s.getTerm().getId(), s.getCourseId(), s.getSubject(),
                    s.getGradeLevel(), s.getColor(), s.getTeacher(), s.getTeacherUsername(), List.copyOf(s.getDays()),
                    s.getStartTime().toString(), s.getEndTime().toString(), s.getStartDate(), s.getEndDate());
        }
    }
}
