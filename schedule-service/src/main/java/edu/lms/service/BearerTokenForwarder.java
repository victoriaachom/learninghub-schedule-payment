package edu.lms.service;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;

/**
 * Bearer token passed across services: when this service calls another one while
 * handling a request, it attaches the SAME access token the user sent in. The
 * downstream service validates it itself and sees the original user and role.
 */
public class BearerTokenForwarder implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            request.getHeaders().setBearerAuth(token.getToken().getTokenValue());
        }
        return execution.execute(request, body);
    }
}
