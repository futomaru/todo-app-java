package io.github.futomaru.todoapp.service;

import io.github.futomaru.todoapp.dto.TodoCreateRequest;
import io.github.futomaru.todoapp.dto.TodoResponse;
import io.github.futomaru.todoapp.dto.TodoUpdateRequest;
import io.github.futomaru.todoapp.entity.Todo;
import io.github.futomaru.todoapp.exception.TodoNotFoundException;
import io.github.futomaru.todoapp.mapper.TodoMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Todo のビジネスロジックを提供するサービス層。
@Service
@Transactional(readOnly = true)
public class TodoService {
  private final TodoMapper todoMapper;
  private final Clock clock;

  public TodoService(TodoMapper todoMapper, Clock clock) {
    this.todoMapper = todoMapper;
    this.clock = clock;
  }

  public List<TodoResponse> findAll(@Nullable Boolean completed) {
    List<Todo> todos =
        (completed == null) ? todoMapper.findAll() : todoMapper.findByCompleted(completed);
    return todos.stream().map(TodoResponse::from).toList();
  }

  public TodoResponse findById(Long id) {
    Todo todo = todoMapper.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    return TodoResponse.from(todo);
  }

  @Transactional
  public TodoResponse create(TodoCreateRequest request) {
    var now = LocalDateTime.now(clock);
    Todo todo = new Todo();
    todo.setTitle(request.title());
    todo.setDescription(request.description());
    todo.setCompleted(false);
    todo.setCreatedAt(now);
    todo.setUpdatedAt(now);

    todoMapper.insert(todo);
    return TodoResponse.from(todo);
  }

  @Transactional
  public TodoResponse update(Long id, TodoUpdateRequest request) {
    var now = LocalDateTime.now(clock);
    Todo existingTodo = todoMapper.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    if (request.title() != null) {
      existingTodo.setTitle(request.title());
    }
    if (request.description() != null) {
      existingTodo.setDescription(request.description());
    }
    if (request.completed() != null) {
      existingTodo.setCompleted(request.completed());
    }
    existingTodo.setUpdatedAt(now);

    todoMapper.update(existingTodo);
    return TodoResponse.from(existingTodo);
  }

  @Transactional
  public void delete(Long id) {
    todoMapper.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    todoMapper.deleteById(id);
  }

  @Transactional
  public void deleteCompleted() {
    todoMapper.deleteCompleted();
  }
}
