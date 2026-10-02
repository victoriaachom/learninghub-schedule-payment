package edu.lms.service;

/** Who is making the request. Built by CallerResolver. */
public record Caller(String username, String role) {
    public boolean isAdmin() { return "admin".equalsIgnoreCase(role); }
    public boolean canSee(String owner) { return isAdmin() || username.equals(owner); }
}
