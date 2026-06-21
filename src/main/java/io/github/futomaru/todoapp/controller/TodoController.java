package io.github.futomaru.todoapp.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.futomaru.todoapp.dto.TodoCreateRequest;
import io.github.futomaru.todoapp.dto.TodoResponse;
import io.github.futomaru.todoapp.dto.TodoUpdateRequest;
import io.github.futomaru.todoapp.service.TodoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

/// Todo リソースの REST API エンドポイント。
@RestController
@RequestMapping("/api/v1/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public List<TodoResponse> listTodos(@RequestParam(required = false) Boolean completed) {
        return todoService.findAll(completed);
    }

    @GetMapping("/{id}")
    public TodoResponse getTodoById(@PathVariable @Min(1) Long id) {
        return todoService.findById(id);
    }

    @PostMapping
    public ResponseEntity<TodoResponse> createTodo(@RequestBody @Valid TodoCreateRequest request) {
        TodoResponse created = todoService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    public TodoResponse patchTodo(@PathVariable @Min(1) Long id,
            @RequestBody @Valid TodoUpdateRequest request) {
        return todoService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTodo(@PathVariable @Min(1) Long id) {
        todoService.delete(id);
    }

    /// 完了済み Todo を一括削除する（`completed=true` が必須）。
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCompleted(
            @RequestParam @AssertTrue(message = "must be true") boolean completed) {
        todoService.deleteCompleted();
    }
}
