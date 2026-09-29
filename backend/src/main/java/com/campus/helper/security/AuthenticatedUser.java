package com.campus.helper.security;

public record AuthenticatedUser(Long userId, String role, String displayName) {
}
