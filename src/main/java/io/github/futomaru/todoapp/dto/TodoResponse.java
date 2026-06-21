package io.github.futomaru.todoapp.dto;

import io.github.futomaru.todoapp.entity.Todo;
import java.time.LocalDateTime;

public record TodoResponse(
    Long id,
    String title,
    String description,
    boolean completed,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static TodoResponse from(Todo todo) {
    return new TodoResponse(
        todo.getId(),
        todo.getTitle(),
        todo.getDescription(),
        todo.isCompleted(),
        todo.getCreatedAt(),
        todo.getUpdatedAt());
  }
}
