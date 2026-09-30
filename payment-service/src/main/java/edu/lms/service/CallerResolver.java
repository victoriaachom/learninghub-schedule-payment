package edu.lms.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Component
public class CallerResolver {

    private final String defaultUser;
    private final String defaultRole;

    public CallerResolver(@Value("${lms.dev-user:sstudent}") String defaultUser,
                          @Value("${lms.dev-role:student}") String defaultRole) {
        this.defaultUser = defaultUser;
        this.defaultRole = defaultRole;
    }

    public Caller current() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String user = request.getHeader("X-User");
        String role = request.getHeader("X-Role");
        return new Caller(
                user == null || user.isBlank() ? defaultUser : user.trim(),
                role == null || role.isBlank() ? defaultRole : role.trim().toLowerCase());
    }
}
