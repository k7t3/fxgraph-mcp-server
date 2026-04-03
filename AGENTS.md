# AGENTS.md — fxgraph-mcp-server

JavaFX アプリケーションをリアルタイムに解析・操作するための MCP サーバーと Agent Skills を提供するプロジェクトです。
生成 AI エージェントはこのドキュメントを参照してコードを生成・レビュー・修正してください。

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
| `core` | 共有モデル・プロトコル・エージェント通信ロジック（Spring 非依存） |
| `agent` | 対象 JVM に inject される Java Agent（Spring 非依存） |
| `cli` | 軽量 CLI ツール（Spring 非依存、スタンドアロン JAR） |
| `mcp-server` | Spring Boot MCP サーバー（STDIO モード） |
| `javafx-test-app` | 動作確認用サンプル JavaFX アプリ |
| `skills/fxgraph` | AI Agent Skill 定義・スクリプト |

---

## Build Commands

```bash
# プロジェクト全体のビルド
./gradlew build

# Shadow JAR の生成（各モジュール）
./gradlew :mcp-server:shadowJar          # → mcp-server/build/libs/fxgraph-mcp-server.jar
./gradlew :cli:shadowJar                 # → cli/build/libs/fxgraph-cli.jar
./gradlew :agent:shadowJar               # → agent/build/libs/fxgraph-agent.jar

# CLI と Agent JAR を skills/ へ配置するカスタムタスク
./gradlew :cli:installSkillJars

# クリーン
./gradlew clean
```

---

## Test Commands

```bash
# 全テスト実行
./gradlew test

# モジュール単体のテスト
./gradlew :core:test
./gradlew :agent:test           # Monocle (headless) で JavaFX テストを実行
./gradlew :cli:test
./gradlew :mcp-server:test      # 統合テストには事前に shadowJar が必要

# 単一テストクラスの実行
./gradlew :mcp-server:test --tests "io.github.k7t3.fxgraph.mcp.tools.FxgraphServiceTest"

# 単一テストメソッドの実行
./gradlew :mcp-server:test --tests "io.github.k7t3.fxgraph.mcp.tools.FxgraphServiceTest.connectApplicationReturnsExistingSessionWhenAlreadyConnected"

# テスト結果の継続表示（--info でログ出力）
./gradlew :mcp-server:test --info

# 統合テストの事前準備（shadow JAR が必須）
./gradlew :mcp-server:shadowJar && ./gradlew :mcp-server:test
```

### Agent モジュールのテスト（JavaFX headless）

`agent` モジュールのテストは自動的に Monocle を使ったヘッドレスモードで実行されます（build.gradle 設定済み）。
CI 環境でも追加設定不要です。

---

## Code Style Guidelines

### Java バージョン・ビルドツール

- **Java 21** — Records、Sealed Classes、Pattern Matching、Switch Expressions、`var` を積極的に活用する
- **Gradle 9** — Version Catalog (`gradle/libs.versions.toml`) で依存バージョンを一元管理

### パッケージ構造

```
io.github.k7t3.fxgraph.mcp.          # core / mcp-server 共通ルート
io.github.k7t3.fxgraph.mcp.agent.    # エージェント通信層
io.github.k7t3.fxgraph.mcp.model.    # ドメインモデル
io.github.k7t3.fxgraph.mcp.tools.    # MCP ツール (@Tool)
io.github.k7t3.fxgraph.mcp.agent.    # agent サブプロジェクト (独立コピー)
io.github.k7t3.fxgraph.mcp.cli.      # cli サブプロジェクト
```

### インポート

- 静的インポートは `Assertions.*`、`Mockito.*` など標準的なテストユーティリティのみ許可
- 未使用インポートは削除する

### 命名規則

| 種別 | 規則 | 例 |
|---|---|---|
| クラス・インターフェース | UpperCamelCase | `FxgraphService`, `AgentCommand` |
| メソッド・変数 | lowerCamelCase | `sendAgentCommand()`, `sessionId` |
| 定数 | UPPER_SNAKE_CASE | `DEFAULT_TIMEOUT_MS` |
| テストメソッド | 動作を説明するキャメルケース | `connectApplicationReturnsExistingSessionWhenAlreadyConnected` |
| コマンド変数 | `cmd` | `AgentCommand cmd = ...` |
| レスポンス変数 | `resp` | `AgentResponse resp = ...` |
| 結果 Map | `result` | `Map<String, Object> result = new LinkedHashMap<>()` |

### フォーマット・スタイル

- インデント: スペース 4 つ
- Lombok は**使用禁止** — getter/setter/constructor はすべて手書き
- 行長の厳密な制限はないが、可読性を優先して適切に改行する
- 空の `catch` ブロックは禁止（例外を無視する場合は `// Ignore` コメントを付ける）

### 型・データ構造

- JSON を返す MCP ツールメソッドの戻り値は `Map<String, Object>`
- 順序付きのレスポンス Map には `LinkedHashMap` を使用（`HashMap` は不可）
- モデル/プロトコルクラスには `@JsonInclude(JsonInclude.Include.NON_NULL)` を付与する
- コレクションは `List.of(...)` / `Map.of(...)` などイミュータブルファクトリを優先

### エラーハンドリング

- すべての公開メソッドは `Exception` をキャッチし、エラー情報を含む Map または `AgentResponse.error(message)` を返す
- 例外をスローして上位に伝播させることは原則禁止（MCP ツール層では特に厳守）
- エラーレスポンスの Map キー: `"success": false`, `"error": "<message>"`
- 成功レスポンスの Map キー: `"success": true`, データキー

```java
// 推奨パターン
try {
    // ...
    result.put("success", true);
    return result;
} catch (Exception e) {
    return Map.of("success", false, "error", e.getMessage());
}
```

### スレッド安全性・非同期処理

- JavaFX シーングラフへのアクセスはすべて `Platform.runLater()` 経由で `runOnFxThread()` ヘルパーを使って実行する
- `runOnFxThread()` は `CompletableFuture` + タイムアウト（10秒）でブロッキング待機する
- タイムアウトは `TimeoutException` として適切にハンドリングする

### MCP ツールの実装パターン

```java
@Tool(description = "...")
public Map<String, Object> toolName(
        @ToolParam(description = "...", required = true)  String requiredParam,
        @ToolParam(description = "...", required = false) String optionalParam) {

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("requiredParam", requiredParam);
    if (optionalParam != null) params.put("optionalParam", optionalParam);

    AgentCommand cmd = new AgentCommand(AgentCommand.CommandType.TOOL_NAME, params);
    return sendAgentCommand(sessionId, cmd);
}
```

---

## Testing Guidelines

### フレームワーク

- **JUnit 5** (`@Test`, `@BeforeEach`, `@AfterEach`, `@BeforeAll`)
- **Mockito 5** (`@ExtendWith(MockitoExtension.class)`, `@Mock`, `ArgumentCaptor`)
- Spring コンテキストは原則不要 — サービス層のテストは `new FxgraphService(sessionManager)` のように直接構築する

### テストパターン

```java
// 1. Agent のモックを SessionManager に登録
JavaFxAgent mockAgent = mock(JavaFxAgent.class);
sessionManager.registerSession("session-id", mockAgent);

// 2. 期待するレスポンスをスタブ
when(mockAgent.sendCommand(any(AgentCommand.class)))
    .thenReturn(AgentResponse.success(Map.of("key", "value")));

// 3. サービス呼び出し
Map<String, Object> result = service.toolMethod("session-id", "param");

// 4. 送信コマンドの検証
ArgumentCaptor<AgentCommand> captor = ArgumentCaptor.forClass(AgentCommand.class);
verify(mockAgent).sendCommand(captor.capture());
assertThat(captor.getValue().getType()).isEqualTo(AgentCommand.CommandType.EXPECTED_TYPE);
```

- `System.out` / `System.err` をテスト内でリダイレクトする場合は `@BeforeEach` で保存し `@AfterEach` で必ず復元する
- ヘッドレス JavaFX が不要な環境では `Assumptions.assumeTrue(...)` でスキップする
- 統合テスト (`McpServerStdioIntegrationTest`) は shadow JAR の存在を `Assumptions.assumeTrue` でチェックする

---

## Protocol Notes

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
