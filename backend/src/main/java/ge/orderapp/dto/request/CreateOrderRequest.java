package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty List<OrderItemRequest> items,
        boolean sendTelegram
) {
    public record OrderItemRequest(
            String customerName,
            String customerId,
            String comment,
            String board
    ) {}
}
