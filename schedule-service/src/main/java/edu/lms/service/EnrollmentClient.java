package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;


@Component
public class EnrollmentClient {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentClient.class);

    private final RestClient rest;

    public EnrollmentClient(@Value("${lms.enrollment-url:http://enrollment-service:8104}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(2000);
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** Course catalogue keyed by course id; empty map if enrollment-service is unavailable. */
    public Map<String, Course> courses() {
        try {
            List<Course> list = rest.get()
                    .uri("/api/enrollment/courses")
                    .header("X-User", "schedule-service")
                    .header("X-Role", "admin")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Course>>() {});
            Map<String, Course> byId = new HashMap<>();
            if (list != null) list.forEach(c -> byId.put(c.id(), c));
            return byId;
        } catch (RestClientException e) {
            log.warn("enrollment-service unavailable, showing course ids instead of names: {}", e.getMessage());
            return Map.of();
        }
    }

    public Optional<Set<String>> enrolledCourseIds(String username) {
        try {
            EnrollmentState state = rest.get()
                    .uri("/api/enrollment/state")
                    .header("X-User", username)
                    .header("X-Role", "student")
                    .retrieve()
                    .body(EnrollmentState.class);
            return Optional.of(state == null || state.enrolled() == null ? Set.of() : new HashSet<>(state.enrolled()));
        } catch (RestClientException e) {
            log.warn("enrollment-service unavailable, showing all classes: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Course(String id,
                         String termId,
                         @JsonAlias("subject") String name,
                         String gradeLevel,
                         String teacherUsername,
                         @JsonAlias({"teacherName", "teacherDisplayName"}) String teacher,
                         Integer credits,
                         String color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnrollmentState(List<String> enrolled) {}
}
