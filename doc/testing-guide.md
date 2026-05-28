# テストガイドライン

## テスト戦略

| レベル | 対象 | ツール | 観点 |
| --- | --- | --- | --- |
| Mapper Test | Mapper | `@MybatisTest` + H2 | SQL が正しく動くか |
| Service Test | Service | JUnit 5 + Mockito | ビジネスロジック・例外・`Clock` 注入 |
| Controller Test | Controller | `@WebMvcTest` + `MockMvc` | HTTP マッピング・バリデーション・`ProblemDetail` |
| E2E Test | 全層 | `@SpringBootTest` | ハッピーパスの動作確認（少数） |

## テストファイル配置

```
src/test/java/io/github/futomaru/todoapp/
├── controller/TodoControllerTest.java
├── service/TodoServiceTest.java
└── mapper/TodoMapperTest.java
```

## 方針

- **AAA パターン**: `//// 準備 ////` / `//// 実行 ////` / `//// 検証 ////` でセクション分割
- **`@Nested`** + 日本語クラス名でケースをグルーピング
- **テストメソッド名は日本語**で振る舞いを記述
- **`Clock`** は `Clock.fixed(...)` を注入して時刻を固定
- **異常系**では `verify(mapper, never()).insert(any())` 等で副作用が無いことも確認

## 各レイヤーのサンプル

### Mapper Test

```java
@MybatisTest
class TodoMapperTest {

    @Autowired TodoMapper mapper;

    @Nested
    class 登録と取得 {

        @Test
        void insert_すると_findById_で取得できる() {
            //// 準備 ////
            var todo = new Todo();
            todo.setTitle("買い物");
            todo.setCompleted(false);
            todo.setCreatedAt(LocalDateTime.parse("2026-05-28T10:00:00"));
            todo.setUpdatedAt(LocalDateTime.parse("2026-05-28T10:00:00"));

            //// 実行 ////
            mapper.insert(todo);

            //// 検証 ////
            assertThat(mapper.findById(todo.getId()))
                .hasValueSatisfying(t -> assertThat(t.getTitle()).isEqualTo("買い物"));
        }
    }
}
```

### Service Test

```java
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    @Mock TodoMapper mapper;
    TodoService service;

    private static final Clock FIXED =
        Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() { service = new TodoService(mapper, FIXED); }

    @Nested
    class 作成の場合 {

        @Test
        void createdAt_と_updatedAt_に_Clock_の値が設定される() {
            //// 実行 ////
            service.create(new TodoCreateRequest("買い物", "牛乳"));

            //// 検証 ////
            var captor = ArgumentCaptor.forClass(Todo.class);
            verify(mapper).insert(captor.capture());
            assertThat(captor.getValue().getCreatedAt())
                .isEqualTo(LocalDateTime.parse("2026-05-28T10:00:00"));
        }
    }

    @Nested
    class 存在しない_id_の取得 {

        @Test
        void TodoNotFoundException_が発生する() {
            when(mapper.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(TodoNotFoundException.class);
        }
    }
}
```

### Controller Test

```java
@WebMvcTest(TodoController.class)
class TodoControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean TodoService service;

    @Nested
    class POST_api_v1_todos {

        @Test
        void 正常リクエストは_201_と_Location_ヘッダーを返す() throws Exception {
            when(service.create(any())).thenReturn(
                new TodoResponse(2L, "買い物", "牛乳", false,
                    LocalDateTime.parse("2026-05-28T10:00:00"),
                    LocalDateTime.parse("2026-05-28T10:00:00")));

            mockMvc.perform(post("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "title": "買い物", "description": "牛乳" }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/todos/2"));
        }

        @Test
        void title_が空なら_400_と_ProblemDetail_を返す() throws Exception {
            mockMvc.perform(post("/api/v1/todos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{ "title": "", "description": "牛乳" }"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        }
    }
}
```

## 実行方法

```bash
./gradlew test                                    # 全テスト
./gradlew test --tests "*.TodoServiceTest"        # 特定クラスのみ
```
