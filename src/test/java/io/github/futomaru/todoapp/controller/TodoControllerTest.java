package io.github.futomaru.todoapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import io.github.futomaru.todoapp.dto.TodoResponse;
import io.github.futomaru.todoapp.exception.GlobalExceptionHandler;
import io.github.futomaru.todoapp.exception.TodoNotFoundException;
import io.github.futomaru.todoapp.service.TodoService;

@ExtendWith(MockitoExtension.class)
class TodoControllerTest {
    @Mock
    TodoService service;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient
                .bindToController(new TodoController(service))
                .configureServer(builder -> builder
                        .setControllerAdvice(new GlobalExceptionHandler()))
                .build();
    }

    @Nested
    class ListTodos {

        @Test
        void completedパラメータなしで200と全件リストを返す() {
            when(service.findAll(null)).thenReturn(List.of(
                    new TodoResponse(1L, "タスク1", null, false, null, null),
                    new TodoResponse(2L, "タスク2", null, true, null, null)));

            client.get().uri("/api/v1/todos")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(2)
                    .jsonPath("$[0].id").isEqualTo(1)
                    .jsonPath("$[1].id").isEqualTo(2);
        }

        @Test
        void completedがtrueのとき200を返す() {
            when(service.findAll(true)).thenReturn(List.of(
                    new TodoResponse(2L, "タスク2", null, true, null, null)));

            client.get().uri("/api/v1/todos?completed=true")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0].completed").isEqualTo(true);
        }

        @Test
        void completedがfalseのとき200を返す() {
            when(service.findAll(false)).thenReturn(List.of(
                    new TodoResponse(1L, "タスク1", null, false, null, null)));

            client.get().uri("/api/v1/todos?completed=false")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1)
                    .jsonPath("$[0].completed").isEqualTo(false);
        }

        @Test
        void 件数が0件のとき200と空配列を返す() {
            when(service.findAll(null)).thenReturn(List.of());

            client.get().uri("/api/v1/todos")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(0);
        }
    }

    @Nested
    class GetTodoById {

        @Test
        void 存在するidを指定すると200とTodoResponseのJSONを返す() {
            when(service.findById(1L)).thenReturn(new TodoResponse(1L, "タイトル", "説明", false, null, null));
            client.get().uri("/api/v1/todos/1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(1)
                    .jsonPath("$.title").isEqualTo("タイトル")
                    .jsonPath("$.description").isEqualTo("説明")
                    .jsonPath("$.completed").isEqualTo(false);
        }

        @Test
        void 存在しないidを指定すると404とProblemDetailを返す() {
            when(service.findById(999L)).thenThrow(new TodoNotFoundException(999L));

            client.get().uri("/api/v1/todos/999")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Todo not found: 999");
        }
    }

    @Nested
    class CreateTodo {

        @Test
        void 正常なリクエストで201とLocationヘッダーを返す() {
            when(service.create(any())).thenReturn(new TodoResponse(1L, "新しいタスク", null, false, null, null));

            client.post().uri("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"title": "新しいタスク"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().location("/api/v1/todos/1");
        }

        @Test
        void titleがnullのとき400を返す() {
            client.post().uri("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"title": null}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void titleが空文字のとき400を返す() {
            client.post().uri("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"title": ""}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void titleが256文字以上のとき400を返す() {
            String longTitle = "a".repeat(256);
            client.post().uri("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"title\": \"" + longTitle + "\"}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    class DeleteTodo {

        @Test
        void 存在するidを指定すると204を返す() {
            doNothing().when(service).delete(1L);

            client.delete().uri("/api/v1/todos/1")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        void 存在しないidを指定すると404とProblemDetailを返す() {
            doThrow(new TodoNotFoundException(999L)).when(service).delete(999L);

            client.delete().uri("/api/v1/todos/999")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Todo not found: 999");
        }
    }

    @Nested
    class DeleteCompleted {

        @Test
        void completedがtrueのとき204を返す() {
            doNothing().when(service).deleteCompleted();

            client.delete().uri("/api/v1/todos?completed=true")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        void completedがfalseのとき400を返す() {
            client.delete().uri("/api/v1/todos?completed=false")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    class PatchTodo {

        @Test
        void 存在するidを指定すると200とTodoResponseのJSONを返す() {
            when(service.update(eq(1L), any())).thenReturn(
                    new TodoResponse(1L, "更新済み", "説明", true, null, null));

            client.patch().uri("/api/v1/todos/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"title": "更新済み", "description": "説明", "completed": true}
                            """)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(1)
                    .jsonPath("$.title").isEqualTo("更新済み")
                    .jsonPath("$.completed").isEqualTo(true);
        }

        @Test
        void 存在しないidを指定すると404とProblemDetailを返す() {
            when(service.update(eq(999L), any())).thenThrow(new TodoNotFoundException(999L));

            client.patch().uri("/api/v1/todos/999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {}
                            """)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Todo not found: 999");
        }
    }

}
