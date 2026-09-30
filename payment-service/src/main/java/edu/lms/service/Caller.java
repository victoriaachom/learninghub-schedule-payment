package edu.lms.service;


public record Caller(String username, String role) {
    public boolean isAdmin() { return "admin".equalsIgnoreCase(role); }
    public boolean canSee(String owner) { return isAdmin() || username.equals(owner); }
}
