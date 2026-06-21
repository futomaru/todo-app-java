package io.github.futomaru.todoapp.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.futomaru.todoapp.dto.TodoCreateRequest;
import io.github.futomaru.todoapp.dto.TodoResponse;
import io.github.futomaru.todoapp.dto.TodoUpdateRequest;
import io.github.futomaru.todoapp.service.TodoService;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;

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
    public ResponseEntity<TodoResponse> getTodoById(@PathVariable Long id) {
        return ResponseEntity.ok(todoService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TodoResponse> createTodo(@RequestBody @Valid TodoCreateRequest request) {
        TodoResponse created = todoService.create(request);
        URI location = URI.create("/api/v1/todos/" + created.id());
        return ResponseEntity.created(location).body(created);

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(@PathVariable Long id) {
        todoService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteCompleted(
            @RequestParam boolean completed) {
        if (completed) {
            todoService.deleteCompleted();
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TodoResponse> patchTodo(@PathVariable Long id,
            @RequestBody @Valid TodoUpdateRequest request) {
        return ResponseEntity.ok(todoService.update(id, request));
    }
}
