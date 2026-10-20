package com.festpass.auth;

import com.festpass.user.Role;
import com.festpass.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user, stored in the HTTP session.
 * Controllers get it with {@code @AuthenticationPrincipal AppUserPrincipal me}.
 * A role change by an admin applies at the user's next login.
 */
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String name;
    private final String email;
    private final Role role;
    private String passwordHash;

    public AppUserPrincipal(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.passwordHash = user.getPasswordHash();
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public Role role() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
