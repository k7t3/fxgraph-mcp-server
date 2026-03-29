# fxgraph-cli

JavaFX アプリケーションのシーングラフを操作する **軽量 CLI ツール**です。  
Spring Boot に依存せず、`core` と Jackson のみで構成されているため高速に起動します。

## 概要

MCP クライアント（AI）を介さず、シェルスクリプトや Agent Skills から直接 JavaFX アプリを操作する用途に適しています。  
すべての出力は **JSON** 形式で標準出力に書き込まれます。

```
AI (Agent Skills) <-- Shell/JSON --> fxgraph-cli.jar <--> fxgraph-agent.jar <--> JavaFX Scene Graph
```

## ビルド

```bash
./gradlew :cli:shadowJar
# 出力: cli/build/libs/fxgraph-cli.jar
#       cli/build/libs/fxgraph-agent.jar  (agent から自動コピー)
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
| `connect` | 指定 PID の JavaFX アプリに接続し sessionId を返す | — |
| `disconnect` | 接続を切断する | `{"sessionId":"..."}` |
| `getStages` | Stage（ウィンドウ）一覧を取得 | `{"sessionId":"..."}` |
| `getScenegraph` | シーングラフツリーを取得 | `{"sessionId":"...","stageId":"...","depth":5}` |
| `getNodeDetails` | 指定ノードの詳細プロパティを取得 | `{"sessionId":"...","nodeId":12345}` |
| `setProperty` | ノードのプロパティを変更 | `{"sessionId":"...","nodeId":12345,"propertyName":"text","value":"hello"}` |
| `selectNode` | ノードをハイライト表示 | `{"sessionId":"...","nodeId":12345}` |
| `clickNode` | ノードをクリック | `{"sessionId":"...","nodeId":12345}` |
| `requestFocus` | ノードにフォーカスを当てる | `{"sessionId":"...","nodeId":12345}` |
| `typeKey` | キー入力を送信 | `{"sessionId":"...","key":"ENTER"}` |
| `takeScreenshot` | スクリーンショットを保存 | `{"sessionId":"...","savePath":"/tmp/shot.png"}` |

### 使用例

```bash
PID=$(java -jar fxgraph-cli.jar discover | jq -r '.[0].pid')
SESSION=$(java -jar fxgraph-cli.jar $PID connect | jq -r '.sessionId')
java -jar fxgraph-cli.jar $PID getScenegraph "{\"sessionId\":\"$SESSION\",\"depth\":3}"
java -jar fxgraph-cli.jar $PID disconnect "{\"sessionId\":\"$SESSION\"}"
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
| `:core` | implementation | JavaFxAgent・プロトコル・モデル |
| `jackson-databind` | implementation | JSON 出力 |
| `mockito-junit-jupiter` | testImplementation | モック |
