# fxgraph-agent

JavaFX アプリケーションの JVM 内に動的に注入される **インスペクタエージェント**です。
Java Instrumentation API を使用してターゲット JVM に対してアタッチし、シーングラフの分析・操作を行います。

## 概要

このサブプロジェクトは、FXGraph MCP Server システムの中核となる軽量エージェント JAR を生成します。  
エージェントは対象 JVM のクラスローダーに依存しない最小構成（Jackson + インスペクタクラスのみ）で設計されており、クラスローダー競合のリスクを最小化しています。

```
[MCP Server / CLI]
      |
      | Java Attach API (JVM 外部)
      v
[fxgraph-agent.jar] -- 注入 --> [対象 JavaFX アプリ JVM]
                                      |
                                      | TCP Socket (JSON)
                                      v
                               [SceneGraphInspector]
                                      |
                                      v
                               [JavaFX Scene Graph]
```

## パッケージ構成

| パッケージ | クラス | 説明 |
|-----------|-------|------|
| `agent.inspector` | `FxGraphInspectorAgent` | Java Instrumentation API エントリポイント。TCP サーバーを起動しコマンドを受け付ける |
| `agent.inspector` | `SceneGraphInspector` | シーングラフのノード取得・プロパティ操作・スクリーンショット等の実装 |
| `agent.inspector` | `NodeHierarchy` | 公開 JavaFX API から直接の子ノードを不変スナップショットとして取得するユーティリティ |
| `agent.protocol` | `AgentCommand` / `AgentResponse` | エージェント独立の JSON 通信プロトコル定義 (fxgraph-core とは別コピー) |

> **注記:** `agent.protocol` は `fxgraph-core` サブプロジェクトのプロトコル定義と同一内容の独立コピーです。  
> エージェントがターゲット JVM のクラスパスに依存せずに動作するため、意図的に分離されています。

## ビルド

```bash
# エージェント JAR の生成
./gradlew :fxgraph-agent:shadowJar
# 出力: fxgraph-agent/build/libs/fxgraph-agent.jar
```

## 出力成果物

| ファイル | 説明 |
|---------|------|
| `fxgraph-agent.jar` | Shadow JAR（Jackson 同梱）。Instrumentation マニフェスト属性付き |

### マニフェスト属性

```
Agent-Class:              io.github.k7t3.fxgraph.mcp.agent.inspector.FxGraphInspectorAgent
Premain-Class:            io.github.k7t3.fxgraph.mcp.agent.inspector.FxGraphInspectorAgent
Can-Retransform-Classes:  true
Can-Redefine-Classes:     true
```

## テスト

テストには TestFX + Monocle（ヘッドレス）を使用しています。CI 環境でも GUI なしで実行できます。

```bash
./gradlew :fxgraph-agent:test
```

## 依存関係

| ライブラリ | スコープ | 用途 |
|-----------|---------|------|
| `jackson-databind` | implementation | JSON シリアライズ／デシリアライズ |
| `javafx-controls/graphics/base` | compileOnly | ターゲット JVM が既に持つ JavaFX API |
| `testfx-junit5` | testImplementation | JavaFX UI テスト |
| `openjfx-monocle` | testImplementation | ヘッドレステスト用レンダリング |
