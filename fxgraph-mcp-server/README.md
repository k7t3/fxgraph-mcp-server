# fxgraph-mcp-server

[Model Context Protocol (MCP)](https://modelcontextprotocol.io/) を使用して、JavaFX アプリケーションのシーングラフをリアルタイムで分析・操作する **MCP サーバー**です。

## 概要

```
MCP Client (AI) <--> fxgraph-mcp-server.jar (STDIO) <--> fxgraph-agent.jar (TCP) <--> JavaFX App
```

AI から JavaFX アプリケーションを Scenic View のように検査・操作できます。

## ビルド

```bash
./gradlew :fxgraph-mcp-server:shadowJar
# 出力: fxgraph-mcp-server/build/libs/fxgraph-mcp-server.jar
#       fxgraph-mcp-server/build/libs/fxgraph-agent.jar  (fxgraph-agent から自動コピー)
```

## 設定 (Claude Desktop)

`claude_desktop_config.json` に以下を追加します:

```json
{
  "mcpServers": {
    "fxgraph": {
      "command": "java",
      "args": ["-jar", "/path/to/fxgraph-mcp-server.jar"]
    }
  }
}
```

> **注記:** `fxgraph-agent.jar` は `fxgraph-mcp-server.jar` と同じディレクトリに配置してください。

## MCP ツール一覧

詳細な仕様は [`docs/tools-reference.md`](../docs/tools-reference.md) を参照してください。

| ツール | 説明 |
|-------|------|
| `discoverApplications` | 実行中の JavaFX アプリを検出する |
| `connectApplication` | 指定 PID のアプリに接続し sessionId を返す |
| `disconnectApplication` | セッションを切断する |
| `getStages` | Stage（ウィンドウ）一覧を取得する |
| `getScenegraph` | シーングラフツリーを取得する |
| `getNodeDetails` | 指定ノードのプロパティ詳細を取得する |
| `setProperty` | ノードのプロパティ値を変更する |
| `selectNode` | ノードをハイライト表示する |
| `clickNode` | ノードをクリックする |
| `requestFocus` | ノードにフォーカスを当てる |
| `typeKey` | キー入力を送信する |
| `takeScreenshot` | スクリーンショットを PNG で保存する |

## パッケージ構成

| パッケージ | クラス | 説明 |
|-----------|-------|------|
| `mcp.server` | `McpServerApplication` | Spring Boot エントリポイント |
| `mcp.agent` | `SessionManager` | 接続セッションの管理 (@Component) |
| `mcp.tools` | `FxgraphService` | MCP ツール定義 (@Component) |

## 技術スタック

| 項目 | 内容 |
|------|------|
| フレームワーク | Spring Boot 4.0.2 |
| MCP 実装 | Spring AI MCP Server 1.1.2 |
| トランスポート | STDIO (同期モード) |
| Java バージョン | Java 21+ |

## 設定ファイル

Spring Boot の設定は `src/main/resources/application.yml` で管理されます。

## 依存関係

| ライブラリ | スコープ | 用途 |
|-----------|---------|------|
| `spring-ai-starter-mcp-server` | implementation | MCP サーバー機能 |
| `spring-boot-starter` | implementation | Spring Boot 基盤 |
| `spring-web` | implementation | Spring AI MCP 自動設定に必要 |
| `:fxgraph-core` | implementation | JavaFxAgent・プロトコル・モデル |
| `jackson-databind` | implementation | JSON (Spring DM でバージョン管理) |
| `spring-boot-starter-test` | testImplementation | Spring コンテキストテスト |
