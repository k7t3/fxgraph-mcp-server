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
java -jar fxgraph-cli.jar <pid> <command> [arguments/options]
```

### コマンド一覧

| コマンド | 説明 | パラメータ |
|---------|------|-----------|
| `stages` | Stage（ウィンドウ）一覧を取得 | — |
| `scenegraph` | シーングラフツリーを取得 | `--depth N`, `--bounds`, `--props`, `--filter`, `--stageId` |
| `window-details` | ウィンドウの位置・サイズ・状態を取得 | `<stageId>` または `--stageId ID` |
| `set-window-property` | Stage の公開プロパティを変更 | `<stageId> <property> <value>`, `--type` |
| `close-window` | 終了要求を送り、アプリのキャンセルを尊重 | `<stageId>` または `--stageId ID` |
| `close-popup` | 指定ポップアップを閉じる | `<stageId>` または `--stageId ID` |
| `node-details` | 指定ノードの詳細プロパティ・実効表示状態・祖先を取得 | `<nodeId>`, `--filter`, `--ancestors` |
| `find-nodes` | タイプ・ID・テキスト・スタイルクラスからノードを検索 | `--type`, `--id`, `--text`, `--styleClass`, `--stageId`, `--visible-only` |
| `scroll-node` | ピクセル指定または端へのスクロール | `<nodeId>`, `--dx`, `--dy` または `--align` |
| `scroll-to-index` | 仮想化コンテナの項目を表示 | `<nodeId> --index N` |
| `set-property` | ノードのプロパティを変更 | `<nodeId> <property> <value>`, `--type` |
| `select-node` | ノードをハイライト表示 | `<nodeId>`, `--no-bounds` |
| `click-node` | ボタン・クリック回数を指定してクリック | `<nodeId>`, `--mode synthetic`, `--button primary|secondary|middle`, `--clickCount 1|2` |
| `right-click-node` | `click-node --button secondary` のショートカット | `<nodeId>`, `--mode` |
| `double-click-node` | `click-node --clickCount 2` のショートカット | `<nodeId>`, `--mode`, `--button` |
| `activate-node` | マウス入力なしに `ButtonBase` を起動 | `<nodeId>` |
| `focus` | ノードにフォーカスを当てる | `<nodeId>` |
| `type-key` | 修飾キー付きのキー入力を送信 | `<key>`, `--nodeId`, `--modifiers META,SHIFT`, `--mode synthetic` |
| `screenshot` | 元の解像度で PNG を保存。縮小時は元サイズ・scaled を返す | `<path>`, `--nodeId`, `--stageId`, `--maxWidth`, `--maxHeight`, `--no-limit` |
| `capture-video` | MP4 動画クリップを保存 | `<path>`, `--nodeId`, `--stageId`, `--durationSeconds`, `--framesPerSecond`, `--maxWidth`, `--maxHeight` |

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

# クリック・論理起動・フォーカス・キー入力
java -jar fxgraph-cli.jar $PID click-node $NODE_ID
java -jar fxgraph-cli.jar $PID click-node $NODE_ID --mode synthetic
java -jar fxgraph-cli.jar $PID click-node $NODE_ID --button secondary
java -jar fxgraph-cli.jar $PID click-node $NODE_ID --clickCount 2
java -jar fxgraph-cli.jar $PID right-click-node $NODE_ID
java -jar fxgraph-cli.jar $PID double-click-node $NODE_ID
java -jar fxgraph-cli.jar $PID activate-node $BUTTON_NODE_ID
java -jar fxgraph-cli.jar $PID focus $NODE_ID
java -jar fxgraph-cli.jar $PID type-key ENTER
java -jar fxgraph-cli.jar $PID type-key TAB --modifiers SHIFT
java -jar fxgraph-cli.jar $PID type-key W --modifiers META

# スクリーンショット
java -jar fxgraph-cli.jar $PID screenshot ./result.png
java -jar fxgraph-cli.jar $PID screenshot ./node.png --nodeId $NODE_ID

# 短時間動画（最大30秒、音声なし）
java -jar fxgraph-cli.jar $PID capture-video ./clip.mp4
java -jar fxgraph-cli.jar $PID capture-video ./node.mp4 --nodeId $NODE_ID --durationSeconds 10 --framesPerSecond 15
```

## 出力形式

成功時はコマンドの結果を JSON オブジェクト／配列として標準出力に書き込みます。  
エラーは標準エラー出力に書き込まれます（終了コード 1）。空のオプション値・値の欠落は、オプション名と使用法を示します:

```text
Error: Command failed: --stageId requires a non-empty value
Usage: fxgraph <pid> find-nodes [options]
```

操作の詳細、検索対象、モーダルの回避策は [CLI リファレンス](../skills/fxgraph/references/interact-commands.md) を参照してください。

## 依存関係

| ライブラリ | スコープ | 用途 |
|-----------|---------|------|
| `:fxgraph-core` | implementation | JavaFxAgent・プロトコル・モデル |
| `jackson-databind` | implementation | JSON 出力 |
| `mockito-junit-jupiter` | testImplementation | モック |
