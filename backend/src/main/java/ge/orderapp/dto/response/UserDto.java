package ge.orderapp.dto.response;

public record UserDto(
        String userId,
        String username,
        String displayName,
        String role,
        boolean active,
        String createdAt
) {}
