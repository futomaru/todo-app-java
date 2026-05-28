# Copilot Review Instructions

学習目的の Spring Boot + MyBatis REST API（Todoアプリ）です。
プルリクエストのレビューでは以下を重点的に確認してください。

## プロジェクト構成

```
TodoController → TodoService → TodoMapper → H2
```

フロントエンド: `src/main/resources/static/`（Vanilla JS SPA）

## 重点レビュー項目

- **責務分離**: Controller / Service / Mapper の役割が混在していないか
- **ライブラリの活用**: LombokやMybatis, Springの機能を適切に利用しているか
- **Spring**: DIの活用（`@Autowired` ではなくコンストラクタインジェクションを推奨）など。Springのベストプラクティスに沿っているか
- **命名・可読性**: 意図が読み取れる命名か、不要なコメントがないか。コードは誰が見ても理解しやすいものになっているか。
- **テスト**: 変更箇所に対応するテストがあり、境界値・異常系も考慮されているか

## レビューのトーン

学習目的のため、指摘には **理由・背景** を必ず添えてください。
「直してください」ではなく、**なぜそうすべきか** を説明する形でお願いします。

## 参照ドキュメント

- [doc/architecture.md](../doc/architecture.md) — 全体構成
- [doc/design.md](../doc/design.md) — 設計判断の理由
- [doc/coding-standards.md](../doc/coding-standards.md) — コーディング規約
- [doc/testing-guide.md](../doc/testing-guide.md) — テスト方針
