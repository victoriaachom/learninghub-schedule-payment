package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


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
    public record EnrollmentState(List<String> enrolled) {}
}
