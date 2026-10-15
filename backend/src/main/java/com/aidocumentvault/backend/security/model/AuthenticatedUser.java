package com.aidocumentvault.backend.security.model;

import java.util.UUID;

public record AuthenticatedUser(
    UUID userId,
    String email
) {
}
