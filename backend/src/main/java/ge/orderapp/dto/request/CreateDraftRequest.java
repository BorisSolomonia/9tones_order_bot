package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateDraftRequest(
        @NotBlank String name,
        @NotNull List<DraftItemRequest> items
) {
    public record DraftItemRequest(
            String customerName,
            String customerId,
            String comment
    ) {}
}
