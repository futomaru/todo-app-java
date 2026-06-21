# アーキテクチャガイドライン

## 概要

シンプルなレイヤードアーキテクチャ。Spring MVC / MyBatis / H2 の学習が目的。

```
Controller → Service → Mapper → DB
                ↓
          Entity / DTO（横断利用）
```

## ディレクトリ構成

```
io.github.futomaru.todoapp/
├── controller/       ← REST エンドポイント
├── service/          ← ビジネスロジック
├── mapper/           ← MyBatis @Mapper
├── entity/           ← DB と対応する可変 POJO
├── dto/              ← API 入出力（不変 record）
└── exception/        ← カスタム例外 + ProblemDetail 変換
```

静的フロントエンド: `src/main/resources/static/`

## レイヤー責務

| レイヤー | 責務 | 禁止事項 |
| --- | --- | --- |
| Controller | HTTP マッピング・`@Valid` 委譲・ステータスコード組み立て | ビジネスロジック |
| Service | ユースケース・`@Transactional` 境界・Entity↔DTO 変換・例外スロー | — |
| Mapper | SQL（`@Select`/`@Insert`/`@Update`/`@Delete`） | Java での分岐・加工 |

## 設計原則

**Entity と DTO の分離**
- Entity: 可変 POJO（MyBatis が setter で注入）
- DTO: 不変 record（API 境界）
- 変換: `TodoResponse.from(Todo)` などの static factory に集約

**トランザクション境界**
- 書き込み: `@Transactional`
- 読み取り: `@Transactional(readOnly = true)`
- Controller / Mapper には付けない

**時刻制御**
- Service に `Clock` を DI し `LocalDateTime.now(clock)` を使う
- テストでは `Clock.fixed(...)` で固定できる

**ID 戦略**
- DB の `AUTO_INCREMENT` で採番
- `@Options(useGeneratedKeys = true, keyProperty = "id")` で Entity に書き戻し

**エラーハンドリング**
- Service で業務例外をスロー → `@RestControllerAdvice` で `ProblemDetail`（RFC 7807）に変換
- `Content-Type: application/problem+json`

## 技術スタック

| 項目 | 技術 |
| --- | --- |
| 言語 | Java 25 |
| フレームワーク | Spring Boot 4.1.x / Spring Framework 7.1.x |
| Web 層 | Spring MVC |
| DB アクセス | MyBatis |
| DB | H2（file-based） |
| バリデーション | Jakarta Bean Validation |
| 並行処理 | Virtual Threads（JDK 21 で GA、本プロジェクトは JDK 25 で利用） |
| ボイラープレート削減 | Lombok（Entity のみ `@Getter`/`@Setter`） |
| フロントエンド | HTML / CSS / Vanilla JS |
| テスト | JUnit 5 + Mockito / `@WebMvcTest` / `@MybatisTest` |
| ビルド | Gradle 9 |
