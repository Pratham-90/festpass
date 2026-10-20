package com.festpass.auth;

import com.festpass.common.ApiException;
import com.festpass.common.ErrorCode;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Register, log in and "who am I". Logout is handled by Spring Security at POST /api/auth/logout. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionStrategy;

    public AuthController(UserService userService, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy loginSessionStrategy) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionStrategy = loginSessionStrategy;
    }

    /** Creates a STUDENT account and logs it in straight away. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse register(@Valid @RequestBody RegisterRequest body,
                               HttpServletRequest request, HttpServletResponse response) {
        User user = userService.create(body.name(), body.email(), body.password(), Role.STUDENT);
        return logIn(user.getEmail(), body.password(), request, response);
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest body,
                            HttpServletRequest request, HttpServletResponse response) {
        return logIn(body.email(), body.password(), request, response);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal me) {
        return MeResponse.of(me);
    }

    private MeResponse logIn(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException e) {
            throw new ApiException(ErrorCode.BAD_CREDENTIALS, "Email or password is incorrect");
        }
        // New session id and CSRF token on login (prevents fixation), then store the login in the session.
        sessionStrategy.onAuthentication(auth, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return MeResponse.of((AppUserPrincipal) auth.getPrincipal());
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record MeResponse(Long id, String name, String email, Role role) {
        static MeResponse of(AppUserPrincipal p) {
            return new MeResponse(p.id(), p.name(), p.email(), p.role());
        }
    }
}
