# FXGraph MCP Server

FXGraph MCP Serverは、JavaFXアプリケーションのシーングラフを実行時に分析・操作するためのMCPサーバーおよびCLIツールです。

## 技術スタック

- Java 21
- Spring Boot 4.0.2 (MCPサーバーのみ)
- Spring AI MCP Server 1.1.2 (MCPサーバーのみ)
- Java Instrumentation API / Attach API
- Gradle 9 (マルチプロジェクト構成)

## アーキテクチャ

### MCPサーバーモード

```
MCP Client (AI) <--> MCP Server (STDIO) <--> Agent (TCP Socket) <--> JavaFX Scene Graph
                     [fxgraph-mcp-server.jar]  [fxgraph-agent.jar]    [対象JVM内部]
```

### CLI + Agent Skills モード

```
AI (Agent Skills) <-- Shell/JSON --> CLI <--> Agent (TCP Socket) <--> JavaFX Scene Graph
                      [fxgraph-cli.jar]  [fxgraph-agent.jar]          [対象JVM内部]
```

### プロジェクト構成

```
fxgraph-mcp-server/          # ルートプロジェクト
├── agent/                    # エージェントサブプロジェクト (対象JVM内で実行)
│   └── src/main/java/
│       └── .../agent/
│           ├── inspector/    # インスペクタエージェント
│           │   ├── FxGraphInspectorAgent.java  # Agentエントリポイント
│           │   ├── SceneGraphInspector.java     # シーングラフ操作
│           │   └── ChildrenGetter.java          # 子ノード取得ユーティリティ
│           └── protocol/     # JSON通信プロトコル (agent独立コピー)
├── core/                     # 共有ロジック (Spring不使用)
│   └── src/main/java/
│       └── .../mcp/
│           ├── agent/            # JavaFxAgent (JVM接続・エージェント注入)
│           │   └── protocol/     # AgentCommand / AgentResponse
│           └── model/            # データモデル (SVNode, StageInfo, etc.)
├── cli/                      # CLIサブプロジェクト (fxgraph-cli.jar)
│   └── src/main/java/
│       └── .../cli/
│           ├── FxgraphApplication.java   # main エントリポイント
│           ├── CliCommandDispatcher.java # コマンドルーティング・実装
│           └── CliJsonOutput.java        # JSON stdout 出力
├── mcp-server/               # MCPサーバーサブプロジェクト (Spring Boot)
│   └── src/main/java/
│       └── .../mcp/
│           ├── agent/            # SessionManager (@Component)
│           ├── server/           # Spring Bootエントリポイント
│           └── tools/            # FxgraphService (MCPツール定義)
├── skills/                   # Agent Skills (GitHub Copilot)
│   ├── fxgraph-inspect/SKILL.md  # シーングラフ検査スキル
│   └── fxgraph-interact/SKILL.md # UI操作スキル
├── javafx-test-app/          # テスト用JavaFXアプリ
└── docs/
    └── tools-reference.md    # ツールリファレンス
```

### JAR の分離

- `fxgraph-agent.jar` (~2MB) - 最小限のエージェント (Jackson + インスペクタクラスのみ)
- `fxgraph-cli.jar` - 軽量CLIツール (Spring不使用、`core` + Jackson のみ)
- `fxgraph-mcp-server.jar` (~21MB) - MCPサーバー本体 (Spring Boot含む)

エージェントJARは対象JVMにロードされるため、Spring Bootなどの不要な依存関係を含めず、クラスローダーの競合リスクを最小化しています。

## ビルド

```bash
# MCPサーバー (既存)
./gradlew :mcp-server:shadowJar
# mcp-server/build/libs/fxgraph-mcp-server.jar
# mcp-server/build/libs/fxgraph-agent.jar

# CLI ツール
./gradlew :cli:shadowJar
# cli/build/libs/fxgraph-cli.jar
# cli/build/libs/fxgraph-agent.jar
```

## テスト

```bash
./gradlew test
```

## 参考

- [Scenic View](https://github.com/JonathanGiles/scenic-view)

