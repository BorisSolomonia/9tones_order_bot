package ge.orderapp.service;

import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.request.CreateUserRequest;
import ge.orderapp.dto.request.UpdateUserRequest;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.BadRequestException;
import ge.orderapp.exception.NotFoundException;
import ge.orderapp.repository.SheetsClient;
import ge.orderapp.security.PasswordService;
import ge.orderapp.security.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final Set<String> VALID_ROLES = Set.of("MANAGER", "ACCOUNTANT", "ADMIN");

    private final InMemoryStore store;
    private final PasswordService passwordService;
    private final SessionManager sessionManager;

    @Autowired(required = false)
    private SheetsClient sheetsClient;

    public UserService(InMemoryStore store, PasswordService passwordService, SessionManager sessionManager) {
        this.store = store;
        this.passwordService = passwordService;
        this.sessionManager = sessionManager;
    }

    public List<UserDto> getAllUsers() {
        return store.getAllUsers();
    }

    public UserDto createUser(CreateUserRequest request) {
        if (!VALID_ROLES.contains(request.role().toUpperCase())) {
            throw new BadRequestException("Invalid role: " + request.role());
        }

        UserDto existing = store.getUserByUsername(request.username());
        if (existing != null) {
            throw new BadRequestException("Username already taken: " + request.username());
        }

        String now = Instant.now().toString();
        String userId = UUID.randomUUID().toString();
        String passwordHash = passwordService.hash(request.password());

        UserDto user = new UserDto(
                userId, request.username().trim(),
                request.displayName().trim(),
                request.role().toUpperCase(),
                true, now);

        store.putUser(user, passwordHash);

        if (sheetsClient != null) {
            sheetsClient.appendRow("Users", List.of(
                    user.userId(), user.username(), passwordHash,
                    user.displayName(), user.role(),
                    "TRUE", user.createdAt()));
        }

        log.info("User created: {} ({})", user.username(), user.role());
        return user;
    }

    public UserDto updateUser(String userId, UpdateUserRequest request) {
        UserDto existing = store.getUser(userId);
        if (existing == null) throw new NotFoundException("User not found: " + userId);

        if (request.role() != null && !VALID_ROLES.contains(request.role().toUpperCase())) {
            throw new BadRequestException("Invalid role: " + request.role());
        }

        UserDto updated = new UserDto(
                existing.userId(),
                existing.username(),
                request.displayName() != null ? request.displayName().trim() : existing.displayName(),
                request.role() != null ? request.role().toUpperCase() : existing.role(),
                request.active() != null ? request.active() : existing.active(),
                existing.createdAt());

        store.putUser(updated, null);

        if (sheetsClient != null) {
            int rowIndex = sheetsClient.findRowIndex("Users", userId);
            if (rowIndex > 0) {
                String passwordHash = store.getUserPasswordHash(userId);
                sheetsClient.updateRow("Users", rowIndex, List.of(
                        updated.userId(), updated.username(), passwordHash != null ? passwordHash : "",
                        updated.displayName(), updated.role(),
                        updated.active() ? "TRUE" : "FALSE",
                        updated.createdAt()));
            }
        }

        // If deactivated, remove sessions
        if (!updated.active()) {
            sessionManager.removeAllSessionsForUser(userId);
        }

        return updated;
    }

    public void changePassword(String userId, String newPassword) {
        UserDto existing = store.getUser(userId);
        if (existing == null) throw new NotFoundException("User not found: " + userId);

        String passwordHash = passwordService.hash(newPassword);
        store.putUser(existing, passwordHash);

        if (sheetsClient != null) {
            int rowIndex = sheetsClient.findRowIndex("Users", userId);
            if (rowIndex > 0) {
                sheetsClient.updateRow("Users", rowIndex, List.of(
                        existing.userId(), existing.username(), passwordHash,
                        existing.displayName(), existing.role(),
                        existing.active() ? "TRUE" : "FALSE",
                        existing.createdAt()));
            }
        }

        // Invalidate sessions to force re-login
        sessionManager.removeAllSessionsForUser(userId);
        log.info("Password changed for user: {}", existing.username());
    }

    public void deleteUser(String userId) {
        updateUser(userId, new UpdateUserRequest(null, null, false));
    }
}
