package ge.orderapp.dto.response;

public record OrderItemDto(
        String itemId,
        String orderId,
        String customerName,
        String customerId,
        String comment,
        String createdAt,
        String board
) {}
