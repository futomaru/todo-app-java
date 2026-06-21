package io.github.futomaru.todoapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.futomaru.todoapp.dto.TodoResponse;
import io.github.futomaru.todoapp.exception.GlobalExceptionHandler;
import io.github.futomaru.todoapp.exception.TodoNotFoundException;
import io.github.futomaru.todoapp.service.TodoService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/// `TodoController` の Web 層テスト。
/// `RestTestClient` で Controller を standalone に組み立て、`GlobalExceptionHandler` を組み込んだ上で、
/// HTTP マッピング・Bean Validation・`ProblemDetail` 形式の応答を検証する。
@ExtendWith(MockitoExtension.class)
class TodoControllerTest {
  @Mock TodoService service;

  RestTestClient client;

  @BeforeEach
  void setUp() {
    client =
        RestTestClient.bindToController(new TodoController(service))
            .configureServer(builder -> builder.setControllerAdvice(new GlobalExceptionHandler()))
            .build();
  }

  /// `GET /api/v1/todos`: `completed` クエリパラメータの有無による Service への振り分けと、
  /// レスポンス JSON 配列の構造を検証する。
  @Nested
  class ListTodos {

    @Test
    void completedパラメータなしで200と全件リストを返す() {
      when(service.findAll(null))
          .thenReturn(
              List.of(
                  new TodoResponse(1L, "タスク1", null, false, null, null),
                  new TodoResponse(2L, "タスク2", null, true, null, null)));

      client
          .get()
          .uri("/api/v1/todos")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(2)
          .jsonPath("$[0].id")
          .isEqualTo(1)
          .jsonPath("$[1].id")
          .isEqualTo(2);
    }

    @Test
    void completedがtrueのとき200を返す() {
      when(service.findAll(true))
          .thenReturn(List.of(new TodoResponse(2L, "タスク2", null, true, null, null)));

      client
          .get()
          .uri("/api/v1/todos?completed=true")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(1)
          .jsonPath("$[0].completed")
          .isEqualTo(true);
    }

    @Test
    void completedがfalseのとき200を返す() {
      when(service.findAll(false))
          .thenReturn(List.of(new TodoResponse(1L, "タスク1", null, false, null, null)));

      client
          .get()
          .uri("/api/v1/todos?completed=false")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(1)
          .jsonPath("$[0].completed")
          .isEqualTo(false);
    }

    @Test
    void 件数が0件のとき200と空配列を返す() {
      when(service.findAll(null)).thenReturn(List.of());

      client
          .get()
          .uri("/api/v1/todos")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.length()")
          .isEqualTo(0);
    }
  }

  /// `GET /api/v1/todos/{id}`: 正常系の 200、`TodoNotFoundException` 由来の 404、
  /// `@PathVariable @Min(1)` バリデーション失敗による 400 の振り分けを検証する。
  @Nested
  class GetTodoById {

    @Test
    void 存在するidを指定すると200とTodoResponseのJSONを返す() {
      when(service.findById(1L)).thenReturn(new TodoResponse(1L, "タイトル", "説明", false, null, null));
      client
          .get()
          .uri("/api/v1/todos/1")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.id")
          .isEqualTo(1)
          .jsonPath("$.title")
          .isEqualTo("タイトル")
          .jsonPath("$.description")
          .isEqualTo("説明")
          .jsonPath("$.completed")
          .isEqualTo(false);
    }

    @Test
    void 存在しないidを指定すると404とProblemDetailを返す() {
      when(service.findById(999L)).thenThrow(new TodoNotFoundException(999L));

      client
          .get()
          .uri("/api/v1/todos/999")
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo(404)
          .jsonPath("$.title")
          .isEqualTo("Not Found")
          .jsonPath("$.detail")
          .isEqualTo("Todo not found: 999")
          .jsonPath("$.instance")
          .isEqualTo("/api/v1/todos/999");
    }

    @Test
    void idが0のとき400とProblemDetailを返す() {
      client
          .get()
          .uri("/api/v1/todos/0")
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo(400)
          .jsonPath("$.title")
          .isEqualTo("Bad Request")
          .jsonPath("$.detail")
          .isEqualTo("Validation failed")
          .jsonPath("$.instance")
          .isEqualTo("/api/v1/todos/0")
          .jsonPath("$.errors")
          .isArray();

      verify(service, never()).findById(any());
    }
  }

  /// `POST /api/v1/todos`: 正常系の 201 + `Location` ヘッダー生成と、
  /// `@RequestBody @Valid TodoCreateRequest` のバリデーション失敗（`@NotBlank` / `@Size`）による 400 を検証する。
  @Nested
  class CreateTodo {

    @Test
    void 正常なリクエストで201とLocationヘッダーを返す() {
      when(service.create(any()))
          .thenReturn(new TodoResponse(1L, "新しいタスク", null, false, null, null));

      client
          .post()
          .uri("/api/v1/todos")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                            {"title": "新しいタスク"}
                            """)
          .exchange()
          .expectStatus()
          .isCreated()
          .expectHeader()
          .value(
              "Location",
              location -> {
                org.assertj.core.api.Assertions.assertThat(location).endsWith("/api/v1/todos/1");
              });
    }

    @Test
    void titleがnullのとき400とProblemDetailを返す() {
      client
          .post()
          .uri("/api/v1/todos")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                            {"title": null}
                            """)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo(400)
          .jsonPath("$.detail")
          .isEqualTo("Validation failed")
          .jsonPath("$.errors[?(@.field == 'title')]")
          .exists();
    }

    @Test
    void titleが空文字のとき400とProblemDetailを返す() {
      client
          .post()
          .uri("/api/v1/todos")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                            {"title": ""}
                            """)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.detail")
          .isEqualTo("Validation failed")
          .jsonPath("$.errors[?(@.field == 'title')]")
          .exists();
    }

    @Test
    void titleが256文字以上のとき400とProblemDetailを返す() {
      String longTitle = "a".repeat(256);
      client
          .post()
          .uri("/api/v1/todos")
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"title\": \"" + longTitle + "\"}")
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.detail")
          .isEqualTo("Validation failed")
          .jsonPath("$.errors[?(@.field == 'title')]")
          .exists();
    }
  }

  /// `DELETE /api/v1/todos/{id}`: 正常系の 204、`TodoNotFoundException` 由来の 404、
  /// `@PathVariable @Min(1)` バリデーション失敗による 400 の振り分けを検証する。
  @Nested
  class DeleteTodo {

    @Test
    void 存在するidを指定すると204を返す() {
      doNothing().when(service).delete(1L);

      client.delete().uri("/api/v1/todos/1").exchange().expectStatus().isNoContent();
    }

    @Test
    void 存在しないidを指定すると404とProblemDetailを返す() {
      doThrow(new TodoNotFoundException(999L)).when(service).delete(999L);

      client
          .delete()
          .uri("/api/v1/todos/999")
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.detail")
          .isEqualTo("Todo not found: 999")
          .jsonPath("$.instance")
          .isEqualTo("/api/v1/todos/999");
    }

    @Test
    void idが0のとき400とProblemDetailを返す() {
      client
          .delete()
          .uri("/api/v1/todos/0")
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.detail")
          .isEqualTo("Validation failed");

      verify(service, never()).delete(any());
    }
  }

  /// `DELETE /api/v1/todos?completed=true`: 一括削除の 204、`@RequestParam @AssertTrue` による
  /// `completed=true` 以外（false）の 400、必須パラメータ欠落（`MissingServletRequestParameterException`）の 400
  // を検証する。
  @Nested
  class DeleteCompleted {

    @Test
    void completedがtrueのとき204を返す() {
      doNothing().when(service).deleteCompleted();

      client.delete().uri("/api/v1/todos?completed=true").exchange().expectStatus().isNoContent();
    }

    @Test
    void completedがfalseのとき400とProblemDetailを返す() {
      client
          .delete()
          .uri("/api/v1/todos?completed=false")
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo(400)
          .jsonPath("$.detail")
          .isEqualTo("Validation failed");

      verify(service, never()).deleteCompleted();
    }

    @Test
    void completedが未指定のとき400とProblemDetailを返す() {
      client
          .delete()
          .uri("/api/v1/todos")
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo(400)
          .jsonPath("$.title")
          .isEqualTo("Bad Request");

      verify(service, never()).deleteCompleted();
    }
  }

  /// `PATCH /api/v1/todos/{id}`: 部分更新の 200、`TodoNotFoundException` 由来の 404、
  /// `@PathVariable @Min(1)` および `@RequestBody @Valid TodoUpdateRequest`（`@Size(min=1)`）の
  /// バリデーション失敗による 400 を検証する。
  @Nested
  class PatchTodo {

    @Test
    void 存在するidを指定すると200とTodoResponseのJSONを返す() {
      when(service.update(eq(1L), any()))
          .thenReturn(new TodoResponse(1L, "更新済み", "説明", true, null, null));

      client
          .patch()
          .uri("/api/v1/todos/1")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                            {"title": "更新済み", "description": "説明", "completed": true}
                            """)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.id")
          .isEqualTo(1)
          .jsonPath("$.title")
          .isEqualTo("更新済み")
          .jsonPath("$.completed")
          .isEqualTo(true);
    }

    @Test
    void 存在しないidを指定すると404とProblemDetailを返す() {
      when(service.update(eq(999L), any())).thenThrow(new TodoNotFoundException(999L));

      client
          .patch()
          .uri("/api/v1/todos/999")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                            {}
                            """)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.detail")
          .isEqualTo("Todo not found: 999");
    }

    @Test
    void idが0のとき400とProblemDetailを返す() {
      client
          .patch()
          .uri("/api/v1/todos/0")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                            {"title": "new"}
                            """)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.detail")
          .isEqualTo("Validation failed");

      verify(service, never()).update(any(), any());
    }

    @Test
    void titleが空文字のとき400とProblemDetailを返す() {
      client
          .patch()
          .uri("/api/v1/todos/1")
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
                            {"title": ""}
                            """)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .jsonPath("$.detail")
          .isEqualTo("Validation failed")
          .jsonPath("$.errors[?(@.field == 'title')]")
          .exists();

      verify(service, never()).update(any(), any());
    }
  }
}
