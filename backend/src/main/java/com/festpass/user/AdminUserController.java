package com.festpass.user;

import com.festpass.auth.AppUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User management. Only ADMIN can reach /api/admin/** (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserView> list(@RequestParam(required = false) String q) {
        return userService.list(q);
    }

    @PutMapping("/{id}/role")
    public UserView changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request,
                               @AuthenticationPrincipal AppUserPrincipal admin) {
        return userService.changeRole(id, request.role(), admin.id());
    }

    public record ChangeRoleRequest(@NotNull Role role) {}
}
