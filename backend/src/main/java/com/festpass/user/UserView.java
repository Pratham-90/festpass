package com.festpass.user;

import java.time.Instant;

public record UserView(Long id, String name, String email, Role role, Instant createdAt) {

    public static UserView of(User user) {
        return new UserView(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
