package com.festpass.auth;

import com.festpass.user.UserRepository;
import com.festpass.user.UserService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(UserService.normalizeEmail(email))
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with that email"));
    }
}
