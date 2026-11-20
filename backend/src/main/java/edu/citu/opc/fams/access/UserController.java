package edu.citu.opc.fams.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/** SDD 6.2: /api/users endpoints, Administrator only. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserView> list() {
        return service.list().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUser req) {
        return UserView.of(service.create(req.fullName(), req.username(), req.temporaryPassword(), req.role(),
                req.departmentId()));
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUser req) {
        return UserView.of(service.update(id, req.fullName(), req.role(), req.departmentId()));
    }

    @PostMapping("/{id}/deactivate")
    public UserView deactivate(@PathVariable Long id) {
        return UserView.of(service.setActive(id, false));
    }

    @PostMapping("/{id}/activate")
    public UserView activate(@PathVariable Long id) {
        return UserView.of(service.setActive(id, true));
    }

    @PostMapping("/{id}/reset-password")
    public UserView resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPassword req) {
        return UserView.of(service.resetPassword(id, req.temporaryPassword()));
    }

    public record CreateUser(
            @NotBlank(message = "Enter the full name.") @Size(max = 100) String fullName,
            @NotBlank(message = "Enter a username.") @Size(max = 50) String username,
            @NotBlank(message = "Enter a temporary password.") @Size(max = 72) String temporaryPassword,
            @NotNull(message = "Choose a role.") Role role,
            Long departmentId) {
    }

    public record UpdateUser(
            @NotBlank(message = "Enter the full name.") @Size(max = 100) String fullName,
            @NotNull(message = "Choose a role.") Role role,
            Long departmentId) {
    }

    public record ResetPassword(
            @NotBlank(message = "Enter a temporary password.") @Size(max = 72) String temporaryPassword) {
    }

    public record UserView(Long id, String fullName, String username, Role role, String roleLabel,
                           Long departmentId, boolean active, boolean mustChangePassword, OffsetDateTime createdAt) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getFullName(), u.getUsername(), u.getRole(), u.getRole().label(),
                    u.getDepartmentId(), u.isActive(), u.isMustChangePassword(), u.getCreatedAt());
        }
    }
}
