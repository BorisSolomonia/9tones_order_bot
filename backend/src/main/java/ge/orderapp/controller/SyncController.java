package ge.orderapp.controller;

import ge.orderapp.dto.request.TriggerSyncRequest;
import ge.orderapp.dto.response.SyncStateDto;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.ForbiddenException;
import ge.orderapp.security.SessionAuthFilter;
import ge.orderapp.service.SyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/trigger")
    public ResponseEntity<SyncStateDto> trigger(@Valid @RequestBody TriggerSyncRequest req,
                                                  HttpServletRequest request) {
        UserDto user = requireAdmin(request);
        log.info("Manual sync trigger requested: userId={}, username={}, type={}, date={}",
                user.userId(), user.username(), req.type(), req.date());
        return ResponseEntity.ok(syncService.triggerSync(req.type(), req.date()));
    }

    @GetMapping("/status")
    public ResponseEntity<SyncStateDto> status(HttpServletRequest request) {
        UserDto user = requireAdmin(request);
        SyncStateDto state = syncService.getLatestStatus();
        if (state == null) {
            log.info("Sync status requested: userId={}, username={}, state=NONE", user.userId(), user.username());
        } else {
            log.info("Sync status requested: userId={}, username={}, syncId={}, status={}, found={}, added={}, error={}",
                    user.userId(), user.username(), state.syncId(), state.status(),
                    state.customersFound(), state.customersAdded(), state.errorMessage());
            if (state.syncId().isBlank() || state.status().isBlank()) {
                log.warn("Sync status contains invalid state row: syncId='{}', status='{}'", state.syncId(), state.status());
            }
        }
        return state != null ? ResponseEntity.ok(state) : ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<List<SyncStateDto>> history(
            @RequestParam(defaultValue = "10") int limit,
            HttpServletRequest request) {
        requireAdmin(request);
        return ResponseEntity.ok(syncService.getHistory(limit));
    }

    private UserDto requireAdmin(HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        if (!"ADMIN".equals(user.role())) {
            throw new ForbiddenException("Admin role required");
        }
        return user;
    }
}
