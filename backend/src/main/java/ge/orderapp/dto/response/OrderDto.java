package ge.orderapp.dto.response;

import java.util.List;

public record OrderDto(
        String orderId,
        String managerId,
        String managerName,
        String date,
        String status,
        boolean telegramSent,
        String telegramSentAt,
        int itemCount,
        String createdAt,
        List<OrderItemDto> items
) {}
