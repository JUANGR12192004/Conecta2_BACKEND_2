package com.example.worker_registry.securtity;

public record AuthenticatedUser(Long userId, String role) {
    public boolean hasRole(String expected) {
        return expected != null && expected.equalsIgnoreCase(role);
    }
}
