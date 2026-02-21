package ge.orderapp.dto.response;

public record SyncStateDto(
        String syncId,
        String syncType,
        String startDate,
        String endDate,
        String status,
        int customersFound,
        int customersAdded,
        String errorMessage,
        String startedAt,
        String completedAt
) {}
