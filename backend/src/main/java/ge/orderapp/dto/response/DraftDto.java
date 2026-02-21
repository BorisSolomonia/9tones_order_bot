package ge.orderapp.dto.response;

import java.util.List;

public record DraftDto(
        String draftId,
        String managerId,
        String name,
        List<DraftItemDto> items,
        String createdAt,
        String updatedAt
) {}
