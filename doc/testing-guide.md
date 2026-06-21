# テストガイドライン

## テスト戦略

Spring Boot のテスト用スライス（部分起動）を活用し、3 層を独立してテストする。
**スライスを使う理由**: `@SpringBootTest` でアプリ全体を毎回起動するのは遅く、失敗時の原因も特定しにくい。
層ごとに必要な Bean だけを起動することで、テスト対象が明確になり実行も速くなる。

| レイヤー | テスト種別 | 使用ツール | 観点 |
| --- | --- | --- | --- |
| Mapper | DB 統合テスト | `@MybatisTest` + H2 | SQL が H2 上で正しく動くか |
| Service | 単体テスト | JUnit 5 + Mockito | ビジネスロジック・例外・`Clock` 注入 |
| Controller | Web 層テスト | `RestTestClient` + Mockito | HTTP マッピング・バリデーション・`ProblemDetail` の形式 |
| 全体 | 結合テスト | `@SpringBootTest` | エンドツーエンドのハッピーパス（少数）|

採用理由（`RestTestClient` 採用 / `@MybatisTest` の DB 置換挙動）は [design.md §11](design.md#11-テスト戦略) を参照。
本書ではサンプルコードと運用ルールに集中する。

> **現状の `@SpringBootTest`**: 既定の `TodoappApplicationTests` は `contextLoads` 相当（コンテキスト起動の確認のみ）。
> エンドツーエンドの API シナリオはまだ追加していない。MVP の完成後に最小限のハッピーパス（POST → GET → PATCH → DELETE）を追加する予定。

## テストファイル配置

```
src/test/java/io/github/futomaru/todoapp/
├── controller/TodoControllerTest.java   ← RestTestClient + Mockito (standalone)
├── service/TodoServiceTest.java         ← JUnit 5 + Mockito
└── mapper/TodoMapperTest.java           ← @MybatisTest
```

## 方針

- **`@Nested`** + 日本語クラス名でケースをグルーピング
- **テストメソッド名は日本語**で振る舞いを記述（`title_が空なら_400_と_ProblemDetail_を返す` など）
- **`Clock`** は `Clock.fixed(...)` を注入して時刻を固定する
- **異常系**では `verify(mapper, never()).insert(any())` 等で副作用が無いことも確認する
- AAA（準備・実行・検証）は **空行で区切る** ことで読み手に意図を示す
  - 装飾コメント（`//// 準備 ////` 等）は必須にしない（実装の構造から十分読み取れる）

## 各レイヤーのサンプル

### Mapper Test

`@MybatisTest` が組み込み H2 とトランザクション境界（テスト後ロールバック）を自動で構成する。

```java
@MybatisTest
class TodoMapperTest {

    @Autowired TodoMapper todoMapper;

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-06-21T10:00:00");

    @Nested
    class insert {

        @Test
        void 登録後にidが採番されエンティティに設定される() {
            Todo todo = new Todo();
            todo.setTitle("新規Todo");
            todo.setCompleted(false);
            todo.setCreatedAt(NOW);
            todo.setUpdatedAt(NOW);

            todoMapper.insert(todo);

            assertThat(todo.getId()).isNotNull();
        }
    }
}
```

### Service Test

`Clock` を `Clock.fixed(...)` で固定することで `LocalDateTime.now(clock)` の結果を検証できる。
Mapper は `@Mock` で差し替え、`ArgumentCaptor` で Service が組み立てた Entity を観察する。

```java
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    static final Clock FIXED = Clock.fixed(
            Instant.parse("2026-06-20T10:00:00Z"), ZoneOffset.UTC);
    static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED);

    @Mock TodoMapper mapper;
    TodoService service;

    @BeforeEach
    void setUp() { service = new TodoService(mapper, FIXED); }

    @Nested
    class create {

        @Test
        void createdAtとupdatedAtにClockの時刻が設定される() {
            service.create(new TodoCreateRequest("買い物", "牛乳"));

            ArgumentCaptor<Todo> captor = ArgumentCaptor.forClass(Todo.class);
            verify(mapper).insert(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isEqualTo(FIXED_NOW);
            assertThat(captor.getValue().getUpdatedAt()).isEqualTo(FIXED_NOW);
        }
    }

    @Nested
    class findById {

        @Test
        void 存在しないidを指定するとTodoNotFoundExceptionをスローする() {
            when(mapper.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(999L))
                    .isInstanceOf(TodoNotFoundException.class);
        }
    }
}
```

### Controller Test

`RestTestClient.bindToController(...)` で Controller を standalone に組み立て、
`setControllerAdvice(...)` で `GlobalExceptionHandler` を組み込む。
これにより「Controller + ExceptionHandler の協調」をテスト対象にできる。

```java
@ExtendWith(MockitoExtension.class)
class TodoControllerTest {

    @Mock TodoService service;
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
    class CreateTodo {

        @Test
        void 正常なリクエストで201とLocationヘッダーを返す() {
            when(service.create(any())).thenReturn(
                    new TodoResponse(1L, "新しいタスク", null, false, null, null));

            client.post().uri("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"title": "新しいタスク"}
                            """)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().value("Location",
                            location -> assertThat(location).endsWith("/api/v1/todos/1"));
        }

        @Test
        void titleが空文字のとき400とProblemDetailを返す() {
            client.post().uri("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"title": ""}
                            """)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.detail").isEqualTo("Validation failed")
                    .jsonPath("$.errors[?(@.field == 'title')]").exists();
        }
    }
}
```

> **Text Blocks の注意**: リクエストボディを `"""..."""` で書くときは、開始の `"""` 直後に改行が必要。
> `"""{ "title": ... }"""` のように 1 行で書くとコンパイルエラーになる。

## 実行方法

```bash
./gradlew test                                    # 全テスト
./gradlew test --tests "*.TodoServiceTest"        # 特定クラスのみ
```
