# アーキテクチャガイドライン

Spring MVC / MyBatis / H2 を学習するためのシンプルなレイヤードアーキテクチャ。
本ドキュメントは **全体構成・レイヤー責務・技術スタックの要約**（What）。
**「なぜこう設計したか」（Why）は [design.md](design.md) を参照**。

## ドキュメントの読み方

初学者は次の順序で読むと、設計の意図 → 仕様 → 実装に進める：

1. **architecture.md**（本書）— What: 全体像
2. [design.md](design.md) — Why: 設計判断の理由
3. [api.yaml](api.yaml) — How (API): REST 仕様
4. [coding-standards.md](coding-standards.md) — How (Code): 規約
5. [testing-guide.md](testing-guide.md) — How (Test): 戦略とサンプル
6. [git.md](git.md) — How (Git): 運用ルール

## 全体構成

```
ブラウザ (static/index.html + app.js)
    │  fetch() → REST 呼び出し
    ▼
TodoController (REST) → TodoService (@Transactional) → TodoMapper (@Mapper) → H2 (file-based)
                                  │
                                  ▼
                          Entity / DTO（横断利用）
```

例外は `GlobalExceptionHandler`（`@RestControllerAdvice`）が捕捉し、
**`ProblemDetail`（RFC 7807）** として `Content-Type: application/problem+json` で応答する。

## ディレクトリ構成

```
io.github.futomaru.todoapp/
├── controller/       ← REST エンドポイント
├── service/          ← ビジネスロジック・@Transactional 境界
├── mapper/           ← MyBatis @Mapper
├── entity/           ← DB と対応する可変 POJO
├── dto/              ← API 入出力（不変 record）
└── exception/        ← カスタム例外 + ProblemDetail 変換
```

静的フロントエンド: `src/main/resources/static/`

## レイヤー責務（要約）

| レイヤー | 責務 | 禁止 |
| --- | --- | --- |
| Controller | HTTP マッピング・`@Valid` 委譲・ステータスコード組み立て | ビジネスロジック |
| Service | ユースケース・`@Transactional` 境界・Entity↔DTO 変換・例外スロー | HTTP 関連の関心事 |
| Mapper | SQL（`@Select`/`@Insert`/`@Update`/`@Delete`） | Java での分岐・加工 |

> 各レイヤーの **設計判断の根拠** は [design.md §3.2](design.md#32-レイヤー責務) を参照。

## 主要な設計原則（リンク集）

| 原則 | 詳細 |
| --- | --- |
| Entity（可変 POJO）と DTO（不変 record）の分離 | [design.md §5.2 / §5.3](design.md#52-todo-エンティティ設計) |
| `@Transactional(readOnly=true)` をクラス級デフォルトに | [design.md §3.2](design.md#32-レイヤー責務) |
| 時刻は `Clock` を DI して `LocalDateTime.now(clock)` | [design.md §5.1](design.md#51-todos-テーブル) |
| ID は DB AUTO_INCREMENT + `@Options(useGeneratedKeys = true)` | [design.md §5.5](design.md#55-todomapper-インターフェース設計sql-アノテーション方式) |
| エラーは `ProblemDetail`（RFC 7807） | [design.md §6.2](design.md#62-エラーレスポンス形式problemdetail--rfc-7807) |

## 技術スタック（要約）

| 項目 | 技術 |
| --- | --- |
| 言語 | Java 25 |
| フレームワーク | Spring Boot 4.1.x / Spring Framework 7.1.x |
| Web 層 | Spring MVC |
| DB アクセス | MyBatis (`mybatis-spring-boot-starter` 4.0.x) |
| DB | H2（file-based） |
| バリデーション | Jakarta Bean Validation |
| エラーレスポンス | `ProblemDetail`（RFC 7807） |
| 並行処理 | Virtual Threads（`spring.threads.virtual.enabled=true`） |
| null 表現 | JSpecify（`org.jspecify.annotations.@Nullable`） |
| ボイラープレート削減 | Lombok（Entity のみ `@Getter` `@Setter` `@NoArgsConstructor`） |
| フロントエンド | HTML / CSS / Vanilla JS |
| テスト | JUnit 5 / Mockito / `@MybatisTest` / `RestTestClient` |
| ビルド | Gradle 9 |

> 各技術の **採用理由** は [design.md §2](design.md#2-技術スタック) を参照。
