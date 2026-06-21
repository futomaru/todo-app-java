package io.github.futomaru.todoapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.futomaru.todoapp.dto.TodoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

/// 結合テスト（End-to-End シナリオ）。
/// `@SpringBootTest(RANDOM_PORT)` で実サーバを起動し、`application-test.properties` の in-memory H2 に対して
/// `RestTestClient` で実 HTTP リクエストを送る。
/// スライステストでは保証できない「Controller → Service → MyBatis → H2 の配線が結合して往復すること」を検証する。
///
/// Spring Boot 4 では HTTP テストクライアントは自動構成されない。
/// `@AutoConfigureRestTestClient` は別モジュール（`spring-boot-resttestclient`）に分離されたため、
/// 依存を増やさず `@LocalServerPort` + `RestTestClient.bindToServer()` で手動構築する。
/// 参考: https://docs.spring.io/spring-framework/reference/testing/resttestclient.html
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TodoappApplicationTests {

  @LocalServerPort int port;

  RestTestClient client;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Nested
  class ContextLoad {

    @Test
    void contextLoads() {}
  }

  /// CRUD のハッピーパスを 1 本の連鎖シナリオで検証する。
  /// POST で生成された id を後続のステップで使うことで、機能間の繋がり（HTTP → DB → HTTP の往復）を保証する。
  @Nested
  class ハッピーパスシナリオ {

    @Test
    void POSTで作成しGETで取得しPATCHで更新しDELETEで削除できる() {
      // (1) POST: 作成 → 201 + Location ヘッダ + body から id を取得
      TodoResponse created =
          client
              .post()
              .uri("/api/v1/todos")
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  """
                  {"title": "結合テスト用Todo", "description": "シナリオ"}
                  """)
              .exchange()
              .expectStatus()
              .isCreated()
              .expectHeader()
              .value("Location", location -> assertThat(location).contains("/api/v1/todos/"))
              .expectBody(TodoResponse.class)
              .returnResult()
              .getResponseBody();

      assertThat(created).isNotNull();
      assertThat(created.id()).isNotNull();
      assertThat(created.title()).isEqualTo("結合テスト用Todo");
      assertThat(created.description()).isEqualTo("シナリオ");
      assertThat(created.completed()).isFalse();
      assertThat(created.createdAt()).isNotNull();
      assertThat(created.updatedAt()).isNotNull();

      Long id = created.id();

      // (2) GET /{id}: 単件取得 → 200 + 作成内容と一致
      client
          .get()
          .uri("/api/v1/todos/{id}", id)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.id")
          .isEqualTo(id)
          .jsonPath("$.title")
          .isEqualTo("結合テスト用Todo")
          .jsonPath("$.description")
          .isEqualTo("シナリオ")
          .jsonPath("$.completed")
          .isEqualTo(false)
          .jsonPath("$.createdAt")
          .exists()
          .jsonPath("$.updatedAt")
          .exists();

      // (3) GET /: 一覧 → 200 + 作成した id を含む
      client
          .get()
          .uri("/api/v1/todos")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$[?(@.id == " + id + ")]")
          .exists();

      // (4) PATCH /{id}: completed=true に更新 → 200
      client
          .patch()
          .uri("/api/v1/todos/{id}", id)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              """
              {"completed": true}
              """)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.id")
          .isEqualTo(id)
          .jsonPath("$.title")
          .isEqualTo("結合テスト用Todo")
          .jsonPath("$.completed")
          .isEqualTo(true);

      // (5) GET /{id}: 再取得 → DB に completed=true が永続化されたことを確認
      client
          .get()
          .uri("/api/v1/todos/{id}", id)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.completed")
          .isEqualTo(true);

      // (6) DELETE /{id}: 削除 → 204
      client.delete().uri("/api/v1/todos/{id}", id).exchange().expectStatus().isNoContent();

      // (7) GET /{id}: 削除済み → 404 + ProblemDetail
      client
          .get()
          .uri("/api/v1/todos/{id}", id)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo(404)
          .jsonPath("$.detail")
          .isEqualTo("Todo not found: " + id);
    }
  }
}
