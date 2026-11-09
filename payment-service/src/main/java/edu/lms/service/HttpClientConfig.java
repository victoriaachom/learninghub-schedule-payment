package edu.lms.service;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * A RestClient.Builder whose clients resolve service names through Eureka:
 * http://schedule-service/... is sent to a live schedule-service instance,
 * load-balanced if several are running.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
