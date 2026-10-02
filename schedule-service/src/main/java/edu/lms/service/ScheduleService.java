package edu.lms.service;

import edu.lms.service.EnrollmentClient.Course;
import edu.lms.service.ScheduleDtos.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;


@Service
@Transactional(readOnly = true)
public class ScheduleService {

    static final List<String> WEEKDAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter TIME_NO_AMPM = DateTimeFormatter.ofPattern("h:mm", Locale.US);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.US);

    private final TermRepository terms;
    private final ClassSessionRepository sessions;
    private final EnrollmentClient enrollment;
    private final ZoneId zone;

    public ScheduleService(TermRepository terms, ClassSessionRepository sessions, EnrollmentClient enrollment,
                           @Value("${lms.zone:America/Phoenix}") String zone) {
        this.terms = terms;
        this.sessions = sessions;
        this.enrollment = enrollment;
        this.zone = ZoneId.of(zone);
    }

    /** A slot together with its course details from enrollment (course may be null if unavailable). */
    private record Slot(ClassSession s, Course c) {
        String subject() { return c != null && c.name() != null ? c.name() : s.getCourseId(); }
        String gradeLevel() { return c == null ? null : c.gradeLevel(); }
        String color() { return c != null && c.color() != null ? c.color() : "blue"; }
        String teacherUsername() { return c == null ? null : c.teacherUsername(); }
        String teacher() {
            if (c == null) return null;
            return c.teacher() != null ? c.teacher() : c.teacherUsername();
        }
    }

    // ================= timetable views from the API spec =================

    public MonthView month(Caller caller, Integer year, Integer month) {
        LocalDate today = today();
        YearMonth ym;
        try {
            ym = YearMonth.of(year != null ? year : today.getYear(), month != null ? month : today.getMonthValue());
        } catch (DateTimeException e) {
            throw badRequest("month must be 1-12");
        }
        List<Slot> mine = visible(caller);
        List<MonthClass> classes = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate d = ym.atDay(day);
            for (Slot x : mine) {
                if (meets(x.s(), d)) {
                    classes.add(new MonthClass(day, x.s().getCourseId(), x.subject(), x.color(),
                            time(x.s().getStartTime()), time(x.s().getEndTime()), x.gradeLevel()));
                }
            }
        }
        return new MonthView(ym.format(MONTH_YEAR), ym.getYear(), ym.getMonthValue(),
                ym.atDay(1).getDayOfWeek().getValue() % 7,          // 0 = Sunday
                ym.lengthOfMonth(),
                ym.equals(YearMonth.from(today)) ? today.getDayOfMonth() : null,
                classes);
    }

    public TermView term(Caller caller) {
        Term t = currentTerm(today());
        long span = Math.max(1, ChronoUnit.DAYS.between(t.getStartDate(), t.getEndDate()));

        List<String> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(t.getStartDate()); !m.isAfter(YearMonth.from(t.getEndDate())); m = m.plusMonths(1)) {
            months.add(m.format(MONTH));
        }

        List<TermBar> bars = visible(caller).stream()
                .filter(x -> x.s().getTerm().getId().equals(t.getId()))
                .map(x -> {
                    LocalDate from = max(x.s().effectiveStart(), t.getStartDate());
                    LocalDate to = min(x.s().effectiveEnd(), t.getEndDate());
                    return new TermBar(x.s().getCourseId(), x.subject(), x.color(), range(from, to),
                            pct(t.getStartDate(), from, span), pct(t.getStartDate(), to, span));
                })
                .toList();
        return new TermView(termInfo(t), months, bars);
    }

    /** date is optional (defaults to today); handy for demos on weekends. */
    public List<TodayClass> today(Caller caller, LocalDate date) {
        LocalDate d = date != null ? date : today();
        return visible(caller).stream()
                .filter(x -> meets(x.s(), d))
                .map(x -> new TodayClass(time(x.s().getStartTime()), time(x.s().getEndTime()), x.subject(),
                        x.gradeLevel(), x.gradeLevel(), x.color(), x.teacher(), x.teacherUsername(),
                        x.s().getCourseId()))
                .toList();
    }

    /** date is optional (defaults to today); any day inside the week you want. */
    public WeekView week(Caller caller, LocalDate date) {
        LocalDate ref = date != null ? date : today();
        LocalDate monday = ref.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        List<Slot> mine = visible(caller);

        List<WeekDay> days = new ArrayList<>();
        for (LocalDate d = monday; !d.isAfter(sunday); d = d.plusDays(1)) {
            days.add(new WeekDay(dow(d), d.format(SHORT)));
        }

        List<Slot> thisWeek = mine.stream()
                .filter(x -> monday.datesUntil(sunday.plusDays(1)).anyMatch(d -> meets(x.s(), d)))
                .toList();
        List<WeekBlock> blocks = thisWeek.stream()
                .map(x -> new WeekBlock(x.s().getCourseId(), x.subject(), x.gradeLevel(), x.color(), x.teacher(),
                        x.teacherUsername(), List.copyOf(x.s().getDays()),
                        time(x.s().getStartTime()), time(x.s().getEndTime())))
                .toList();

        int firstHour = thisWeek.stream().mapToInt(x -> x.s().getStartTime().getHour()).min().orElse(8);
        int lastHour = thisWeek.stream()
                .mapToInt(x -> x.s().getEndTime().getHour() + (x.s().getEndTime().getMinute() > 0 ? 1 : 0))
                .max().orElse(16);
        List<String> hours = new ArrayList<>();
        for (int h = firstHour; h <= lastHour; h++) hours.add(time(LocalTime.of(h, 0)));

        YearMonth ym = YearMonth.from(ref);
        List<Integer> dotDays = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate d = ym.atDay(day);
            if (mine.stream().anyMatch(x -> meets(x.s(), d))) dotDays.add(day);
        }

        Term t = findTerm(ref).orElse(null);
        return new WeekView(t == null ? null : termInfo(t), weekLabel(monday, sunday), days, hours, blocks,
                new MiniCalendar(ym.format(MONTH_YEAR), ref.getDayOfMonth(), ref.getDayOfMonth(), dotDays),
                upcoming(mine, ref, 4));
    }

    // ================= slots (reference data) and the admin sync used by enrollment =================

    /** Every slot, for every role: enrollment-service reads this to add times to its catalogue. */
    public List<SessionView> listSessions() {
        return sessions.findAllByOrderByStartTimeAsc().stream().map(SessionView::of).toList();
    }

    public SessionView getSession(Long id) {
        return SessionView.of(sessions.findById(id).orElseThrow(() -> notFound("Unknown session " + id)));
    }

    @Transactional
    public SessionView upsertByCourse(Caller caller, String courseId, SessionRequest req) {
        requireAdmin(caller);
        ClassSession s = sessions.findFirstByCourseId(courseId).orElseGet(ClassSession::new);
        apply(s, courseId, req);
        return SessionView.of(sessions.save(s));
    }

    @Transactional
    public void deleteByCourse(Caller caller, String courseId) {
        requireAdmin(caller);
        List<ClassSession> found = sessions.findByCourseId(courseId);
        if (found.isEmpty()) throw notFound("No session for course " + courseId);
        sessions.deleteAll(found);
    }

    @Transactional
    public SessionView createSession(Caller caller, SessionRequest req) {
        requireAdmin(caller);
        if (req.courseId() == null || req.courseId().isBlank()) throw badRequest("courseId is required");
        if (sessions.findFirstByCourseId(req.courseId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course " + req.courseId() + " already has a slot");
        }
        ClassSession s = new ClassSession();
        apply(s, req.courseId(), req);
        return SessionView.of(sessions.save(s));
    }

    @Transactional
    public SessionView updateSession(Caller caller, Long id, SessionRequest req) {
        requireAdmin(caller);
        ClassSession s = sessions.findById(id).orElseThrow(() -> notFound("Unknown session " + id));
        apply(s, s.getCourseId(), req);   // a slot keeps its course; move a course with PUT /by-course
        return SessionView.of(sessions.save(s));
    }

    @Transactional
    public void deleteSession(Caller caller, Long id) {
        requireAdmin(caller);
        if (!sessions.existsById(id)) throw notFound("Unknown session " + id);
        sessions.deleteById(id);
    }

    // ================= terms (every other service reads them from here) =================

    public List<Term> listTerms() { return terms.findAllByOrderByStartDateAsc(); }

    public Term getTerm(String id) {
        return terms.findById(id).orElseThrow(() -> notFound("Unknown term " + id));
    }

    @Transactional
    public Term createTerm(Caller caller, Term term) {
        requireAdmin(caller);
        if (terms.existsById(term.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Term " + term.getId() + " already exists");
        }
        return saveTerm(term);
    }

    @Transactional
    public Term updateTerm(Caller caller, String id, Term incoming) {
        requireAdmin(caller);
        Term t = getTerm(id);
        t.setName(incoming.getName());
        t.setStartDate(incoming.getStartDate());
        t.setEndDate(incoming.getEndDate());
        t.setCurrent(incoming.isCurrent());
        t.setMaxCredits(incoming.getMaxCredits());
        t.setQuarter(incoming.getQuarter());
        t.setAcademicYear(incoming.getAcademicYear());
        return saveTerm(t);
    }

    @Transactional
    public void deleteTerm(Caller caller, String id) {
        requireAdmin(caller);
        terms.delete(getTerm(id));   // the database cascades to its slots
    }

    // ================= helpers =================

    private LocalDate today() { return LocalDate.now(zone); }

    /** The caller's slots, each paired with its course details from enrollment. */
    private List<Slot> visible(Caller caller) {
        Map<String, Course> catalog = enrollment.courses();
        List<Slot> all = sessions.findAllByOrderByStartTimeAsc().stream()
                .map(s -> new Slot(s, catalog.get(s.getCourseId())))
                .toList();
        if (caller.isAdmin()) return all;
        if ("teacher".equals(caller.role())) {
            if (catalog.isEmpty()) return all;   // enrollment unavailable: cannot tell who teaches what
            return all.stream().filter(x -> caller.username().equals(x.teacherUsername())).toList();
        }
        return enrollment.enrolledCourseIds(caller.username())
                .map(ids -> all.stream().filter(x -> ids.contains(x.s().getCourseId())).toList())
                .orElse(all);
    }

    static boolean meets(ClassSession s, LocalDate d) {
        return !d.isBefore(s.effectiveStart()) && !d.isAfter(s.effectiveEnd()) && s.getDays().contains(dow(d));
    }

    private Optional<Term> findTerm(LocalDate day) {
        return terms.findAllByOrderByStartDateAsc().stream().filter(t -> t.contains(day)).findFirst()
                .or(terms::findFirstByCurrentTrue);
    }

    private Term currentTerm(LocalDate day) {
        return findTerm(day).orElseThrow(() -> notFound("No current term"));
    }

    private List<UpcomingClass> upcoming(List<Slot> mine, LocalDate from, int limit) {
        LocalDate realToday = today();
        LocalTime now = LocalTime.now(zone);
        List<UpcomingClass> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(from.plusDays(60)) && result.size() < limit; d = d.plusDays(1)) {
            for (Slot x : mine) {
                if (result.size() == limit) break;
                if (!meets(x.s(), d)) continue;
                if (d.equals(realToday) && !x.s().getStartTime().isAfter(now)) continue;   // already started
                result.add(new UpcomingClass(x.subject(), d.format(WHEN),
                        timeRange(x.s().getStartTime(), x.s().getEndTime()), x.gradeLevel(), x.teacher(),
                        x.teacherUsername(), x.s().getCourseId(), x.color()));
            }
        }
        return result;
    }

    private void apply(ClassSession s, String courseId, SessionRequest req) {
        Term term = req.termId() == null || req.termId().isBlank()
                ? currentTerm(today())
                : terms.findById(req.termId()).orElseThrow(() -> badRequest("Unknown termId " + req.termId()));

        List<String> days = new ArrayList<>();
        for (String raw : req.days()) {
            String d = raw == null ? "" : raw.trim();
            String match = WEEKDAYS.stream().filter(w -> w.equalsIgnoreCase(d)).findFirst()
                    .orElseThrow(() -> badRequest("days must be Mon-Sun, got '" + raw + "'"));
            if (!days.contains(match)) days.add(match);
        }
        days.sort(Comparator.comparingInt(WEEKDAYS::indexOf));

        LocalTime start = parseTime(req.startTime(), "startTime");
        LocalTime end = req.endTime() == null || req.endTime().isBlank() ? start.plusHours(1) : parseTime(req.endTime(), "endTime");
        if (!end.equals(start.plusHours(1))) throw badRequest("endTime must be startTime + 1 hour");
        if (req.startDate() != null && req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw badRequest("endDate is before startDate");
        }

        s.setTerm(term);
        s.setCourseId(courseId);
        s.setDays(days);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setStartDate(req.startDate());
        s.setEndDate(req.endDate());
    }

    private Term saveTerm(Term t) {
        if (t.getEndDate().isBefore(t.getStartDate())) throw badRequest("endDate is before startDate");
        if (t.isCurrent()) {   // only one current term at a time
            terms.findAll().stream().filter(o -> !o.getId().equals(t.getId())).forEach(o -> o.setCurrent(false));
        }
        return terms.save(t);
    }

    private static LocalTime parseTime(String value, String field) {
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException | NullPointerException e) {
            throw badRequest(field + " must look like 08:00");
        }
    }

    static String dow(LocalDate d) { return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US); }

    static String time(LocalTime t) { return t.format(TIME); }

    /** "8:00 - 9:00 AM", or "11:00 AM - 12:00 PM" when it crosses noon */
    static String timeRange(LocalTime start, LocalTime end) {
        boolean sameHalf = (start.getHour() < 12) == (end.getHour() < 12);
        return (sameHalf ? start.format(TIME_NO_AMPM) : time(start)) + " - " + time(end);
    }

    /** "Aug 24 - Oct 23, 2026" */
    static String range(LocalDate from, LocalDate to) {
        return from.getYear() == to.getYear()
                ? from.format(SHORT) + " - " + to.format(LONG)
                : from.format(LONG) + " - " + to.format(LONG);
    }

    /** "Sep 21 - 27, 2026" or "Sep 28 - Oct 4, 2026" */
    static String weekLabel(LocalDate monday, LocalDate sunday) {
        if (monday.getYear() != sunday.getYear()) return monday.format(LONG) + " - " + sunday.format(LONG);
        if (monday.getMonth() == sunday.getMonth()) {
            return monday.format(SHORT) + " - " + sunday.getDayOfMonth() + ", " + sunday.getYear();
        }
        return monday.format(SHORT) + " - " + sunday.format(LONG);
    }

    private static TermInfo termInfo(Term t) {
        return new TermInfo(t.getStartDate().format(LONG), t.getEndDate().format(LONG), t.getName());
    }

    private static int pct(LocalDate termStart, LocalDate d, long span) {
        return (int) Math.round(100.0 * ChronoUnit.DAYS.between(termStart, d) / span);
    }

    private static LocalDate max(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }
    private static LocalDate min(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }

    private static void requireAdmin(Caller caller) {
        if (!caller.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin only");
    }

    private static ResponseStatusException notFound(String msg) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
    }

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
