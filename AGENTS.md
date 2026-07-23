# AGENTS.md — fxgraph-mcp-server

このプロジェクトは JavaFX アプリケーションをリアルタイムに解析・操作するための MCP サーバーと Agent Skills を提供します。

---

## Project Architecture

```
MCP Client (AI)
  └─[STDIO]─► McpServerApplication (Spring Boot)
                └─► FxgraphService (@Tool methods)
                      └─► JavaFxAgent (Attach API + TCP socket)
                            └─[TCP JSON]─► FxGraphInspectorAgent (injected into target JVM)
                                             └─► SceneGraphInspector
```

### Subprojects (Gradle multi-project)

| Module | Role |
|---|---|
| `fxgraph-core` | 共有モデル・プロトコル・エージェント通信ロジック |
| `fxgraph-agent` | 対象 JVM に注入される Java Agent |
| `fxgraph-cli` | 軽量 CLI ツール（スタンドアロン JAR） |
| `fxgraph-mcp-server` | Spring Boot MCP サーバー（STDIO トランスポート） |
| `javafx-test-app` | 動作確認用サンプル JavaFX アプリ |

---

## Build Commands

```bash
# プロジェクト全体のビルド
./gradlew build

# Shadow JAR の生成（各モジュール）
./gradlew :fxgraph-mcp-server:shadowJar          # → fxgraph-mcp-server/build/libs/fxgraph-mcp-server.jar
./gradlew :fxgraph-cli:shadowJar                 # → fxgraph-cli/build/libs/fxgraph-cli.jar
./gradlew :fxgraph-agent:shadowJar               # → fxgraph-agent/build/libs/fxgraph-agent.jar

# CLI と Agent JAR を skills/ へ配置するカスタムタスク
./gradlew :fxgraph-cli:installSkillJars

# クリーン
./gradlew clean
```

---

## Test Commands

```bash
# 全テスト実行
./gradlew test

# モジュール単体のテスト
./gradlew :fxgraph-core:test
./gradlew :fxgraph-agent:test           # Monocle (headless) で JavaFX テストを実行
./gradlew :fxgraph-cli:test
./gradlew :fxgraph-mcp-server:test      # 統合テストには事前に shadowJar が必要

# 単一テストクラスの実行
./gradlew :fxgraph-mcp-server:test --tests "io.github.k7t3.fxgraph.mcp.tools.FxgraphServiceTest"

# 単一テストメソッドの実行
./gradlew :fxgraph-mcp-server:test --tests "io.github.k7t3.fxgraph.mcp.tools.FxgraphServiceTest.connectApplicationReturnsExistingSessionWhenAlreadyConnected"

# テスト結果の継続表示（--info でログ出力）
./gradlew :fxgraph-mcp-server:test --info

# 統合テストの事前準備（shadow JAR が必須）
./gradlew :fxgraph-mcp-server:shadowJar && ./gradlew :fxgraph-mcp-server:test
```

---

## STDIO Transport Notes

- MCP サーバーは **STDIO モード**（`spring.ai.mcp.server.stdio: true`）で動作する
- エージェントとの通信は **改行区切り JSON（NDJSON）** over TCP ソケット
- シリアライズされた JSON には `\n` を含んではならない（1メッセージ1行）
- ノード ID は `System.identityHashCode(node)` — JVM セッションをまたいで不変ではない
- ログはすべてローリングファイル (`fxgraph.log`) へ出力し、STDIO を汚染しない

---

## Key Dependencies (gradle/libs.versions.toml)

| ライブラリ | バージョン | 用途 |
|---|---|---|
| Java | 21 | 言語・ランタイム |
| Spring Boot | 4.0.2 | MCP サーバー基盤 |
| Spring AI MCP | 1.1.2 | MCP プロトコル実装 |
| Jackson | 2.18.3 | JSON シリアライズ |
| JUnit | 5.10.0 | テストフレームワーク |
| Mockito | 5.11.0 | テストモック |
| JavaFX | 21 | 対象プラットフォーム |
| AtlantaFX | 2.0.1 | テスト用 JavaFX サンプルアプリ |
