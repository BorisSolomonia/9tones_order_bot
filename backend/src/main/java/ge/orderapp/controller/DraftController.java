package ge.orderapp.controller;

import ge.orderapp.dto.request.CopyDraftRequest;
import ge.orderapp.dto.request.CreateDraftRequest;
import ge.orderapp.dto.response.DraftDto;
import ge.orderapp.dto.response.UserDto;
import ge.orderapp.exception.ForbiddenException;
import ge.orderapp.security.SessionAuthFilter;
import ge.orderapp.service.DraftService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drafts")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    @GetMapping
    public ResponseEntity<List<DraftDto>> list(HttpServletRequest request) {
        UserDto user = requireManager(request);
        return ResponseEntity.ok(draftService.listDrafts(user.userId()));
    }

    @GetMapping("/suggest")
    public ResponseEntity<DraftDto> suggest(HttpServletRequest request) {
        UserDto user = requireManager(request);
        DraftDto draft = draftService.suggestDraft(user.userId());
        return draft != null ? ResponseEntity.ok(draft) : ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<DraftDto> create(@Valid @RequestBody CreateDraftRequest req,
                                            HttpServletRequest request) {
        UserDto user = requireManager(request);
        return ResponseEntity.ok(draftService.createDraft(req, user.userId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DraftDto> update(@PathVariable String id,
                                            @Valid @RequestBody CreateDraftRequest req,
                                            HttpServletRequest request) {
        UserDto user = requireManager(request);
        return ResponseEntity.ok(draftService.updateDraft(id, req, user.userId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest request) {
        UserDto user = requireManager(request);
        draftService.deleteDraft(id, user.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/load")
    public ResponseEntity<DraftDto> load(@PathVariable String id, HttpServletRequest request) {
        UserDto user = requireManager(request);
        return ResponseEntity.ok(draftService.loadDraft(id, user.userId()));
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<DraftDto> copy(@PathVariable String id,
                                          @Valid @RequestBody CopyDraftRequest req,
                                          HttpServletRequest request) {
        UserDto user = requireManager(request);
        return ResponseEntity.ok(draftService.copyDraft(id, req, user.userId()));
    }

    private UserDto requireManager(HttpServletRequest request) {
        UserDto user = SessionAuthFilter.getCurrentUser(request);
        if (!"MANAGER".equals(user.role())) {
            throw new ForbiddenException("Only managers can manage drafts");
        }
        return user;
    }
}
