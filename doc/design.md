# TODO アプリ 設計書

## 1. 概要

Spring MVC を用いた REST API 形式の TODO 管理バックエンドアプリケーション。  
組み込みデータベース（H2）でデータを永続化し、基本的な CRUD 操作を提供する。

本アプリは **Spring Boot 4.1 / Spring Framework 7.1 / JDK 25** を学習することを主目的とし、
これらのバージョンで利用可能な機能（`ProblemDetail`、Virtual Threads、`record` DTO、Text Blocks、
pattern matching for `instanceof`／`switch` 等）を積極的に採用する。

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

## 2. 技術スタック（採用理由）

> 技術一覧の要約表は [architecture.md](architecture.md#技術スタック要約) を参照。本節は **採用理由** に絞る。

| 項目 | 採用技術 | 採用理由 |
|------|----------|---------|
| 言語 | Java 25 | プロジェクト指定。`record` / Text Blocks / pattern matching を学習対象として活用 |
| フレームワーク | Spring Boot 4.1.x + Spring MVC | DispatcherServlet ベースの標準的 Web レイヤー |
| コアフレームワーク | Spring Framework 7.1 | Spring Boot 4.1 に同梱、Jakarta EE 11 ベース |
| データベース | H2 (file-based) | 組み込み DB、再起動後もデータが残るため学習で扱いやすい |
| ORM | MyBatis | SQL を明示的に書ける。シンプルな CRUD に適し、隠蔽が少ないため何が起きているか理解しやすい |
| エラーレスポンス | `ProblemDetail`（RFC 7807） | Spring Framework 6 で導入された標準。`@RestControllerAdvice` で集約しやすい |
| 並行処理 | Virtual Threads | JDK 21 で GA。`spring.threads.virtual.enabled=true` で有効化できる |
| null 表現 | JSpecify（`@Nullable`） | Spring Framework 7 が公式採用。`jakarta.annotation.@Nullable` からの移行先 |
| ボイラープレート削減 | Lombok | Entity の getter/setter/コンストラクタ生成（DTO record には不要） |
| フロントエンド | HTML / CSS / Alpine.js (CDN) | ビルドツール不要・宣言的バインディングが書けて、学習用 SPA を最小構成で組める |

### 2.1 主な学習ポイント

- **JDK 25**: `record` を DTO に採用し、不変オブジェクト（DTO）と可変 POJO（Entity）の使い分けを学ぶ。SQL は **Text Blocks**（Java 15 で正式機能化）で記述。`instanceof` / `switch` の pattern matching（Java 16 / 21 で正式機能化）も必要に応じて活用する。
- **Spring Framework 7.1**: `ProblemDetail` ベースのエラーハンドリング、Virtual Threads サポート、Jakarta EE 11（`jakarta.*` 名前空間）が前提。
- **Spring Boot 4.1 / Spring Framework 7.1**: 自動構成、`@Transactional` 境界、テスト用スライス（`@MybatisTest`）の使い分け。Web 層テストには Spring Framework 7 で導入された **`RestTestClient`** を standalone モードで用いる（詳細は §11 参照）。

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

| レイヤー | クラス | 責務 | 根拠 |
|---------|--------|------|------|
| Controller | `TodoController` | HTTP メソッド・パスのマッピング、`@Valid` 委譲、ステータスコード組み立て | Web プロトコルの関心事を Service に持ち込まない |
| Service | `TodoService` | ビジネスルール、`@Transactional` 制御、`Clock` を用いた `created_at`/`updated_at` 設定、Entity ↔ DTO 変換、例外スロー | Web プロトコルにも DB アクセス手段にも依存しない中心層 |
| Mapper | `TodoMapper` | `@Mapper` インターフェース＋ SQL アノテーションで DB CRUD | SQL を明示的に書き、Java での分岐・加工をしない |
| Entity | `Todo` | DB テーブルと対応する POJO（MyBatis が setter で値を注入するため可変クラス） | フレームワーク（MyBatis）の制約と整合 |
| DTO | `TodoCreateRequest` / `TodoUpdateRequest` / `TodoResponse` | API の入出力形式定義（**Java `record` で不変**）。Entity ↔ DTO 変換は DTO 側の static factory（例: `TodoResponse.from(Todo)`）に集約 | API 境界の不変性表現 |
| Exception | `TodoNotFoundException` / `GlobalExceptionHandler` | カスタム例外と `ProblemDetail` への変換 | 業務例外を HTTP レスポンスに変換する責務を 1 箇所に集中 |

> **トランザクション境界の指針**: `TodoService` のクラス級に `@Transactional(readOnly = true)` を付け、
> 書き込みメソッド（`create` / `update` / `delete` / `deleteCompleted`）のみメソッド級で `@Transactional` を上書きする。
> 「**デフォルトは安全（readOnly）、書き込みメソッドが明示的に目立つ**」設計になる。
> `readOnly = true` は MyBatis でも JDBC ドライバへヒントが渡るほか、「意図の明示」というドキュメント効果が大きい。

---

## 4. プロジェクト構成

ディレクトリ構成（パッケージレイアウト）は [architecture.md](architecture.md#ディレクトリ構成) に一元化。
本書ではテスト構成のみ補足する：

```
src/test/java/io/github/futomaru/todoapp/
├── TodoappApplicationTests.java         # 結合テスト (@SpringBootTest RANDOM_PORT + 実 HTTP 往復)
├── controller/TodoControllerTest.java   # RestTestClient + Mockito (standalone)
├── service/TodoServiceTest.java         # JUnit 5 + Mockito
└── mapper/TodoMapperTest.java           # @MybatisTest
src/test/resources/
└── application-test.properties          # in-memory H2 プロファイル（結合テスト用）
```

各テストの構成意図は [§11](#11-テスト戦略) と [testing-guide.md](testing-guide.md) を参照。

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
> 本番では `TodoappApplication` に `@Bean Clock systemClock() { return Clock.systemDefaultZone(); }` を登録し、
> テストでは `Clock.fixed(...)` を注入することで時刻を固定して検証できる
> （Bean 定義のコードは [TodoappApplication.java](../src/main/java/io/github/futomaru/todoapp/TodoappApplication.java) 参照）。

### 5.2 `Todo` エンティティ設計

MyBatis は setter 経由でカラム値を注入するため、Java `record` は使えない。
**ミュータブルな通常クラス** として定義する（DTO とは対照的）。
getter/setter の手書きは冗長になるため、**Lombok の `@Getter` / `@Setter`** で生成する。

```java
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class Todo {
    private Long id;
    private String title;
    private String description;
    private boolean completed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

> **なぜ Entity に Lombok（`@Getter` `@Setter` `@NoArgsConstructor`）を使うのか**: MyBatis は
> リフレクションで Entity をインスタンス化し（→ 引数なしコンストラクタが必要）、setter 経由で
> 各カラム値を注入する。`@Getter` `@Setter` `@NoArgsConstructor` をフィールド宣言だけで賄うと、
> 設計意図（「これは MyBatis が触る可変 POJO」）が一目で読める。
>
> **なぜ DTO（`record`）には Lombok を使わないのか**: `record` は宣言だけで
> 不変フィールド・アクセサ（`title()` 形式）・`equals` / `hashCode` / `toString` を自動生成する。
> Lombok を重ねる必要がない。むしろ「DTO は不変・Entity は可変」という設計意図を
> `record` vs `@Getter @Setter` の対比で表現できる。
>
> **`@Data` を使わない理由**: `@Data` は `@EqualsAndHashCode` も含む。
> 可変 Entity の `equals` が ID 確定前後で変化するなどの落とし穴があるため、
> 学習目的では明示的に `@Getter` `@Setter` のみに留める方が安全。

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
すべての ProblemDetail に `instance` としてリクエスト URI をセットする。

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Todo not found: 1",
  "instance": "/api/v1/todos/1"
}
```

バリデーション失敗（400）は Spring Boot が自動で生成する ProblemDetail をそのまま返す。

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/v1/todos"
}
```

#### `GlobalExceptionHandler` の構造

| 担当 | 対象 | 実装方法 |
|------|------|---------|
| 自前ハンドラ | `TodoNotFoundException`（業務例外） | `@ExceptionHandler` で 404 ProblemDetail を組み立てる |
| 親クラスに委譲 | `MethodArgumentNotValidException` / `HandlerMethodValidationException` / `MissingServletRequestParameterException` 等の Spring 標準例外 | `ResponseEntityExceptionHandler` を継承し、Spring 6+ デフォルトの ProblemDetail 応答に任せる |

> **学習ポイント — なぜ `ResponseEntityExceptionHandler` を継承するのか**:
> Spring 6 から MVC の標準例外（`@Valid` 失敗、必須クエリ欠落、メッセージ変換エラー等）は
> 親クラス側で **デフォルトで ProblemDetail に変換** されるようになった。継承するだけで
> 多数の標準例外が RFC 7807 形式で返るため、業務例外（`TodoNotFoundException`）だけ自前で書けばよい。
>
> 「`@RequestBody @Valid` と `@PathVariable @Min` で投げられる例外型が違う」という Spring の仕様は
> 学習として重要だが、それを **アプリ側で吸収する必要は無くなった** のがこの設計の要点。

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
レスポンス 400: ProblemDetail (id が 0 以下のとき)
レスポンス 404: ProblemDetail (status=404, detail="Todo not found: 1")
```

> **`{id}` に `@Min(1)` を付ける理由**: ID は DB の `AUTO_INCREMENT` で 1 以上が採番されるため、
> 0 や負の値は構造的に存在し得ない。Controller の入口で `@PathVariable @Min(1) Long id` として
> 弾けば、Service / Mapper を呼ぶ前に 400 で短絡できる。
> Service に「ID は正の整数」という前提を持ち込まずに済むのが利点。

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

##### 設計判断の整理

| 論点 | 採用 | 理由 |
|------|------|------|
| パス形式 | `DELETE /api/v1/todos?completed=true` | GET の `?completed=...` と対称。「同じフィルターで取得 → 削除」のメンタルモデルが一貫 |
| マッチ 0 件のステータス | 204 | コレクション削除は「条件にマッチしたものを消す」操作。0 件は正常な空集合操作（冪等性も自然に成立） |
| レスポンスボディ | なし | フロントは削除後に一覧を再取得するため不要。「ボディなし = 204」の慣習を維持 |
| `?completed=false` | **受け付けない**（400） | 「未完了を一括削除」は誤操作リスク極大。UI からも公開しない |

##### 棄却した代替案

| 案 | 棄却理由 |
|---|---------|
| `DELETE /api/v1/todos/completed` | `completed` がリソース名か状態名か曖昧。フィルター増で破綻 |
| `POST /api/v1/todos/clear-completed` | REST セマンティクスから外れる（削除を POST で表現） |

> **学習ポイント — 単一 DELETE と コレクション DELETE のエラー意味論**:
> 単一 `DELETE /{id}` は 404、コレクション `DELETE ?completed=true` は 0 件でも 204。
> 「リソース指定の単一削除」と「条件指定の集合削除」では存在チェックの意味が違う、というのが要点。
>
> **`@AssertTrue` で `?completed=true` 以外を 400 にする**:
> Controller では `@RequestParam @AssertTrue boolean completed` と書くだけで、
> false や型不一致を Spring が `HandlerMethodValidationException` として投げる。
> Service に「`completed == true` チェック」を持ち込まずに済むのが利点（§6.2 参照）。

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

> **プロファイル分離（発展課題）**: 開発時のみ有効化したい設定（`spring.h2.console.*` 等）は、
> `application-dev.properties` / `application-prod.properties` に分離し
> `--spring.profiles.active=dev` で切り替えるのが Spring Boot の典型構成。本 MVP では単一ファイルで進める。

---

## 8. 依存関係（build.gradle）

> Spring Boot 4.1.x（Spring Framework 7.1 / Jakarta EE 11）、Gradle 9 を前提とする。
> MyBatis Spring Boot Starter は **Spring Boot 4 対応版** を使用する必要がある。
> 最新バージョンは https://github.com/mybatis/spring-boot-starter のリリースを確認すること。

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'com.diffplug.spotless' version '7.0.2'   // フォーマッタ。googleJavaFormat を使用
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'           // Spring MVC
    implementation 'org.springframework.boot:spring-boot-starter-validation'    // バリデーション
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0'  // Spring Boot 4 対応版
    // Spring Framework 7 / Boot 4 が公式採用した JSpecify の null 注釈
    // jakarta.annotation.@Nullable ではなく org.jspecify.annotations.@Nullable を使う
    implementation 'org.jspecify:jspecify:1.0.0'
    runtimeOnly    'com.h2database:h2'                                          // H2 組み込みDB

    testImplementation 'org.springframework.boot:spring-boot-starter-test'      // RestTestClient / AssertJ / JUnit5 / Mockito 同梱
    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.0'
    testRuntimeOnly    'org.junit.platform:junit-platform-launcher'

    // Lombok（Entity の getter/setter/コンストラクタ生成に利用）
    compileOnly         'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

> **JSpecify の `@Nullable`**: Spring Framework 7 / Boot 4 から、null 許容を示す標準が **JSpecify** に統一された。
> Service の `findAll(@Nullable Boolean completed)` のように、nullable な引数・戻り値に明示する。
> IDE / 静的解析（NullAway 等）が型情報として読める利点がある。

`gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
```

> **Spotless（フォーマッタ）を入れる理由**: コードスタイル（インデント・import 並び・末尾空白）の議論を
> ツールに任せ、レビューの焦点を設計に集中させるため。
> 本プロジェクトでは Google Java Format をベースに、未使用 import 除去・アノテーション整形・末尾改行を強制する
> （詳細は `build.gradle` の `spotless { ... }` ブロック参照）。
> 開発時は `./gradlew spotlessApply` で自動整形できる。


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
| 9 | コントローラー実装（`@Valid` 委譲、`@PathVariable @Min(1)`、`@RequestParam @AssertTrue`） | `controller/TodoController.java` |
| 10 | 例外ハンドラー実装（`ResponseEntityExceptionHandler` を継承。`TodoNotFoundException` のみ自前で 404 ProblemDetail に変換し、Spring 標準例外は親クラスのデフォルト処理に委譲） | `exception/GlobalExceptionHandler.java` |
| 11 | フロントエンド実装 | `static/index.html`, `static/app.js`, `static/style.css` |
| 12 | テスト実装（Mapper: `@MybatisTest` / Service: Mockito / Controller: `RestTestClient`） | `src/test/java/.../**Test.java` |
| 13 | （発展）プロファイル分離 | `application-dev.properties` / `application-prod.properties` |

---

## 10. フロントエンド設計

### 10.1 ファイル構成

| ファイル | 役割 |
|---------|------|
| `static/index.html` | UI 構造 + Alpine.js のディレクティブ（`x-data` / `x-model` / `x-for` 等）でデータバインディング |
| `static/app.js` | `Alpine.data('todoApp', () => ({...}))` で state とメソッドを登録 |
| `static/style.css` | 最小限のスタイル定義 |

Alpine.js 本体は `<script src="...alpinejs/cdn.min.js">` で CDN から読み込む。
ビルドツール（npm / webpack 等）は **一切使わず**、Spring Boot の静的配信だけで完結する。

> **Alpine.js を採用した理由**: Vanilla JS で `document.getElementById` や手動 DOM 更新を書くと、
> 状態とビューの同期コードがボイラープレートとして増えていく。Alpine.js は `x-model` / `x-for` /
> `x-show` などの宣言的バインディングで「状態 → ビュー」を自動同期できるため、学習用 SPA の
> コード量を最小化できる。フレームワーク学習コストもタグ属性数個分で済む。

### 10.2 UI 機能

| 機能 | 操作 |
|------|------|
| TODO 一覧表示 | ページ読み込み時 (`init()`) に `reload()` で全件取得 |
| TODO 追加 | 入力欄 + 「追加」ボタンの submit で `create()` → POST |
| 完了トグル | チェックボックスの change で `toggle(t)` → PATCH（`completed` 反転） |
| TODO 削除 | 「削除」ボタンの click で `remove(id)` → DELETE |
| 完了フィルター | 「すべて / 未完了 / 完了」タブで `filter` を切替えて `reload()` |
| 完了済み一括削除 | 「完了を消す →」ボタンで `clearCompleted()` → DELETE `?completed=true` |

### 10.3 `Alpine.data('todoApp')` の構成

```
const API = '/api/v1/todos';

Alpine.data('todoApp', () => ({
  // state
  todos:    [],                          // 表示中の Todo 一覧
  filter:   'all',                       // 'all' | 'active' | 'completed'
  newTitle: '',                          // 入力中のタイトル
  error:    null,                        // エラーメッセージ

  // lifecycle
  init() { this.reload(); },             // 初期描画

  // メソッド（呼び出す API）
  reload()         // GET    /api/v1/todos[?completed=true|false]   filter から組み立て
  create()         // POST   /api/v1/todos                           { title: newTitle }
  toggle(t)        // PATCH  /api/v1/todos/{t.id}                    { completed: !t.completed }
  remove(id)       // DELETE /api/v1/todos/{id}
  clearCompleted() // DELETE /api/v1/todos?completed=true

  // 共通ハンドラ
  handleError(res) // 4xx/5xx 応答を error にセット（ProblemDetail の detail を表示）
}))
```

UI 側は `index.html` の `x-data="todoApp"` 配下で、上記 state とメソッドを `x-model` / `@click` /
`x-for` / `x-text` 等から参照する。`x-cloak` で初期描画前のチラつきを抑えている。

---

## 11. テスト戦略

層ごとに最小のスライスを使う。**サンプルコードは [testing-guide.md](testing-guide.md) に集約**しているため、
ここでは「なぜその構成にしたか」のみ記す。

| 層 | テスト構成 | 設計判断 |
|----|-----------|---------|
| Mapper | `@MybatisTest` + 自動構成 H2 | DB 連動部分は本番に近い形（H2 への実 SQL 発行）で検証する。トランザクション境界はテストごとに自動ロールバック |
| Service | JUnit 5 + Mockito + `Clock.fixed(...)` | ビジネスロジックを純粋に検証する。Mapper を `@Mock` で差し替え、時刻を固定して `created_at`/`updated_at` の挙動を観察する |
| Controller | `RestTestClient.bindToController(...)` + `setControllerAdvice(...)` + Mockito | Web 層を **standalone** で起動（DispatcherServlet なし）。Service を `@Mock`、`GlobalExceptionHandler` を組み込み、Controller + ハンドラの協調をテストする |
| 結合（全体） | `@SpringBootTest(RANDOM_PORT)` + `@ActiveProfiles("test")` + `RestTestClient.bindToServer()` | 実サーバを起動し、`application-test.properties` の **in-memory H2** に対して実 HTTP で CRUD ハッピーパスを 1 本だけ流す。スライステストでは保証できない「Controller → Service → MyBatis → H2 の往復配線」を検証する |

> **`@WebMvcTest + MockMvc` ではなく `RestTestClient` を採用する理由**:
> Spring Framework 7 で導入された `RestTestClient` は WebTestClient 系の流れる API を持ち、
> standalone モードで Controller を組み立てるため Spring コンテキストの起動が要らない。
> `@WebMvcTest` よりさらに小さい単位で Controller を検証でき、テスト起動が速い。
>
> **`@MybatisTest` の DB に関する注意**: `@MybatisTest` はデフォルトで本番の DataSource を
> in-memory の embedded DB に **置換** する。本番の file-based H2 とストレージモードが異なる点に留意。
> 本番と同じ file-based DB でテストしたい場合は `@AutoConfigureTestDatabase(replace = Replace.NONE)` を併用する。
>
> **結合テストで in-memory H2 に差し替える理由**: 本番設定 (`jdbc:h2:file:./data/tododb`) を
> そのまま使うとテスト実行で本番 DB ファイルが汚れる。`application-test.properties` で
> `jdbc:h2:mem:tododb-it;DB_CLOSE_DELAY=-1` に上書きし、`@ActiveProfiles("test")` で読み込む。
> `DB_CLOSE_DELAY=-1` は「最後の接続が閉じても JVM 終了まで DB を維持」するためのオプションで、
> MyBatis のコネクションプールが接続を再取得した際にデータが消えるのを防ぐ。
>
> **Spring Boot 4 での結合テスト構成の注意**: Spring Boot 4 では `RestTestClient` の自動構成
> （`@AutoConfigureRestTestClient`）が別モジュール (`spring-boot-resttestclient`) に分離された。
> 依存を追加せずに済ませるため、本プロジェクトでは `@LocalServerPort` + `RestTestClient.bindToServer()`
> で手動構築している。

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
