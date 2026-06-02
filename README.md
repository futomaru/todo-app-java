# todo-app-java

Java・Spring Boot・MyBatis・H2 を使った Todo REST API の学習用プロジェクトです。
TodoMVC 相当のコア機能（追加・編集・完了切替・削除・完了フィルター・完了済み一括削除）に絞った
**MVP（最小機能版）** として実装します。スコープの詳細は [doc/design.md §1.1](doc/design.md) を参照。

## 技術スタック

| 項目 | 技術 |
|---|---|
| 言語 | Java 25 |
| フレームワーク | Spring Boot 4.0.x / Spring Framework 7.0 |
| DB アクセス | MyBatis |
| データベース | H2（ファイルベース） |
| ビルド | Gradle 9 |

## セットアップ

必要環境: JDK 25

```bash
./gradlew bootRun
```

起動後、<http://localhost:8080> にアクセスできます。

## ビルド・テスト

```bash
./gradlew build  # ビルド + テスト
./gradlew test   # テスト実行
```

## ディレクトリ構成

```text
src/
  main/
    java/io/github/futomaru/todoapp/
      TodoappApplication.java
      entity/Todo.java
    resources/
      application.properties
      schema.sql
  test/
    java/io/github/futomaru/todoapp/
      TodoappApplicationTests.java
doc/
  architecture.md
  design.md
  coding-standards.md
  testing-guide.md
  api.yaml
  CONTRIBUTING.md # 準備中
```

## ドキュメント

- [アーキテクチャ](doc/architecture.md)
- [設計](doc/design.md)
- [コーディング規約](doc/coding-standards.md)
- [テストガイド](doc/testing-guide.md)
- [API 仕様（OpenAPI 3.0）](doc/api.yaml)
- [コントリビューション（準備中）](doc/CONTRIBUTING.md)
