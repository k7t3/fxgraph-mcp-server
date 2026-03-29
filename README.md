# FXGraph MCP Server

[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-green)](https://spring.io/projects/spring-boot)
[![Gradle](https://img.shields.io/badge/Gradle-9.0.0-orange)](https://gradle.org/)

JavaFXアプリケーションのシーングラフを実行時に分析・操作するための[MCP (Model Context Protocol)](https://modelcontextprotocol.io/) サーバーです。

## 概要

FXGraph MCP Serverは、実行中のJavaFXアプリケーションに接続し、そのシーングラフ（UIコンポーネント階層）をAIアシスタントから調査・操作できるようにします。これは[Scenic View](https://github.com/JonathanGiles/scenic-view)をMCPプロトコルでラップしたようなものです。

### 主な機能

- **🔍 アプリケーション検出** - 実行中のJavaFXアプリケーションを自動検出
- **🔗 動的接続** - Java Attach APIを使用して対象JVMにエージェントを注入
- **🌳 シーングラフ取得** - UIコンポーネント階層をツリー構造で取得
- **📊 プロパティ閲覧** - ノードのプロパティ（レイアウト、スタイル、コンテンツ等）を取得
- **✏️ プロパティ変更** - 実行中のアプリケーションのUIをリアルタイムに変更
- **🎯 ノードハイライト** - 対象アプリケーション内で特定ノードを視覚的に強調表示

## クイックスタート

### ビルド

```bash
# MCPサーバー
./gradlew :mcp-server:shadowJar

# CLI ツール
./gradlew :cli:shadowJar
```

ビルド成果物：
- `mcp-server/build/libs/fxgraph-mcp-server.jar` - MCPサーバー本体 (~21MB)
- `mcp-server/build/libs/fxgraph-agent.jar` - エージェントJAR (~2MB、対象JVMに注入)
- `cli/build/libs/fxgraph-cli.jar` - CLI ツール (Spring不使用、軽量)
- `cli/build/libs/fxgraph-agent.jar` - エージェントJAR (CLI用コピー)

### テスト

```bash
./gradlew test
```

### 実行

MCPサーバーはSTDIOトランスポートで動作します。通常はMCPクライアント（Claude Desktop等）から起動されます：

```bash
java -jar mcp-server/build/libs/fxgraph-mcp-server.jar
```

### CLI ツールの使用

`fxgraph-cli.jar` はSpring Bootを使用しない軽量CLIツールです。パイプラインでのJSON処理に最適です：

```bash
# 実行中のJavaFXアプリ一覧
java -jar cli/build/libs/fxgraph-cli.jar discover

# PIDを取得してウィンドウ一覧を表示
PID=$(java -jar cli/build/libs/fxgraph-cli.jar discover | jq '.[0].pid')
java -jar cli/build/libs/fxgraph-cli.jar $PID stages

# シーングラフ取得（深さ制限・バウンド付き）
java -jar cli/build/libs/fxgraph-cli.jar $PID scenegraph --depth 3 --bounds

# ノード詳細取得
java -jar cli/build/libs/fxgraph-cli.jar $PID node-details $NODE_ID

# プロパティ変更
java -jar cli/build/libs/fxgraph-cli.jar $PID set-property $NODE_ID text "Hello"

# スクリーンショット
java -jar cli/build/libs/fxgraph-cli.jar $PID screenshot ./screenshot.png
```

全出力はJSON形式。エラーはstderrに出力されexit code 1で終了します。

CLIコマンド一覧は `java -jar fxgraph-cli.jar` で確認できます。

### MCPクライアントへの登録

このサーバーをMCPクライアントで使用するには、設定ファイルにサーバーを登録します。

#### Claude Desktop

`~/Library/Application Support/Claude/claude_desktop_config.json`（macOS）または `%APPDATA%/Claude/claude_desktop_config.json`（Windows）に追加：

```json
{
  "mcpServers": {
    "fxgraph": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/fxgraph-mcp-server.jar"
      ]
    }
  }
}
```

#### Cline / Roo Code (VS Code)

`.vscode/mcp-settings.json` に追加：

```json
{
  "servers": [
    {
      "name": "fxgraph",
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/path/to/fxgraph-mcp-server.jar"]
    }
  ]
}
```

#### Claude Code

`.claude/CLAUDE.md` またはプロンプトで：

```
/mcp add fxgraph java -jar /path/to/fxgraph-mcp-server.jar
```

#### カスタムMCPクライアント

```javascript
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const transport = new StdioClientTransport({
  command: 'java',
  args: ['-jar', '/path/to/fxgraph-mcp-server.jar']
});

const client = new Client({ name: 'my-client', version: '1.0.0' });
await client.connect(transport);
```

## アーキテクチャ

### MCPサーバーモード

```
┌─────────────┐     STDIO      ┌─────────────────────┐     TCP Socket     ┌─────────────────┐
│  MCP Client │  ───────────►  │  fxgraph-mcp-server │  ───────────────►  │  fxgraph-agent  │
│  (AI/Claude)│  ◄───────────  │  (~21MB)            │  ◄───────────────  │  (~2MB)         │
└─────────────┘                └─────────────────────┘                    └────────┬────────┘
                                                                                  │
                                                                                  │ injected into
                                                                                  ▼
                                                                         ┌────────────────────┐
                                                                         │  JavaFX App JVM    │
                                                                         │  (Target Process)  │
                                                                         └────────────────────┘
```

### CLI + Agent Skills モード

```
┌──────────────┐  Shell/Pipeline  ┌────────────────┐   TCP Socket   ┌─────────────────┐
│  AI (Skills) │  ──────────────►  │  fxgraph-cli   │  ───────────►  │  fxgraph-agent  │
│              │  ◄──────────────  │  (Spring不使用) │  ◄───────────  │  (~2MB)         │
└──────────────┘   JSON stdout     └────────────────┘                └────────┬────────┘
                                                                              │ injected into
                                                                              ▼
                                                                     ┌────────────────────┐
                                                                     │  JavaFX App JVM    │
                                                                     └────────────────────┘
```

各CLIコマンドは独立して実行（ステートレス）。エージェントは対象JVMに常駐するため2回目以降は再注入不要で高速。

### JAR の分離

エージェントは対象JVMにロードされるため、依存関係を最小化しています：
- **fxgraph-agent.jar** (~2MB) - Jackson + インスペクタクラスのみ
- **fxgraph-cli.jar** - Spring不使用の軽量CLIツール (`core` + Jackson のみ)
- **fxgraph-mcp-server.jar** (~21MB) - Spring Boot含む完全なMCPサーバー

エージェントJARをシンプルに保つことでクラスローダーの競合リスクを最小化しています。

## ツール一覧

| ツール名 | 説明 |
|---------|------|
| `discoverApplications` | 実行中のJavaプロセスを検出（JavaFXアプリをマーク） |
| `connectApplication` | PIDで指定したアプリに接続しsessionIdを取得 |
| `disconnectApplication` | 接続を切断しリソースを解放 |
| `getStages` | ステージ（ウィンドウ）一覧を取得 |
| `getScenegraph` | シーングラフ構造をツリー形式で取得 |
| `getNodeDetails` | 特定ノードの詳細情報を取得 |
| `setProperty` | ノードのプロパティを変更（text, style, visible等） |
| `selectNode` | 対象アプリ内でノードをハイライト表示 |

詳細な仕様は[docs/tools-reference.md](docs/tools-reference.md)を参照してください。

## 使用例

### 基本的な調査フロー

```
1. discoverApplications()
   → { "applications": [{ "pid": 12345, "javaFX": true, ... }] }

2. connectApplication(pid: 12345)
   → { "sessionId": "xxx", "agentPort": 54321 }

3. getStages(sessionId: "xxx")
   → { "data": [{ "stageId": "123", "title": "Main Window" }] }

4. getScenegraph(sessionId: "xxx", depth: 3)
   → { "rootNodes": [{ "nodeId": 456, "nodeClass": "VBox", ... }] }

5. getNodeDetails(sessionId: "xxx", nodeId: 456)
   → { "properties": [{ "name": "spacing", "value": 10 }, ...] }

6. selectNode(sessionId: "xxx", nodeId: 456)
   → 対象アプリでノードに赤い枠線が表示される

7. disconnectApplication(sessionId: "xxx")
```

### プロパティ変更の例

```
getNodeDetails(sessionId: "xxx", nodeId: 789)
→ { "properties": [{ "name": "text", "value": "Old Text", "writable": true }] }

setProperty(sessionId: "xxx", nodeId: 789, propertyName: "text", value: "New Text")
→ { "success": true, "oldValue": "Old Text", "newValue": "New Text" }

selectNode(sessionId: "xxx", nodeId: 789, showBounds: true)
→ 変更が視覚的に反映されていることを確認
```

## 技術仕様

| 項目 | 値 |
|------|-----|
| **MCP Server Type** | SYNC |
| **Transport** | STDIO |
| **Java Version** | 21+ |
| **Spring Boot** | 4.0.2 |
| **Spring AI MCP** | 1.1.2 |
| **Build Tool** | Gradle 9.0.0 |
| **Node ID** | `System.identityHashCode(node)` |
| **Protocol** | Line-delimited JSON over TCP |

## プロジェクト構成

```
fxgraph-mcp-server/
├── agent/                          # エージェントサブプロジェクト (対象JVM内で実行)
│   └── src/main/java/
│       └── io/github/k7t3/fxgraph/mcp/agent/
│           ├── inspector/          # シーングラフ操作クラス
│           │   ├── FxGraphInspectorAgent.java
│           │   ├── SceneGraphInspector.java
│           │   └── ChildrenGetter.java
│           └── protocol/           # JSONプロトコル (agent独立コピー)
├── core/                           # 共有ロジック (Spring不使用)
│   └── src/main/java/
│       └── io/github/k7t3/fxgraph/mcp/
│           ├── agent/              # JavaFxAgent (JVM接続・エージェント注入)
│           │   └── protocol/       # AgentCommand / AgentResponse
│           └── model/              # データモデル (SVNode, StageInfo, etc.)
├── cli/                            # CLI ツール (fxgraph-cli.jar)
│   └── src/main/java/
│       └── io/github/k7t3/fxgraph/mcp/cli/
│           ├── FxgraphApplication.java   # main エントリポイント
│           ├── CliCommandDispatcher.java # コマンドルーティング・実装
│           └── CliJsonOutput.java        # JSON stdout 出力
├── mcp-server/                     # MCPサーバー (Spring Boot, fxgraph-mcp-server.jar)
│   └── src/main/java/
│       └── io/github/k7t3/fxgraph/mcp/
│           ├── agent/              # SessionManager (Spring @Component)
│           ├── server/             # McpServerApplication (Spring Boot main)
│           └── tools/              # FxgraphService (MCPツール定義)
├── skills/                         # Agent Skills (GitHub Copilot)
│   ├── fxgraph-inspect/SKILL.md   # シーングラフ検査スキル
│   └── fxgraph-interact/SKILL.md  # UI操作スキル
├── javafx-test-app/                # テスト用JavaFXアプリ
└── docs/
    └── tools-reference.md          # MCPツール詳細仕様
```

## Scenic View との関係

本プロジェクトは[Scenic View](https://github.com/JonathanGiles/scenic-view)のパターンを参考にしています：

- `ChildrenGetter` - Scenic Viewの`ChildrenGetter.java`と同等の実装
- ノードID - `System.identityHashCode()`を使用（Scenic Viewは`hashCode()`）
- JavaFX検出 - `javafx.version`システムプロパティの存在チェック
- 子ノード取得 - `Parent.getChildrenUnmodifiable()` / `SubScene.getRoot()`

## 貢献

バグ報告や機能要望は[GitHub Issues](https://github.com/k7t3/fxgraph-mcp-server/issues)へお願いします。

## ライセンス

[MIT License](LICENSE)

## 参考

- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Scenic View](https://github.com/JonathanGiles/scenic-view)
- [Spring AI MCP](https://docs.spring.io/spring-ai/reference/api/mcp.html)
