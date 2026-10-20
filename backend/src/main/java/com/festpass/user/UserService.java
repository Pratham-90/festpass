package com.festpass.user;

import com.festpass.common.ApiException;
import com.festpass.common.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Creates a user. Public registration always passes STUDENT; other roles are given by an admin. */
    @Transactional
    public User create(String name, String email, String rawPassword, Role role) {
        String normalized = normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
        try {
            return users.saveAndFlush(new User(
                    name.trim(), normalized, passwordEncoder.encode(rawPassword), role, Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same email raced past the check above.
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<UserView> list(String query) {
        List<User> found = (query == null || query.isBlank())
                ? users.findAllByOrderByCreatedAtDesc()
                : users.search(query.trim());
        return found.stream().map(UserView::of).toList();
    }

    @Transactional
    public UserView changeRole(Long userId, Role role, Long actingAdminId) {
        if (userId.equals(actingAdminId)) {
            throw ApiException.invalidState("You cannot change your own role");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        user.setRole(role);
        return UserView.of(user);
    }
}
