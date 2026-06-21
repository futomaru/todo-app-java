package io.github.futomaru.todoapp.dto;

import jakarta.validation.constraints.Size;

public record TodoUpdateRequest(
        @Size(min = 1, max = 255) String title,
        @Size(max = 1000) String description,
        Boolean completed) {
}
