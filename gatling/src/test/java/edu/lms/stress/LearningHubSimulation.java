package edu.lms.stress;

import io.gatling.javaapi.core.FeederBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Stress test for schedule-service and payment-service, entirely through the gateway.
 *
 * Each virtual user is a student who:
 *   1. logs in to Keycloak (password grant) through the gateway and keeps the access token,
 *   2. for DURATION_SECONDS, repeatedly opens the week timetable, today's classes and the
 *      payment summary, sending "Authorization: Bearer <token>" every time.
 *
 * Every summary call also makes payment-service call schedule-service (found through
 * Eureka) with the forwarded token, so the test loads the service-to-service path too.
 *
 * Settings (environment variables): BASE_URL, USERS, RAMP_SECONDS, DURATION_SECONDS.
 */
public class LearningHubSimulation extends Simulation {

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private final String baseUrl = env("BASE_URL", "http://localhost:8090");
    private final int users = Integer.parseInt(env("USERS", "50"));
    private final int rampSeconds = Integer.parseInt(env("RAMP_SECONDS", "20"));
    private final int durationSeconds = Integer.parseInt(env("DURATION_SECONDS", "60"));

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .userAgentHeader("Gatling - LearningHub stress test");

    // Seeded students who already have this term's invoice (so the summary never has to raise one).
    private final FeederBuilder<Object> students = listFeeder(List.of(
            Map.<String, Object>of("username", "student1"),
            Map.<String, Object>of("username", "jsmith"),
            Map.<String, Object>of("username", "mgarcia")
    )).circular();

    private final ScenarioBuilder student = scenario("Student browsing schedule and payments")
            .feed(students)
            .exec(http("Keycloak login (through gateway)")
                    .post("/auth/realms/learninghub/protocol/openid-connect/token")
                    .formParam("grant_type", "password")
                    .formParam("client_id", "learninghub-web")
                    .formParam("username", "#{username}")
                    .formParam("password", "password")
                    .check(status().is(200), jsonPath("$.access_token").saveAs("token")))
            .during(Duration.ofSeconds(durationSeconds)).on(
                    exec(http("GET /api/schedule/week")
                            .get("/api/schedule/week")
                            .header("Authorization", "Bearer #{token}")
                            .check(status().is(200)))
                    .pause(Duration.ofMillis(200), Duration.ofMillis(800))
                    .exec(http("GET /api/schedule/today")
                            .get("/api/schedule/today")
                            .header("Authorization", "Bearer #{token}")
                            .check(status().is(200)))
                    .pause(Duration.ofMillis(200), Duration.ofMillis(800))
                    .exec(http("GET /api/payments/summary")
                            .get("/api/payments/summary")
                            .header("Authorization", "Bearer #{token}")
                            .check(status().is(200)))
                    .pause(Duration.ofMillis(200), Duration.ofMillis(800)));

    {
        setUp(student.injectOpen(rampUsers(users).during(Duration.ofSeconds(rampSeconds))))
                .protocols(httpProtocol)
                .assertions(
                        global().successfulRequests().percent().gt(95.0),   // resilience: errors stay rare
                        global().responseTime().percentile3().lt(2000));    // 95th percentile under 2 s
    }
}
