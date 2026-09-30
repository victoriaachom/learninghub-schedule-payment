package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;


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

    public List<EnrolledClass> enrolledClasses(String username) {
        try {
            MyClasses response = rest.get()
                    .uri("/api/enrollment/my-classes")
                    .header("X-User", username)
                    .header("X-Role", "student")
                    .retrieve()
                    .body(MyClasses.class);
            return response == null || response.classes() == null ? List.of() : response.classes();
        } catch (RestClientException e) {
            log.warn("enrollment-service unavailable, using single tuition line: {}", e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MyClasses(List<EnrolledClass> classes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnrolledClass(String id, String name, String gradeLevel, Integer credits) {}
}
