package ge.orderapp.controller;

import ge.orderapp.dto.request.ChangePasswordRequest;
import ge.orderapp.dto.request.CreateUserRequest;
import ge.orderapp.dto.request.UpdateUserRequest;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.ForbiddenException;
import ge.orderapp.security.SessionAuthFilter;
import ge.orderapp.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> list(HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest req,
                                           HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(userService.createUser(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable String id,
                                           @Valid @RequestBody UpdateUserRequest req,
                                           HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(userService.updateUser(id, req));
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable String id,
                                                 @Valid @RequestBody ChangePasswordRequest req,
                                                 HttpServletRequest request) {
        requireAdmin(request);
        userService.changePassword(id, req.password());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        requireAdmin(request);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        if (!"ADMIN".equals(user.role())) {
            throw new ForbiddenException("Admin role required");
        }
    }
}
