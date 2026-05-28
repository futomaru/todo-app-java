# todo-app-java

Java・Spring Boot・Gradle を学ぶための Todo REST API 学習プロジェクトです。

## 技術スタック

| 項目 | 技術 |
|---|---|
| 言語 | Java 25 |
| フレームワーク | Spring Boot 4.0.x / Spring Framework 7.0 |
| DB アクセス | MyBatis |
| データベース | H2（ファイルベース） |
| ビルド | Gradle 9 |

## アーキテクチャ

```
TodoController → TodoService → TodoMapper → H2
```

## セットアップ

**必要環境:** JDK 25

```bash
./gradlew bootRun
```

起動後、<http://localhost:8080> でアクセスできます。

## ビルド・テスト

```bash
./gradlew build   # ビルド + テスト
./gradlew test    # テスト実行
```

## ディレクトリ構成

```
src/
  main/
    java/       # アプリケーションコード（controller / service / mapper / entity / dto / exception）
    resources/  # application.properties・schema.sql・静的ファイル
  test/
    java/       # ユニットテスト・統合テスト
doc/
  api.yaml            # OpenAPI 仕様
  architecture.md     # アーキテクチャガイドライン
  design.md           # 設計方針
  coding-standards.md # コーディング規約
  testing-guide.md    # テスト方針
  CONTRIBUTING.md     # コントリビューションガイド
```

## ドキュメント

- [アーキテクチャ](doc/architecture.md)
- [設計](doc/design.md)
- [API 仕様](doc/api.yaml)
- [コーディング規約](doc/coding-standards.md)
- [テストガイド](doc/testing-guide.md)
- [コントリビューション](doc/CONTRIBUTING.md)
