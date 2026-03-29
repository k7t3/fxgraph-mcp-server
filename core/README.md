# core

MCP Server と CLI の両方から利用される **共有ライブラリ**サブプロジェクトです。  
Spring Boot に依存せず、軽量な純粋 Java ライブラリとして設計されています。

## 概要

`core` は以下の 3 つの責務を持ちます。

1. **エージェント接続** — Java Attach API を使ってターゲット JVM にエージェント JAR を注入し、TCP ソケット経由で通信する
2. **プロトコル定義** — MCP Server / CLI とエージェント間の JSON コマンド／レスポンス定義
3. **データモデル** — シーングラフデータを表す不変モデルクラス

## パッケージ構成

### `io.github.k7t3.fxgraph.mcp.agent`

| クラス | 説明 |
|-------|------|
| `JavaFxAgent` | Java Attach API を使い、実行中の JVM に `fxgraph-agent.jar` を注入する。TCP ソケットで通信セッションを確立する |

### `io.github.k7t3.fxgraph.mcp.agent.protocol`

| クラス | 説明 |
|-------|------|
| `AgentCommand` | エージェントへ送信するコマンドの JSON スキーマ |
| `AgentResponse` | エージェントから返却されるレスポンスの JSON スキーマ |

### `io.github.k7t3.fxgraph.mcp.model`

| クラス | 説明 |
|-------|------|
| `SVNode` | シーングラフの 1 ノードを表すデータクラス（ID・型名・子ノード・プロパティ等） |
| `StageInfo` | JavaFX Stage（ウィンドウ）の情報 |
| `JavaFxApplication` | 検出された JavaFX アプリケーション（PID・メインクラス名） |
| `PropertyDetail` | ノードプロパティの詳細（名前・型・値） |
| `Bounds` | ノードのバウンディングボックス（x, y, width, height） |

## ビルド

```bash
./gradlew :core:build
```

このサブプロジェクト単体は JAR を生成しますが、通常は `cli` または `mcp-server` のビルド時に自動的に依存として組み込まれます。

## 依存関係

| ライブラリ | スコープ | 用途 |
|-----------|---------|------|
| `jackson-databind` | implementation | JSON シリアライズ／デシリアライズ |
