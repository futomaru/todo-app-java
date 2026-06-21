# コーディング規約

## 設計ルール

- `record` を DTO に使う（Bean Validation アノテーション付与可）
- ローカル変数は `var` で型推論
- 複数行 SQL はテキストブロック `"""..."""` で書く
- `Optional<T>` は戻り値専用（引数・フィールドには使わない）
- MyBatis のカラム↔プロパティ変換は `map-underscore-to-camel-case=true` に任せる（`@Results` 不要）
- `LocalDateTime.now()` を直接呼ばない。`Clock` を DI して `LocalDateTime.now(clock)` を使う

**バリデーション**
- 入力検証は DTO の record コンポーネントに Jakarta Bean Validation アノテーションで付与
- Controller は `@Valid` を付けるだけ
- PATCH の `title` は `@Size(min = 1, max = 255)`（null は許可、空文字のみ弾く）

**Lombok 利用方針**
- Entity（mutable POJO）: `@Getter` `@Setter` を付け、getter/setter の手書きを省く
  - MyBatis が setter 経由でフィールドに値を注入するため可変クラスである必要があるが、
    アクセサを手書きするのはノイズが大きい。Lombok で機械的に生成して本質に集中する。
- DTO（`record`）: **Lombok は使わない**
  - `record` 自体がアクセサ・`equals` / `hashCode` / `toString` を自動生成し、不変性も保証する。
    Lombok を重ねる必要がない。
- `@Data` は使わない
  - `@EqualsAndHashCode` を含むため可変 Entity の同値性判断が ID 確定前後で変わる落とし穴がある。
    学習目的では `@Getter` `@Setter` のみに留める方が安全。
- `@SneakyThrows` のような「魔法」アノテーションは避ける（例外は明示的に扱う）

## 命名規則

| 対象 | 規則 | 例 |
| --- | --- | --- |
| パッケージ | 英語小文字単数形 | `controller`, `service` |
| クラス | 集約名 + 役割 | `TodoController`, `TodoService` |
| リクエスト DTO | 集約名 + 操作 + `Request` | `TodoCreateRequest`, `TodoUpdateRequest` |
| レスポンス DTO | 集約名 + `Response` | `TodoResponse` |
| カスタム例外 | 対象 + 状況 + `Exception` | `TodoNotFoundException` |
| テーブル名 | 複数形・スネークケース | `todos` |
| カラム名 | スネークケース（Java側はキャメルケース） | `created_at` → `createdAt` |

## コメント規約

**Javadoc を付ける対象**
- Controller / Service / Mapper / Exception クラス: クラスレベルに責務 1〜2 文
- Mapper メソッド: 1 行説明 + `@param` / `@return`
- Controller ハンドラ: HTTP メソッド + パス + `@param` / `@return` / `@throws`
- DTO record コンポーネント: `@param` で意味を記述

**Why コメント**（`// why:` プレフィックス）

設計判断や非自明な理由を記録する。コードを読めば分かる逐次翻訳は書かない。

```java
// why: テスト時に時刻を固定するため Clock 経由で取得
private final Clock clock;

// why: PATCH は null=未指定を許容するため @NotBlank でなく @Size(min=1)
@Size(min = 1, max = 255) String title;

// why: @Options により id がここで entity に書き戻される
mapper.insert(entity);
```

**テストコード**

クラスまたは `@Nested` レベルで「何の仕様を検証しているか」を明記する。
