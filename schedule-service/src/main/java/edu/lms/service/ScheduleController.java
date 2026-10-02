package edu.lms.service;

import edu.lms.service.ScheduleDtos.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;


@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService service;
    private final CallerResolver callers;

    public ScheduleController(ScheduleService service, CallerResolver callers) {
        this.service = service;
        this.callers = callers;
    }

    // ---------- timetable views from the team API spec ----------

    @GetMapping("/month")
    public MonthView month(@RequestParam(required = false) Integer year,
                           @RequestParam(required = false) Integer month) {
        return service.month(callers.current(), year, month);
    }

    @GetMapping("/term")
    public TermView term() { return service.term(callers.current()); }

    /** Optional ?date=2026-10-02 lets you show a weekday's classes on a weekend. */
    @GetMapping("/today")
    public List<TodayClass> today(@RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.today(callers.current(), date);
    }

    /** Optional ?date=2026-10-05 picks the week containing that day. */
    @GetMapping("/week")
    public WeekView week(@RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.week(callers.current(), date);
    }

    // ---------- session sync called by enrollment-service (admin) ----------

    @PutMapping("/sessions/by-course/{courseId}")
    public SessionView upsertByCourse(@PathVariable String courseId, @Valid @RequestBody SessionRequest request) {
        return service.upsertByCourse(callers.current(), courseId, request);
    }

    @DeleteMapping("/sessions/by-course/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteByCourse(@PathVariable String courseId) {
        service.deleteByCourse(callers.current(), courseId);
    }

    // ---------- class-session slots: reference data for every role; writes are admin only ----------

    @GetMapping("/sessions")
    public List<SessionView> listSessions() { return service.listSessions(); }

    @GetMapping("/sessions/{id}")
    public SessionView getSession(@PathVariable Long id) { return service.getSession(id); }

    @PostMapping("/sessions")
    public ResponseEntity<SessionView> createSession(@Valid @RequestBody SessionRequest request) {
        SessionView saved = service.createSession(callers.current(), request);
        return ResponseEntity.created(URI.create("/api/schedule/sessions/" + saved.id())).body(saved);
    }

    @PutMapping("/sessions/{id}")
    public SessionView updateSession(@PathVariable Long id, @Valid @RequestBody SessionRequest request) {
        return service.updateSession(callers.current(), id, request);
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable Long id) { service.deleteSession(callers.current(), id); }

    // ---------- terms: every other service reads them here; writes are admin only ----------

    @GetMapping("/terms")
    public List<Term> listTerms() { return service.listTerms(); }

    @GetMapping("/terms/{id}")
    public Term getTerm(@PathVariable String id) { return service.getTerm(id); }

    @PostMapping("/terms")
    public ResponseEntity<Term> createTerm(@Valid @RequestBody Term term) {
        Term saved = service.createTerm(callers.current(), term);
        return ResponseEntity.created(URI.create("/api/schedule/terms/" + saved.getId())).body(saved);
    }

    @PutMapping("/terms/{id}")
    public Term updateTerm(@PathVariable String id, @Valid @RequestBody Term term) {
        return service.updateTerm(callers.current(), id, term);
    }

    @DeleteMapping("/terms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTerm(@PathVariable String id) { service.deleteTerm(callers.current(), id); }

    // ---------- profile demo ----------

    @GetMapping("/whoami")
    public String whoami(@Value("${lms.environment-label}") String label) { return label; }
}
