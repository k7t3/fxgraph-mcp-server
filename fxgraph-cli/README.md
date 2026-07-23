# fxgraph-cli

JavaFX アプリケーションのシーングラフを操作する **軽量 CLI ツール**です。  
Spring Boot に依存せず、`fxgraph-core` と Jackson のみで構成されているため高速に起動します。

## 概要

MCP クライアント（AI）を介さず、シェルスクリプトや Agent Skills から直接 JavaFX アプリを操作する用途に適しています。  
すべての出力は **JSON** 形式で標準出力に書き込まれます。

```
AI (Agent Skills) <-- Shell/JSON --> fxgraph-cli.jar <--> fxgraph-agent.jar <--> JavaFX Scene Graph
```

## ビルド

```bash
./gradlew :fxgraph-cli:shadowJar
# 出力: fxgraph-cli/build/libs/fxgraph-cli.jar
#       fxgraph-cli/build/libs/fxgraph-agent.jar  (fxgraph-agent から自動コピー)
```

## 使い方

### JavaFX アプリの検出

```bash
java -jar fxgraph-cli.jar discover
```

```json
[
  { "pid": 12345, "mainClass": "io.github.k7t3.simplefx.Main" }
]
```

### アプリへの接続・コマンド実行

```bash
java -jar fxgraph-cli.jar <pid> <command> [params-json]
```

### コマンド一覧

| コマンド | 説明 | パラメータ |
|---------|------|-----------|
| `stages` | Stage（ウィンドウ）一覧を取得 | — |
| `scenegraph` | シーングラフツリーを取得 | `--depth N`, `--bounds`, `--props`, `--filter`, `--stageId` |
| `node-details` | 指定ノードの詳細プロパティを取得 | `<nodeId>`, `--filter` |
| `find-nodes` | タイプ・ID・テキスト・スタイルクラスからノードを検索 | `--type`, `--id`, `--text`, `--styleClass`, `--stageId` |
| `set-property` | ノードのプロパティを変更 | `<nodeId> <property> <value>`, `--type` |
| `select-node` | ノードをハイライト表示 | `<nodeId>`, `--no-bounds` |
| `click-node` | ノードをクリック | `<nodeId>` |
| `focus` | ノードにフォーカスを当てる | `<nodeId>` |
| `type-key` | キー入力を送信 | `<key>`, `--nodeId` |
| `screenshot` | スクリーンショットを保存 | `<path>`, `--nodeId`, `--stageId`, `--maxWidth`, `--maxHeight` |

### 使用例

```bash
# ノードを検索（タイプ指定）
java -jar fxgraph-cli.jar $PID find-nodes --type Button

# ノードを検索（ID指定）
java -jar fxgraph-cli.jar $PID find-nodes --id submitBtn

# ノードを検索（テキスト指定）
java -jar fxgraph-cli.jar $PID find-nodes --text "Submit"

# ノードを検索（スタイルクラス指定）
java -jar fxgraph-cli.jar $PID find-nodes --styleClass primary-action

# シーングラフを取得（深さ 3、テキストプロパティ付き）
java -jar fxgraph-cli.jar $PID scenegraph --depth 3 --props --filter text

# ノードの詳細プロパティを取得（--filter 必須）
java -jar fxgraph-cli.jar $PID node-details $NODE_ID --filter text,visible,disable

# プロパティを変更
java -jar fxgraph-cli.jar $PID set-property $NODE_ID text "Hello"
java -jar fxgraph-cli.jar $PID set-property $NODE_ID visible false --type boolean

# ノードをハイライト
java -jar fxgraph-cli.jar $PID select-node $NODE_ID

# クリック・フォーカス・キー入力
java -jar fxgraph-cli.jar $PID click-node $NODE_ID
java -jar fxgraph-cli.jar $PID focus $NODE_ID
java -jar fxgraph-cli.jar $PID type-key ENTER

# スクリーンショット
java -jar fxgraph-cli.jar $PID screenshot ./result.png
java -jar fxgraph-cli.jar $PID screenshot ./node.png --nodeId $NODE_ID
```

## 出力形式

成功時はコマンドの結果を JSON オブジェクト／配列として標準出力に書き込みます。  
エラー時は以下の形式で標準出力に書き込まれます（終了コード 1）:

```json
{ "error": "エラーメッセージ" }
```

## 依存関係

| ライブラリ | スコープ | 用途 |
|-----------|---------|------|
| `:fxgraph-core` | implementation | JavaFxAgent・プロトコル・モデル |
| `jackson-databind` | implementation | JSON 出力 |
| `mockito-junit-jupiter` | testImplementation | モック |
