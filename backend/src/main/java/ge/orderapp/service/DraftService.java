package ge.orderapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ge.orderapp.cache.InMemoryStore;
import ge.orderapp.dto.request.CopyDraftRequest;
import ge.orderapp.dto.request.CreateDraftRequest;
import ge.orderapp.dto.response.DraftDto;
import ge.orderapp.dto.response.DraftItemDto;
import ge.orderapp.exception.BadRequestException;
import ge.orderapp.exception.ForbiddenException;
import ge.orderapp.exception.NotFoundException;
import ge.orderapp.repository.SheetsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    @Value("${app.drafts.weekday-names:}")
    private String weekdayNamesConfig;

    private final InMemoryStore store;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private SheetsClient sheetsClient;

    public DraftService(InMemoryStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public List<DraftDto> listDrafts(String managerId) {
        return store.getDrafts(managerId);
    }

    public DraftDto suggestDraft(String managerId) {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        String weekday = resolveWeekdayName(today);
        return store.getDraftByName(managerId, weekday);
    }

    public DraftDto createDraft(CreateDraftRequest request, String managerId) {
        String now = Instant.now().toString();
        String draftId = UUID.randomUUID().toString();

        List<DraftItemDto> items = request.items().stream()
                .map(i -> new DraftItemDto(sanitize(i.customerName()), i.customerId(), sanitize(i.comment()), sanitize(i.board())))
                .toList();

        DraftDto draft = new DraftDto(draftId, managerId, sanitize(request.name()), items, now, now);
        store.putDraft(draft);

        if (sheetsClient != null) {
            String itemsJson = serializeItems(items);
            sheetsClient.appendRow("Drafts", List.of(
                    draft.draftId(), draft.managerId(), draft.name(),
                    itemsJson, draft.createdAt(), draft.updatedAt()));
        }

        log.info("Draft created: {} ({})", draft.name(), draft.draftId());
        return draft;
    }

    public DraftDto updateDraft(String draftId, CreateDraftRequest request, String managerId) {
        DraftDto existing = store.getDraft(draftId);
        if (existing == null) throw new NotFoundException("Draft not found: " + draftId);
        if (!existing.managerId().equals(managerId)) throw new ForbiddenException("Not your draft");

        String now = Instant.now().toString();
        List<DraftItemDto> items = request.items().stream()
                .map(i -> new DraftItemDto(sanitize(i.customerName()), i.customerId(), sanitize(i.comment()), sanitize(i.board())))
                .toList();

        DraftDto updated = new DraftDto(draftId, managerId, sanitize(request.name()), items, existing.createdAt(), now);
        store.putDraft(updated);

        if (sheetsClient != null) {
            int rowIndex = sheetsClient.findRowIndex("Drafts", draftId);
            if (rowIndex > 0) {
                String itemsJson = serializeItems(items);
                sheetsClient.updateRow("Drafts", rowIndex, List.of(
                        updated.draftId(), updated.managerId(), updated.name(),
                        itemsJson, updated.createdAt(), updated.updatedAt()));
            }
        }

        return updated;
    }

    public void deleteDraft(String draftId, String managerId) {
        DraftDto existing = store.getDraft(draftId);
        if (existing == null) throw new NotFoundException("Draft not found: " + draftId);
        if (!existing.managerId().equals(managerId)) throw new ForbiddenException("Not your draft");
        store.removeDraft(draftId);
        log.info("Draft deleted: {}", draftId);
    }

    public DraftDto loadDraft(String draftId, String managerId) {
        DraftDto draft = store.getDraft(draftId);
        if (draft == null) throw new NotFoundException("Draft not found: " + draftId);
        if (!draft.managerId().equals(managerId)) throw new ForbiddenException("Not your draft");
        return draft;
    }

    public DraftDto copyDraft(String draftId, CopyDraftRequest request, String managerId) {
        DraftDto source = store.getDraft(draftId);
        if (source == null) throw new NotFoundException("Draft not found: " + draftId);
        if (!source.managerId().equals(managerId)) throw new ForbiddenException("Not your draft");
        if (!managerId.equals(request.targetManagerId())) {
            throw new BadRequestException("Managers can only copy drafts to themselves");
        }

        String now = Instant.now().toString();
        String newDraftId = UUID.randomUUID().toString();
        String name = request.name() != null && !request.name().isBlank() ? request.name() : source.name();

        DraftDto copy = new DraftDto(newDraftId, managerId, name, source.items(), now, now);
        store.putDraft(copy);

        if (sheetsClient != null) {
            String itemsJson = serializeItems(source.items());
            sheetsClient.appendRow("Drafts", List.of(
                    copy.draftId(), copy.managerId(), copy.name(),
                    itemsJson, copy.createdAt(), copy.updatedAt()));
        }

        log.info("Draft copied: {} -> {} for manager {}", draftId, newDraftId, managerId);
        return copy;
    }

    private String serializeItems(List<DraftItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.error("Failed to serialize draft items", e);
            return "[]";
        }
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return input.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
    }

    private String resolveWeekdayName(DayOfWeek dayOfWeek) {
        if (weekdayNamesConfig == null || weekdayNamesConfig.isBlank()) {
            return dayOfWeek.name().toLowerCase();
        }
        String[] parts = weekdayNamesConfig.split(",");
        if (parts.length < 7) {
            return dayOfWeek.name().toLowerCase();
        }
        String value = parts[dayOfWeek.getValue() - 1].trim();
        return value.isEmpty() ? dayOfWeek.name().toLowerCase() : value;
    }
}
