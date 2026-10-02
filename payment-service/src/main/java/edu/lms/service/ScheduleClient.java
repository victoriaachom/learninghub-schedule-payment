package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


@Component
public class ScheduleClient {

    private static final Logger log = LoggerFactory.getLogger(ScheduleClient.class);

    private final RestClient rest;

    public ScheduleClient(@Value("${lms.schedule-url:http://schedule-service:8103}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(2000);
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** The term containing today, else the one flagged current; empty if schedule-service is unavailable. */
    public Optional<TermRef> currentTerm(LocalDate today) {
        try {
            List<TermRef> all = rest.get()
                    .uri("/api/schedule/terms")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TermRef>>() {});
            if (all == null) return Optional.empty();
            return all.stream()
                    .filter(t -> t.startDate() != null && t.endDate() != null
                            && !today.isBefore(t.startDate()) && !today.isAfter(t.endDate()))
                    .min(Comparator.comparing(TermRef::startDate))
                    .or(() -> all.stream().filter(TermRef::current).findFirst());
        } catch (RestClientException e) {
            log.warn("schedule-service unavailable, cannot raise this term's invoice: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TermRef(String id, String name, LocalDate startDate, LocalDate endDate,
                          @JsonProperty("isCurrent") boolean current) {}
}
