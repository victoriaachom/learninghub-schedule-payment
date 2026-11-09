package edu.lms.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service registry (Netflix Eureka). Every service registers here under its
 * spring.application.name; the gateway and payment-service look instances up
 * by that name (lb://schedule-service) instead of using fixed host:port URLs.
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
