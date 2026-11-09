package edu.lms.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point for LearningHub (Spring Cloud Gateway).
 * Routes by path and resolves each service through Eureka (lb://<service>),
 * so clients only ever talk to http://localhost:8080. Routes are in application.yml.
 */
@SpringBootApplication
public class GatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
