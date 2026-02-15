# FXGraph MCP Server

FXGraph MCP Serverは、JavaFXアプリケーションのシーングラフを実行時に分析・操作するためのMCPサーバーです。

## 技術スタック

- Java 21
- Spring Boot 4.0.2
- Spring AI MCP Server 1.1.2
- Java Instrumentation API / Attach API
- Gradle 9 (マルチプロジェクト構成)

## アーキテクチャ

MCPサーバーは対象のJavaFX JVMにJava Attach APIを使用してインスペクタエージェント（`fxgraph-agent.jar`）を注入します。エージェントはTCPソケット上でJSON行区切りプロトコルを使用してMCPサーバーと通信します。

```
MCP Client (AI) <--> MCP Server (STDIO) <--> Agent (TCP Socket) <--> JavaFX Scene Graph
                     [fxgraph-mcp-server.jar]  [fxgraph-agent.jar]    [対象JVM内部]
```

### プロジェクト構成

```
fxgraph-mcp-server/          # ルートプロジェクト (MCPサーバー)
├── agent/                    # エージェントサブプロジェクト
│   └── src/main/java/
│       └── .../agent/
│           ├── inspector/    # インスペクタエージェント (対象JVM内で実行)
│           │   ├── FxGraphInspectorAgent.java  # Agentエントリポイント
│           │   ├── SceneGraphInspector.java     # シーングラフ操作
│           │   └── ChildrenGetter.java          # 子ノード取得ユーティリティ
│           └── protocol/     # JSON通信プロトコル (共有)
│               ├── AgentCommand.java
│               └── AgentResponse.java
├── src/main/java/
│   └── .../mcp/
│       ├── agent/            # Attach API・セッション管理
│       │   ├── JavaFxAgent.java      # JVM接続・エージェント注入
│       │   └── SessionManager.java   # セッション管理
│       ├── model/            # データモデル
│       ├── server/           # Spring Bootエントリポイント
│       └── tools/            # MCPツール定義
│           └── FxgraphService.java   # 8つのMCPツール
└── docs/
    └── tools-reference.md    # ツールリファレンス
```

### エージェントJAR分離

- `fxgraph-agent.jar` (~2MB) - 最小限のエージェント (Jackson + インスペクタクラスのみ)
- `fxgraph-mcp-server.jar` (~21MB) - MCPサーバー本体 (Spring Boot含む)

エージェントJARは対象JVMにロードされるため、Spring Bootなどの不要な依存関係を含めず、クラスローダーの競合リスクを最小化しています。

## ビルド

```bash
./gradlew shadowJar
# build/libs/fxgraph-mcp-server.jar  (MCPサーバー)
# build/libs/fxgraph-agent.jar       (エージェント - 自動コピーされる)
```

## テスト

```bash
./gradlew test
```

## 参考

- [Scenic View](https://github.com/JonathanGiles/scenic-view)
