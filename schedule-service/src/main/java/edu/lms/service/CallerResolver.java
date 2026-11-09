package edu.lms.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who is calling, read from the Keycloak access token that Spring Security has
 * already validated (signature, expiry, issuer):
 *   username = the preferred_username claim
 *   role     = the strongest realm role in realm_access.roles (admin > teacher > student)
 *
 * In Phase 1 this read X-User / X-Role headers; that stand-in is gone, so a
 * caller can no longer claim to be someone else.
 */
@Component
public class CallerResolver {

    public Caller current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A bearer token is required");
        }
        Set<String> roles = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
        String role = roles.contains("admin") ? "admin" : roles.contains("teacher") ? "teacher" : "student";
        return new Caller(token.getToken().getClaimAsString("preferred_username"), role);
    }
}
