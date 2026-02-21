package ge.orderapp.dto.response;

public record DraftItemDto(
        String customerName,
        String customerId,
        String comment
) {}
