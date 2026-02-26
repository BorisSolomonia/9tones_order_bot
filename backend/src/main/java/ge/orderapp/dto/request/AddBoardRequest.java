package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddBoardRequest(@NotBlank String board) {}
