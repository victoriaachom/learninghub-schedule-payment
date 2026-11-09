package edu.lms.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Authentication: every /api call needs a valid Keycloak access token (OAuth2
 * resource server; the key set and issuer are in application.yml).
 * Authorization: Keycloak realm roles (realm_access.roles) become ROLE_admin,
 * ROLE_teacher, ROLE_student; the rules below are checked before any controller
 * runs, and the service layer adds owner checks (e.g. "not your invoice").
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())                 // stateless API: no browser session to protect
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/error", "/api/schedule/whoami").permitAll()
                        // Writes to the timetable and terms are admin-only
                        .requestMatchers(HttpMethod.POST, "/api/schedule/sessions/**", "/api/schedule/terms/**").hasRole("admin")
                        .requestMatchers(HttpMethod.PUT, "/api/schedule/sessions/**", "/api/schedule/terms/**").hasRole("admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/schedule/sessions/**", "/api/schedule/terms/**").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakRoles())));
        return http.build();
    }

    /** preferred_username is the principal; realm_access.roles become ROLE_* authorities. */
    private JwtAuthenticationConverter keycloakRoles() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(new RealmRoles());
        return converter;
    }

    static class RealmRoles implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
        }
    }
}
