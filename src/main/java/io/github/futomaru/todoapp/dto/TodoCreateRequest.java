package io.github.futomaru.todoapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TodoCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 1000) String description) {
}