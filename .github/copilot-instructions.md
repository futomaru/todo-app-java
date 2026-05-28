# Copilot Review Instructions

## プロジェクト概要

学習目的の Todo 管理 REST API。Spring Boot + MyBatis + H2 によるレイヤードアーキテクチャの理解を目的とする。

## アーキテクチャ

シンプルなレイヤードアーキテクチャ。

```
Controller → Service → Mapper → H2
                ↓
          Entity / DTO（横断利用）
```

| レイヤー | 責務 |
| --- | --- |
| Controller | HTTP マッピング、バリデーション委譲、ステータスコード組み立て |
| Service | ユースケース、`@Transactional` 境界、Entity↔DTO 変換、例外スロー |
| Mapper | MyBatis による SQL 実行（`@Select` / `@Insert` など） |
| Entity | DB と対応する可変 POJO |
| DTO | API 入出力（不変 record） |

フロントエンド: `src/main/resources/static/`（Vanilla JS SPA）

## 技術スタック

| 項目 | 技術 |
| --- | --- |
| 言語 | Java 25 |
| フレームワーク | Spring Boot 4.0.x / Spring Framework 7.0 |
| Web 層 | Spring MVC |
| DB アクセス | MyBatis |
| DB | H2（file-based） |
| バリデーション | Jakarta Bean Validation |
| ボイラープレート削減 | Lombok |
| テスト | JUnit 5 / Mockito / `@WebMvcTest` / `@MybatisTest` |
| ビルド | Gradle 9 |

## 重点レビュー項目

- **責務分離**: Controller / Service / Mapper の役割が混在していないか
- **ライブラリの活用**: Lombok・MyBatis・Spring の機能を適切に利用しているか
- **Spring**: DI の活用（`@Autowired` ではなくコンストラクタインジェクションを推奨）など、ベストプラクティスに沿っているか
- **命名・可読性**: 意図が読み取れる命名か、不要なコメントがないか。誰が見ても理解しやすいか
- **テスト**: 変更箇所に対応するテストがあり、境界値・異常系も考慮されているか

## レビューのトーン

学習目的のため、指摘には **理由・背景** を必ず添えてください。
「直してください」ではなく、**なぜそうすべきか** を説明する形でお願いします。

## 参照ドキュメント

- [doc/architecture.md](../doc/architecture.md) — 全体構成
- [doc/design.md](../doc/design.md) — 設計判断の理由
- [doc/coding-standards.md](../doc/coding-standards.md) — コーディング規約
- [doc/testing-guide.md](../doc/testing-guide.md) — テスト方針
