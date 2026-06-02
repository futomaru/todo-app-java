# TODO アプリ 設計書

## 1. 概要

Spring MVC を用いた REST API 形式の TODO 管理バックエンドアプリケーション。  
組み込みデータベース（H2）でデータを永続化し、基本的な CRUD 操作を提供する。

本アプリは **Spring Boot 4 / Spring Framework 7 / JDK 25** を学習することを主目的とし、
これらのバージョンで利用可能な新機能（`ProblemDetail`、Virtual Threads、`record` DTO、Text Blocks 等）
を積極的に採用する。

### 1.1 スコープ: MVP (Minimum Viable Product)

本アプリは **学習目的の MVP（最小機能版）** として位置付ける。
「Todo アプリとして最低限機能する」ことを完成の基準とし、
**TodoMVC 相当のコア機能セット** にスコープを絞る：

- タスクの追加 / 一覧表示 / 1件取得
- タスクの編集（タイトル・説明・完了フラグ）
- タスクの削除
- 完了状態でのフィルター表示
- 完了済みタスクの一括削除（"Clear completed"）

以下は **意図的にスコープ外** とする（学習対象が拡散しないようにするため）：

| 機能 | 除外理由 |
|------|---------|
| 期限・優先度・カテゴリ | データモデルが膨らみ、設計判断の論点がブレる |
| 並び替え（`?sort=...`） | `ORDER BY id`（挿入順）で Todo として困らない |
| ページング | 個人 Todo は数十件規模を想定。MVP では不要 |
| 検索（`?q=...`） | 件数が少なければ画面スクロールで十分 |
| 認証・マルチユーザー | バックエンド設計学習の主題から外れる |

> **MVP として定義する意義**: 「完成の基準」を最初に固定することで、
> 「あれもこれも」と機能追加して未完成のまま塩漬けになるのを防ぐ。
> 上記コア機能が動いた時点で **「完成」と宣言してよい**。
> 拡張機能はそれぞれ独立した学習テーマとして、完成後に追加する。

---

## 2. 技術スタック

| 項目 | 採用技術 | 理由 |
|------|----------|------|
| 言語 | Java 25 | プロジェクト指定（`record`、Text Blocks、pattern matching を活用） |
| フレームワーク | Spring Boot 4.0.x + **Spring MVC** | DispatcherServlet ベースの標準的 Web レイヤー |
| コアフレームワーク | Spring Framework **7.0** | Spring Boot 4.0 に同梱；Jakarta EE 11 ベース |
| ビルドツール | Gradle **9** | プロジェクト指定 |
| データベース | H2 (file-based) | 組み込みDB、再起動後もデータ保持 |
| ORM | **MyBatis** (mybatis-spring-boot-starter) | SQL を明示的に記述、シンプルな CRUD に適している |
| バリデーション | Jakarta Bean Validation | `@NotBlank` / `@Size` 等のアノテーションで入力検証 |
| エラーレスポンス | **`ProblemDetail`** (RFC 7807) | Spring Framework 6+ の標準。`@RestControllerAdvice` で集約 |
| 並行処理 | **Virtual Threads** | `spring.threads.virtual.enabled=true` の 1 行で有効化 |
| フロントエンド | HTML / CSS / Vanilla JS | ビルドツール不要・Spring Boot の静的ファイル配信で提供 |

### 2.1 主な学習ポイント

- **JDK 25**: `record` を DTO に採用し、不変オブジェクト（DTO）と可変 POJO（Entity）の使い分けを学ぶ。SQL は **Text Blocks** で記述。
- **Spring Framework 7**: `ProblemDetail` ベースのエラーハンドリング、Virtual Threads サポート、Jakarta EE 11（`jakarta.*` 名前空間）が前提。
- **Spring Boot 4**: 自動構成、`@Transactional` 境界、テスト用スライス（`@WebMvcTest` / `@MybatisTest`）の使い分け。

---

## 3. アーキテクチャ

### 3.1 全体リクエスト処理フロー

```
ブラウザ (index.html / app.js)
    │  fetch() による REST 呼び出し
    ▼
─────────────────────────────────────────────────
GET /                       → ResourceHttpRequestHandler
                              → src/main/resources/static/index.html を返す
                              （静的リソースハンドラが応答。DispatcherServlet 経由ではない）
─────────────────────────────────────────────────
/api/v1/todos/**            → DispatcherServlet（Spring MVC のフロントコントローラー）
        │
        ▼
    HandlerMapping         ← @RequestMapping でルーティング解決
        │
        ▼
    TodoController         ← @RestController: リクエスト受付・レスポンス生成
        │
        ▼
    TodoService            ← @Service + @Transactional: ビジネスロジック
        │
        ▼
    TodoMapper             ← @Mapper: MyBatis による DB アクセス
        │
        ▼
    H2 Database (file-based) ← 永続化層
```

例外は `GlobalExceptionHandler`（`@RestControllerAdvice`）で捕捉し、
**`ProblemDetail`** 形式の JSON（`Content-Type: application/problem+json`）で応答する。

### 3.2 レイヤー責務

| レイヤー | クラス | 責務 |
|---------|--------|------|
| Controller | `TodoController` | HTTP メソッド・パスのマッピング、DTO 変換、バリデーション委譲 |
| Service | `TodoService` | ビジネスルール、`@Transactional` 制御、`Clock` を用いた `created_at`/`updated_at` 設定、エンティティ↔DTO 変換、例外スロー |
| Mapper | `TodoMapper` | `@Mapper` インターフェース＋ SQL アノテーションで DB CRUD |
| Entity | `Todo` | DB テーブルと対応する POJO（MyBatis が setter で値を注入するため可変クラス） |
| DTO | `TodoCreateRequest` / `TodoUpdateRequest` / `TodoResponse` | API の入出力形式定義（**Java `record` で不変に定義**）。Entity ↔ DTO の変換は DTO 側に static factory（例: `TodoResponse.from(Todo)`）として定義し、Service から呼び出す。 |
| Exception | `TodoNotFoundException` / `GlobalExceptionHandler` | カスタム例外と `ProblemDetail` への変換 |

> **トランザクション境界の指針**: 読み取り専用メソッド（`findAll` / `findById` 等）には
> `@Transactional(readOnly = true)` を付与し、書き込みを伴うメソッドには `@Transactional` を付ける。
> `readOnly = true` は JPA では flush 抑制の最適化、MyBatis でも JDBC ドライバへヒントが渡るほか、
> 「意図の明示」というドキュメント効果が大きい（典型的な Spring 慣習）。

---

## 4. プロジェクト構成

```
todoapp/
├── build.gradle
├── settings.gradle
├── doc/
│   └── design.md                          ← 本ドキュメント
├── src/main/
│   ├── java/io/github/futomaru/todoapp/
│   │   ├── TodoappApplication.java         # Spring Boot エントリーポイント
│   │   ├── controller/
│   │   │   └── TodoController.java         # Spring MVC コントローラー
│   │   ├── service/
│   │   │   └── TodoService.java            # ビジネスロジック層
│   │   ├── mapper/
│   │   │   └── TodoMapper.java             # MyBatis @Mapper インターフェース
│   │   ├── entity/
│   │   │   └── Todo.java                   # POJO エンティティ（mutable）
│   │   ├── dto/
│   │   │   ├── TodoCreateRequest.java      # 作成リクエスト DTO（record）
│   │   │   ├── TodoUpdateRequest.java      # 更新リクエスト DTO（record）
│   │   │   └── TodoResponse.java           # レスポンス DTO（record）
│   │   └── exception/
│   │       ├── TodoNotFoundException.java  # 404 用カスタム例外
│   │       └── GlobalExceptionHandler.java # @RestControllerAdvice
│   └── resources/
│       ├── application.properties          # DB・MyBatis・Virtual Threads 設定
│       ├── schema.sql                      # テーブル DDL（起動時に自動実行）
│       └── static/
│           ├── index.html                  # フロントエンド UI
│           ├── app.js                      # fetch() による REST 呼び出し
│           └── style.css                   # スタイル
└── src/test/
    └── java/io/github/futomaru/todoapp/
        ├── controller/TodoControllerTest.java  # @WebMvcTest
        ├── service/TodoServiceTest.java        # 通常 JUnit + Mockito
        └── mapper/TodoMapperTest.java          # @MybatisTest
```

---

## 5. データモデル

### 5.1 `todos` テーブル

| カラム | 型 | 制約 | 説明 |
|-------|-----|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主キー |
| `title` | VARCHAR(255) | NOT NULL | タスク名 |
| `description` | VARCHAR(1000) | NULL 許可 | 詳細説明 |
| `completed` | BOOLEAN | NOT NULL, DEFAULT false | 完了フラグ |
| `created_at` | TIMESTAMP | NOT NULL | 作成日時（Service 層で `LocalDateTime.now(clock)` を設定） |
| `updated_at` | TIMESTAMP | NOT NULL | 更新日時（Service 層で `LocalDateTime.now(clock)` を設定） |

> **タイムスタンプの設定方針**: DB の `DEFAULT CURRENT_TIMESTAMP` ではなく **Service 層でセット** する。
> 理由：アプリ側で時刻を完全に制御できるためテスタブルになり、
> 「ビジネスロジックは Service 層に集める」という Spring の典型設計と整合する。
>
> Service は `java.time.Clock` をコンストラクタで DI し、`LocalDateTime.now(clock)` を使う。
> 本番では `@Bean Clock systemClock() { return Clock.systemDefaultZone(); }` を登録し、
> テストでは `Clock.fixed(...)` を注入することで時刻を固定して検証できる。

### 5.2 `Todo` エンティティ設計

MyBatis は setter 経由でカラム値を注入するため、Java `record` は使えない。
**ミュータブルな通常クラス** として定義する（DTO とは対照的）。

```java
public class Todo {
    private Long id;
    private String title;
    private String description;
    private boolean completed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // getter / setter
}
```

### 5.3 DTO 設計（Java `record`）

DTO は **不変** が望ましく、Java 25 の `record` がそのまま適合する。
Bean Validation のアノテーションは record のコンポーネントに直接付与できる。

```java
public record TodoCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 1000) String description) {}

public record TodoUpdateRequest(
        @Size(min = 1, max = 255) String title,   // null は「未指定」、空文字は不許可
        @Size(max = 1000) String description,
        Boolean completed) {}     // null は「このフィールドは更新しない」を意味する

public record TodoResponse(
        Long id,
        String title,
        String description,
        boolean completed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
            todo.getId(), todo.getTitle(), todo.getDescription(),
            todo.isCompleted(), todo.getCreatedAt(), todo.getUpdatedAt());
    }
}
```

> **学習ポイント**: Entity（mutable POJO） と DTO（immutable record） を **意図的に分離** することで、
> 「フレームワーク（MyBatis）の制約」と「ドメインの不変性表現」を両立する設計を体験できる。
> `TodoUpdateRequest.completed` を `boolean` ではなく `Boolean`（ラッパー型）にしているのは、
> 「未指定（null）」と「false 指定」を区別するため。
> `title` の `@Size(min = 1, max = 255)` は「PATCH では未指定（null）は許可するが、
> 明示的に空文字で更新するのは不許可」という PATCH セマンティクスを表現している
> （`@NotBlank` は null も弾いてしまうため、ここでは使えない）。

### 5.4 DDL（`schema.sql`）

```sql
CREATE TABLE IF NOT EXISTS todos (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    completed   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
```

### 5.5 `TodoMapper` インターフェース設計（SQL アノテーション方式）

```java
@Mapper
public interface TodoMapper {

    @Select("SELECT * FROM todos ORDER BY id")
    List<Todo> findAll();

    @Select("SELECT * FROM todos WHERE completed = #{completed} ORDER BY id")
    List<Todo> findByCompleted(@Param("completed") boolean completed);

    @Select("SELECT * FROM todos WHERE id = #{id}")
    Optional<Todo> findById(@Param("id") Long id);

    @Insert("""
            INSERT INTO todos (title, description, completed, created_at, updated_at)
            VALUES (#{title}, #{description}, #{completed}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Todo todo);

    @Update("""
            UPDATE todos
               SET title       = #{title},
                   description = #{description},
                   completed   = #{completed},
                   updated_at  = #{updatedAt}
             WHERE id = #{id}
            """)
    void update(Todo todo);

    @Delete("DELETE FROM todos WHERE id = #{id}")
    void deleteById(@Param("id") Long id);

    @Delete("DELETE FROM todos WHERE completed = TRUE")
    int deleteCompleted();
}
```

> **学習ポイント**:
> - `@Mapper` を付与すると mybatis-spring-boot-starter が自動的にスキャン・Bean 化する。
> - `map-underscore-to-camel-case=true` 設定により `created_at` → `createdAt` が自動マッピングされる。
> - `Optional<Todo>` 戻り値は MyBatis 3.5+ 以降サポート。**戻り値専用** に使い、引数やフィールドには使わないのが原則。
> - SQL は Java 25 の **Text Blocks** (`"""..."""`) で記述すると複数行 SQL の可読性が高まる。
> - `deleteCompleted` は **完了フラグを条件にした集合削除**。戻り値 `int` は MyBatis の標準仕様で、
>   削除された行数が返る（0 件でも例外にならない）。Service / Controller でこの値を使うかは
>   設計判断（本アプリではログ用途のみで API レスポンスには含めない。理由は §6.3 参照）。
> - `deleteCompleted` を `deleteByCompleted(boolean)` のように **パラメータ化しなかった理由**:
>   `?completed=false`（未完了をすべて削除）は実運用で誤操作リスクが高く、UI からも公開しないため、
>   メソッドレベルで「完了済みだけを消す」と意図を固定し、SQL リテラルで `TRUE` を埋め込む方が安全。

---

## 6. REST API 仕様

Base URL: `http://localhost:8080/api/v1/todos`

### 6.1 エンドポイント一覧

| メソッド | パス | 説明 | 成功コード |
|---------|------|------|-----------|
| `GET` | `/api/v1/todos` | 全件取得（完了フィルター可） | 200 |
| `GET` | `/api/v1/todos/{id}` | 1件取得 | 200 |
| `POST` | `/api/v1/todos` | 新規作成 | 201 |
| `PATCH` | `/api/v1/todos/{id}` | 部分更新（指定フィールドのみ） | 200 |
| `DELETE` | `/api/v1/todos/{id}` | 削除（単一） | 204 |
| `DELETE` | `/api/v1/todos?completed=true` | 完了済みを一括削除 | 204 |

> **`PUT` ではなく `PATCH`** を採用する理由：null フィールドを無視して指定分だけ更新する操作は
> REST セマンティクス上 `PATCH` が正しい（`PUT` は本来「リソース全体の置換」）。

### 6.2 エラーレスポンス形式（`ProblemDetail` / RFC 7807）

エラーは Spring Framework 標準の `ProblemDetail` で返す。`Content-Type: application/problem+json`。

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Todo not found: 1",
  "instance": "/api/v1/todos/1"
}
```

バリデーションエラー（400）の場合は `errors` のような独自フィールドを `setProperty` で追加する。
`@RestControllerAdvice` で `MethodArgumentNotValidException` を捕捉し、各フィールドエラーを整形する。

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/v1/todos",
  "errors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "description", "message": "size must be between 0 and 1000" }
  ]
}
```

### 6.3 リクエスト / レスポンス詳細

#### GET `/api/v1/todos`
```
クエリパラメータ: ?completed=true|false (省略時: 全件)

レスポンス 200:
[
  { "id": 1, "title": "買い物", "description": "牛乳", "completed": false,
    "createdAt": "2026-05-28T10:00:00", "updatedAt": "2026-05-28T10:00:00" }
]
```

> **実装方針**: Controller は `@RequestParam(required = false) Boolean completed` を受け、
> Service 層で `completed == null` のときは `mapper.findAll()`、それ以外は
> `mapper.findByCompleted(completed)` に振り分ける。

#### GET `/api/v1/todos/{id}`
```
レスポンス 200: { "id": 1, ... }
レスポンス 404: ProblemDetail (status=404, detail="Todo not found: 1")
```

#### POST `/api/v1/todos`
```
リクエスト:
{ "title": "買い物",          // 必須 (空文字不可、255文字以内)
  "description": "牛乳とパン" // 任意 (1000文字以内) }

レスポンス 201:
  ヘッダー: Location: /api/v1/todos/2
  ボディ : { "id": 2, "title": "買い物", "completed": false, ... }
レスポンス 400: ProblemDetail (status=400, detail="Validation failed", errors=[...])
```

> **`Location` ヘッダー**: REST 慣習（RFC 9110）に従い、作成したリソースの URI を返す。
> Spring MVC では `ResponseEntity.created(URI).body(...)` で実装し、URI 構築は
> `ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()`
> を用いるのが典型。

#### PATCH `/api/v1/todos/{id}`
```
リクエスト (null フィールドは無視、指定分のみ更新):
{ "title": "買い物 (更新)", "completed": true }

レスポンス 200: { "id": 1, "title": "買い物 (更新)", "completed": true, ... }
レスポンス 404: ProblemDetail (status=404)
```

#### DELETE `/api/v1/todos/{id}`
```
レスポンス 204: (ボディなし)
レスポンス 404: ProblemDetail (status=404)
```

> **DELETE の冪等性に関する設計判断**: 本アプリでは「存在しない `id` への DELETE は 404」を採用する。
> HTTP の冪等性（複数回呼び出しても結果が同じ）を厳密に重視するなら 204 を返す設計もあり得るが、
> 学習目的では「リソースの存在チェック → 例外スロー → `@ExceptionHandler` で 404 変換」という
> Spring の典型フローを GET と同じ形で体験できる方を優先する。

#### DELETE `/api/v1/todos?completed=true`
```
クエリパラメータ: completed (必須、本 MVP では true のみ受け付ける)

レスポンス 204: (ボディなし。マッチ 0 件でも 204)
レスポンス 400: completed が未指定 / true 以外の場合の ProblemDetail
```

> **設計判断 1 — パス形式の選択**: 「完了済みを一括削除」を表現する方法は複数あり得る：
>
> | 案 | 例 | 評価 |
> |---|---|---|
> | A. クエリフィルター付き DELETE | `DELETE /api/v1/todos?completed=true` | **採用**。GET の `?completed=...` と対称で「同じフィルターで取得 → 削除」というメンタルモデルが一貫する |
> | B. サブリソース | `DELETE /api/v1/todos/completed` | 意図は明確だが、`completed` がリソース名なのか状態名なのか曖昧。フィルターが増えると破綻 |
> | C. アクション風エンドポイント | `POST /api/v1/todos/clear-completed` | REST セマンティクスから外れる（削除を POST で表現）。学習教材としては避けたい |
>
> **設計判断 2 — マッチ 0 件でも 204 を返す**: 単一 DELETE が 404 を返すのと対照的に、
> コレクション削除では「条件にマッチしたものをすべて消す」という操作の性質上、
> マッチ 0 件は **エラーではなく正常な空集合操作**。冪等性（2 回呼んでも同じ状態に収束）も自然に満たされる。
> この **「単一リソース DELETE」と「コレクション DELETE」のエラー意味論の違い** は重要な学習論点。
>
> **設計判断 3 — レスポンスボディに削除件数を含めない**: 「削除件数 N を返す」設計もあり得るが、
> 本 MVP のフロントエンドは削除後に一覧を再取得するため不要。
> 「ボディなし = 204」の慣習を崩さない方を優先する。必要になった時点で 200 + ボディに切り替える。
>
> **設計判断 4 — `?completed=false` は受け付けない**: 形式的には「未完了をすべて削除」と
> 解釈可能だが、これは誤操作リスクが極めて高く、UI からも公開しない機能。
> 「`completed` パラメータは必須かつ `true` のみ許可」と仕様で明示し、それ以外は 400 で弾く。
> （Mapper メソッドを `deleteCompleted()` と固定名にしているのも同じ理由 — §5.5 参照）

---

## 7. 設定（application.properties）

```properties
# H2 file-based DB (./data/tododb に保存)
spring.datasource.url=jdbc:h2:file:./data/tododb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# schema.sql を起動時に実行する。
# file-based H2 は Spring Boot 上「embedded 扱い」されないため、
# デフォルト (embedded) のままだと schema.sql が実行されない。明示的に always を指定する。
# CREATE TABLE IF NOT EXISTS としているため再実行しても冪等。
spring.sql.init.mode=always

# MyBatis: created_at → createdAt の自動マッピングを有効化
mybatis.configuration.map-underscore-to-camel-case=true

# MyBatis: 実行 SQL とバインドパラメータをログ出力（学習用）。
# Mapper インターフェースのパッケージに対して DEBUG を有効化する。
logging.level.io.github.futomaru.todoapp.mapper=DEBUG

# Virtual Threads (JDK 21+, Spring Boot 3.2+)
# Tomcat のリクエスト処理スレッドを仮想スレッドに切り替える
spring.threads.virtual.enabled=true

# H2 コンソール（ブラウザ確認用 / 開発専用）
# 本番では絶対に無効化すること（後述のプロファイル分離を参照）
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

> **プロファイル分離の指針（発展課題）**: 上記の `spring.h2.console.*` のように
> 開発時のみ有効化したい設定は、`application-dev.properties` / `application-prod.properties`
> に分離し、起動時に `--spring.profiles.active=dev` で切り替えるのが Spring Boot の典型構成。
> 本リポジトリの最初の実装では単一の `application.properties` で進め、
> プロファイル分離は学習の次ステップとして取り組むとよい。

---

## 8. 依存関係（build.gradle）

> Spring Boot 4.0.x（Spring Framework 7.0 / Jakarta EE 11）、Gradle 9 を前提とする。
> MyBatis Spring Boot Starter は **Spring Boot 4 対応版** を使用する必要がある。
> 最新バージョンは https://github.com/mybatis/spring-boot-starter のリリースを確認すること。

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'           // Spring MVC
    // ↓ Spring Boot 4 対応版（バージョンは要確認）
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-validation'    // バリデーション
    runtimeOnly    'com.h2database:h2'                                          // H2 組み込みDB

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.0'
    testRuntimeOnly    'org.junit.platform:junit-platform-launcher'
}
```

`gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
```

---

## 9. 実装ステップ

| # | 作業 | ファイル |
|---|------|---------|
| 1 | 依存関係追加 | `build.gradle` |
| 2 | DB・MyBatis・Virtual Threads 設定 | `application.properties` |
| 3 | DDL 作成 | `resources/schema.sql` |
| 4 | エンティティ実装（mutable POJO） | `entity/Todo.java` |
| 5 | マッパー実装（SQL アノテーション + Text Blocks） | `mapper/TodoMapper.java` |
| 6 | DTO 実装（3 つの `record`） | `dto/*.java` |
| 7 | カスタム例外実装 | `exception/TodoNotFoundException.java` |
| 8 | サービス実装（`@Transactional` 適用） | `service/TodoService.java` |
| 9 | コントローラー実装 | `controller/TodoController.java` |
| 10 | 例外ハンドラー実装（`ProblemDetail` で返却、`MethodArgumentNotValidException` も捕捉） | `exception/GlobalExceptionHandler.java` |
| 11 | フロントエンド実装 | `static/index.html`, `static/app.js`, `static/style.css` |
| 12 | テスト実装（Mapper / Service / Controller の 3 層） | `src/test/java/.../**Test.java` |
| 13 | （発展）プロファイル分離 | `application-dev.properties` / `application-prod.properties` |

---

## 10. フロントエンド設計

### 10.1 ファイル構成

| ファイル | 役割 |
|---------|------|
| `static/index.html` | 1ページ構成のUI（TODO一覧・追加フォーム） |
| `static/app.js` | `fetch()` で REST API を呼び出す純粋な JavaScript |
| `static/style.css` | 最小限のスタイル定義 |

### 10.2 UI 機能

| 機能 | 操作 |
|------|------|
| TODO 一覧表示 | ページ読み込み時に全件取得して描画 |
| TODO 追加 | タイトル入力＋送信ボタンで POST |
| 完了トグル | チェックボックスで PATCH（`completed` の反転） |
| TODO 削除 | 削除ボタンで DELETE |
| 完了フィルター | 「全件 / 未完了 / 完了」タブで GET `?completed=` |
| 完了済み一括削除 | 「完了済みを削除」ボタンで DELETE `?completed=true` |

### 10.3 app.js 構成

```
const API = '/api/v1/todos';

loadTodos(filter)    ← GET    /api/v1/todos[?completed=]
createTodo(title)    ← POST   /api/v1/todos
toggleTodo(id, todo) ← PATCH  /api/v1/todos/{id}
deleteTodo(id)       ← DELETE /api/v1/todos/{id}
clearCompleted()     ← DELETE /api/v1/todos?completed=true
renderTodos(list)    ← DOM 更新
```

---

## 11. テスト戦略

Spring Boot のテスト用スライス（部分起動）を活用し、3 層を独立してテストする。

| 層 | テスト種別 | 主な使用クラス・アノテーション | 観点 |
|----|-----------|----------------------------|------|
| Mapper | DB 統合テスト | `@MybatisTest` | SQL が H2 上で正しく動くか |
| Service | 単体テスト | JUnit 5 + Mockito | ビジネスロジック、例外スロー、`Clock` 注入による時刻固定 |
| Controller | Web 層テスト | `@WebMvcTest` + `MockMvc` | HTTP マッピング、バリデーション、ステータスコード、`ProblemDetail` の形式 |
| 全体 | 結合テスト | `@SpringBootTest` | エンドツーエンドの動作確認 |

> **学習ポイント**: Spring Boot のテスト用スライスは **必要な Bean だけを起動** することで
> テストを高速化する仕組み。`@SpringBootTest` だけに頼らず、層ごとに適切なスライスを選ぶ感覚を身に付ける。
>
> **`@MybatisTest` の DB に関する注意**: `@MybatisTest` はデフォルトで本番の DataSource を
> in-memory の embedded DB（H2 など）に **置換** する。本番の file-based H2 と
> ストレージモードが異なる点を把握しておくこと。本番と同じ file-based DB でテストしたい場合は
> `@AutoConfigureTestDatabase(replace = Replace.NONE)` を併用する。

---

## 12. 動作確認

```bash
# アプリ起動
./gradlew bootRun

# ブラウザでフロントエンドを開く
# http://localhost:8080/

# 1. 作成
curl -X POST http://localhost:8080/api/v1/todos \
  -H "Content-Type: application/json" \
  -d '{"title":"買い物","description":"牛乳とパン"}'

# 2. 全件取得
curl http://localhost:8080/api/v1/todos

# 3. 未完了のみ取得
curl "http://localhost:8080/api/v1/todos?completed=false"

# 4. 1件取得
curl http://localhost:8080/api/v1/todos/1

# 5. 部分更新（完了フラグON） ← PATCH
curl -X PATCH http://localhost:8080/api/v1/todos/1 \
  -H "Content-Type: application/json" \
  -d '{"completed":true}'

# 6. 削除（単一）
curl -X DELETE http://localhost:8080/api/v1/todos/1

# 7. 完了済みを一括削除（マッチ 0 件でも 204）
curl -X DELETE "http://localhost:8080/api/v1/todos?completed=true"

# H2 コンソール（ブラウザ、開発専用）
# URL: http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:file:./data/tododb
```
