package ge.orderapp.dto.response;

public record CustomerDto(
        String customerId,
        String name,
        String tin,
        int frequencyScore,
        String addedBy,
        boolean active,
        String createdAt,
        String updatedAt,
        String board
) {}
