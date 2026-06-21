# Git 規約

## ブランチ戦略

**GitHub Flow** を採用する。

```
main
 └── feature/xxx  ← 作業ブランチ
 └── fix/xxx
 └── docs/xxx
```

- `main` は常にデプロイ可能な状態を保つ
- 作業は必ずブランチを切って行い、PR 経由で `main` にマージする
- 直接 `main` へのプッシュは禁止

## ブランチ命名

```
<type>/<kebab-case-description>
```

| type | 用途 |
|------|------|
| `feature` | 新機能の追加 |
| `fix` | バグ修正 |
| `docs` | ドキュメントのみの変更 |
| `refactor` | 動作を変えないコード整理 |
| `test` | テストの追加・修正 |

例: `feature/add-todo-filter`, `fix/null-pointer-on-complete`

## コミット分割の考え方

**1 コミット = 1 つの論理的な変更**

| やること | やらないこと |
|----------|-------------|
| 機能追加とドキュメントは別コミット | まとめて一気にコミット |
| リファクタと機能変更は別コミット | 「とりあえず保存」コミット |
| テスト追加と実装は同じコミットでよい | 半端な状態でコミット |

> なぜ分けるか: `git bisect` でバグを二分探索するとき、1 コミット 1 変更であれば原因の特定が容易になる。

## コミットメッセージ

[Conventional Commits](https://www.conventionalcommits.org/) に準拠する。

```
<type>: <subject>
```

**type の一覧:**

| type | 用途 |
|------|------|
| `feat` | 新機能 |
| `fix` | バグ修正 |
| `docs` | ドキュメント |
| `refactor` | リファクタリング |
| `test` | テスト |
| `chore` | ビルド設定・依存関係など |

**subject のルール:**
- 英語、現在形・命令形で書く（`add`, `fix`, `update`）
- 末尾にピリオドをつけない
- 50 文字以内を目安にする

```
# 良い例
feat: add filter by completion status
fix: return 404 when todo not found
docs: add API usage examples to README

# 悪い例
修正した
update
fix bug
```

> なぜ英語・命令形か: `git log --oneline` で「このコミットを適用すると何が起きるか」を一文で読めるようにするため。
