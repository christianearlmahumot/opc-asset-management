package edu.citu.opc.fams.access;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        var r = authService.login(req.username(), req.password());
        return new LoginResponse(r.token(), Me.of(r.user()));
    }

    /** Lets the front end restore the session from a stored token. */
    @GetMapping("/me")
    public Me me() {
        CurrentUser cu = CurrentUser.get();
        return new Me(cu.id(), cu.username(), cu.fullName(), cu.role(), cu.role().label(), cu.departmentId(),
                cu.mustChangePassword());
    }

    /** Any signed-in user may change their own password. It is required after an admin sets a temporary one. */
    @PostMapping("/change-password")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        var r = authService.changeOwnPassword(req.currentPassword(), req.newPassword());
        return new LoginResponse(r.token(), Me.of(r.user()));
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your username.") @Size(max = 50) String username,
            @NotBlank(message = "Enter your password.") @Size(max = 100) String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Enter your current password.") @Size(max = 100) String currentPassword,
            @NotBlank(message = "Enter a new password.") @Size(max = 100) String newPassword) {
    }

    public record Me(Long id, String username, String fullName, Role role, String roleLabel, Long departmentId,
                     boolean mustChangePassword) {
        static Me of(AppUser u) {
            return new Me(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.getRole().label(),
                    u.getDepartmentId(), u.isMustChangePassword());
        }
    }

    public record LoginResponse(String token, Me user) {
    }
}
