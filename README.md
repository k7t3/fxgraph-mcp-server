# fxgraph-mcp-server

生成 AI エージェントが実行中の JavaFX アプリケーションをリアルタイムに分析・操作するための MCP サーバーと Agent Skills を提供するプロジェクトです。

Java Attach API でターゲット JVM にインスペクタエージェントを動的注入し、シーングラフの探索・プロパティ変更・UI 操作・スクリーンショット／短時間動画の取得などを AI から直接実行できます。

## 機能概要

- **シーングラフ探索** — Stage と表示中のポップアップを含む JavaFX ウィンドウ・ノードツリーを JSON で取得
- **ノード検索** — タイプ・CSS ID・テキスト・スタイルクラスからノードを検索
- **プロパティ読み取り・変更** — テキスト・スタイル・表示状態などのプロパティを動的に操作
- **UI インタラクション** — クリック・フォーカス・キー入力を JavaFX イベントシステム経由で送信
- **ノードハイライト** — 対象ノードを視覚的なオーバーレイでハイライト表示
- **スクリーンショット** — ノード単体またはシーン全体を PNG 保存
- **短時間動画** — ノード単体または任意の JavaFX ウィンドウシーンを最大30秒の MP4/H.264（音声なし）で保存

## アーキテクチャ

```
MCP Client (AI / OpenCode)
  └─[STDIO]─► McpServerApplication (Spring Boot)
                └─► FxgraphService (@Tool × 13 メソッド)
                      └─► JavaFxAgent (Attach API + TCP socket)
                            └─[TCP NDJSON]─► FxGraphInspectorAgent (注入済みエージェント)
                                               └─► SceneGraphInspector
                                                     └─► JavaFX Scene Graph

Agent Skills（CLI 経由）:
AI → bash → scripts/fxgraph → fxgraph-cli.jar
              └─► CliCommandDispatcher
                    └─► JavaFxAgent
```

通信プロトコルは **改行区切り JSON（NDJSON）over TCP**。MCP サーバーは **STDIO トランスポート** で動作します。

## サブプロジェクト構成

| モジュール | 役割 |
|---|---|
| `fxgraph-core` | 共有モデル・プロトコル・エージェント通信ロジック |
| `fxgraph-agent` | 対象 JVM に注入される Java Agent |
| `fxgraph-cli` | 軽量 CLI ツール（Agent Skills から直接利用） |
| `fxgraph-mcp-server` | Spring Boot MCP サーバー（STDIO トランスポート） |
| `javafx-test-app` | 動作確認用サンプル JavaFX アプリ |

## 必要要件

- Java 21+
- Gradle（プロジェクトに `gradlew` 同梱）
- 対象の JavaFX アプリケーション（Java 11+ で動作）

## セットアップ

### ビルド

```bash
# プロジェクト全体をビルド
./gradlew build

# 各モジュールの Shadow JAR を生成
./gradlew :fxgraph-mcp-server:shadowJar   # → fxgraph-mcp-server/build/libs/fxgraph-mcp-server.jar
./gradlew :fxgraph-cli:shadowJar          # → fxgraph-cli/build/libs/fxgraph-cli.jar
./gradlew :fxgraph-agent:shadowJar        # → fxgraph-agent/build/libs/fxgraph-agent.jar
```

### MCP サーバーとして使用する（OpenCodeの場合）

`fxgraph-mcp-server.jar` を生成し、OpenCode の設定ファイルに追記します。

```bash
./gradlew :fxgraph-mcp-server:shadowJar
```

**`~/.config/opencode/config.json` の設定例:**

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "fxgraph": {
      "type": "local",
      "command": ["java", "-jar", "/path/to/fxgraph-mcp-server.jar"],
      "enabled": true
    }
  }
}
```

使用するには、プロンプトで `use the fxgraph tool` と指定します。

> `fxgraph-agent.jar` は `fxgraph-mcp-server.jar` と同じディレクトリに自動でコピーされます。

### Agent Skills として使用する（CLI）

CLI と Agent JAR を `skills/fxgraph/scripts/` へデプロイします。

```bash
./gradlew :fxgraph-cli:installSkillJars
```

デプロイ後の構成:

```
skills/fxgraph/scripts/
├── fxgraph             # 実行ラッパースクリプト
├── fxgraph-cli.jar     # CLI ツール
└── fxgraph-agent.jar   # 対象 JVM に注入されるエージェント
```

AI エージェントに `skills/fxgraph/SKILL.md` を読み込ませることで、CLI 経由でも同等の操作が可能になります。

> JAR ファイルはバージョン管理対象外（`.gitignore`）です。ソース変更後は `installSkillJars` を再実行してください。

## MCP ツール一覧

| ツール | 説明 |
|---|---|
| `discoverApplications` | 実行中の JavaFX アプリケーションを一覧取得 |
| `connectApplication` | PID 指定でインスペクタエージェントを準備 |
| `disconnectApplication` | PID 指定でインスペクタエージェントを停止 |
| `getStages` | 表示中のウィンドウ（Stage・ContextMenu・Tooltip 等）の一覧・種類・サイズ・位置を取得 |
| `getScenegraph` | シーングラフのツリー構造を取得（深さ・プロパティ・バウンズ指定可） |
| `getNodeDetails` | 特定ノードの詳細プロパティを取得 |
| `findNodes` | タイプ・CSS ID・テキスト・スタイルクラスからノードを検索 |
| `setProperty` | ノードのプロパティ値を変更（text・style・visible 等） |
| `selectNode` | ノードを視覚的にハイライト（赤枠オーバーレイ）。`nodeId=0` で解除 |
| `clickNode` | ButtonBase を起動、またはノードに合成クリックイベントを送信 |
| `requestFocus` | ノードにキーボードフォーカスを要求 |
| `typeKey` | キー入力イベントを送信（`ENTER`・`TAB` 等のキーコード対応） |
| `takeScreenshot` | ノードまたはシーン全体のスクリーンショットを PNG 保存 |
| `captureVideo` | ノードまたは任意のウィンドウシーンを最大30秒の MP4/H.264 で保存 |

詳細な入出力スキーマは [`docs/tools-reference.md`](docs/tools-reference.md) を参照してください。

## 使用例（MCP ツール呼び出しフロー）

```
1. discoverApplications()
   → [{ "pid": 12345, "mainClass": "com.example.MyApp", "javaFX": true }]

2. connectApplication(pid: 12345)
   → { "success": true, "agentPort": 54321 }

3. getStages(pid: 12345)
   → [{ "stageId": "...", "windowType": "Stage", "title": "Main Window", "rootNodeId": 987654321 }]

4. getScenegraph(pid: 12345, depth: 3, includeProperties: true, propertyFilter: ["text"])
   → { "rootNodes": [{ "nodeId": 987654321, "type": "VBox", "children": [...] }] }

5. setProperty(pid: 12345, nodeId: 123456, propertyName: "text", value: "Hello")
   → { "oldValue": "Click Me", "newValue": "Hello" }

6. takeScreenshot(pid: 12345, savePath: "/tmp/result.png")
   → { "savedPath": "/tmp/result.png", "width": 800, "height": 600 }

7. captureVideo(pid: 12345, savePath: "/tmp/clip.mp4", durationSeconds: 5)
   → { "savedPath": "/tmp/clip.mp4", "mimeType": "video/mp4", "frameCount": 50 }

8. disconnectApplication(pid: 12345)
   → { "success": true }
```

## CLI コマンド一覧

Agent Skills から `bash` ツール経由で実行します。

```bash
CLI="/path/to/skills/fxgraph/scripts/fxgraph"

# 実行中の JavaFX プロセスを検出
$CLI discover

# ウィンドウ一覧を取得
$CLI <PID> stages

# シーングラフを取得（深さ 3、テキストプロパティ付き）
$CLI <PID> scenegraph --depth 3 --props --filter text

# ノードを検索（タイプ・ID・テキスト・スタイルクラスから）
$CLI <PID> find-nodes --type Button
$CLI <PID> find-nodes --id submitBtn
$CLI <PID> find-nodes --text "Submit"
$CLI <PID> find-nodes --styleClass primary-action --stageId <STAGE_ID>

# ノードの詳細プロパティを取得（--filter 必須）
$CLI <PID> node-details <NODE_ID> --filter text,visible,disable

# プロパティを変更
$CLI <PID> set-property <NODE_ID> text "Hello"
$CLI <PID> set-property <NODE_ID> visible false --type boolean

# ノードをハイライト
$CLI <PID> select-node <NODE_ID>

# クリック・フォーカス・キー入力
$CLI <PID> click-node <NODE_ID>
$CLI <PID> focus <NODE_ID>
$CLI <PID> type-key ENTER

# スクリーンショット
$CLI <PID> screenshot ./result.png
$CLI <PID> screenshot ./node.png --nodeId <NODE_ID>

# 短時間動画（既定: 5秒、10fps、最大1280x720）
$CLI <PID> capture-video ./clip.mp4
$CLI <PID> capture-video ./node.mp4 --nodeId <NODE_ID> --durationSeconds 10
```

> すべてのコマンドは JSON を出力します。`--json` フラグは存在しません（渡すとエラーになります）。
> `--props` フラグは `scenegraph` コマンド専用です。`node-details` では `--filter` を使用してください。
> `stages` には表示中の `PopupWindow` も含まれます。`stageId` は互換性のため残された名前で、
> Stage とポップアップのどちらのウィンドウ ID にも使用できます。

CLI の完全なオプション仕様は `skills/fxgraph/references/` を参照してください。

## テスト実行

```bash
# 全テストを実行
./gradlew test

# モジュール別テスト
./gradlew :fxgraph-core:test
./gradlew :fxgraph-agent:test        # Monocle (headless) で JavaFX テストを実行
./gradlew :fxgraph-cli:test
./gradlew :fxgraph-mcp-server:test   # 統合テスト（事前に shadowJar が必要）

# 統合テストの事前準備
./gradlew :fxgraph-mcp-server:shadowJar && ./gradlew :fxgraph-mcp-server:test
```

`fxgraph-agent` モジュールのテストは Monocle ヘッドレスモードで実行されるため、CI 環境でも追加設定不要です。

## 動作確認用サンプルアプリ

AtlantaFX (NordDark テーマ) を使った Todo リストアプリをサンプルとして同梱しています。

```bash
./gradlew :javafx-test-app:run
```

## 技術スタック

| ライブラリ | バージョン | 用途 |
|---|---|---|
| Java | 21 | 言語・ランタイム |
| Spring Boot | 4.0.2 | MCP サーバー基盤 |
| Spring AI MCP | 1.1.2 | MCP プロトコル実装 |
| Jackson | 2.18.3 | JSON シリアライズ |
| JavaFX | 21 | 対象プラットフォーム |
| JUnit | 5.10.0 | テストフレームワーク |
| Mockito | 5.11.0 | テストモック |
| AtlantaFX | 2.0.1 | テスト用サンプルアプリ |

## 注意事項

- **STDIO トランスポートのみ対応** — 複数の AI エージェントからの同時接続はできません。
- **ノード ID はセッション内でのみ安定** — `System.identityHashCode(node)` を使用しているため、JVM 再起動後は変化します。
- **ポップアップ検査** — `ContextMenu`・`MenuButton` のメニュー・`Tooltip` などは表示中のみ列挙されます。`windowType` と `ownerWindowId` で所有 Stage と対応付けられます。
- **スクリーンショット境界** — ポップアップ ID を指定するとそのポップアップシーン単体を取得できます。所有 Stage とポップアップを合成した1枚が必要な場合は OS の画面キャプチャを使用してください。
- **Java Attach API** — 一部の JVM 設定では `AttachNotSupportedException` が発生する場合があります。その際は起動オプションに `-Djdk.attach.allowAttachSelf=true` の追加をお試しください。
- **MCP サーバーログ** — STDIO を汚染しないよう、ログはすべてローリングファイル (`fxgraph.log`) へ出力されます。
- **UI 操作の実現性** — クリック・キー入力エミュレーションは OS のセキュリティ制限により低レベルな入力注入が不可能なため、JavaFX イベントシステム経由での模倣を行っています。このため、対象アプリケーションの実装次第では動作しない場合があります（例：ネイティブイベントに依存するコントロール、カスタム描画ノードなど）。

## 関連

- [`Scenic View`](https://github.com/JonathanGiles/scenic-view) — JavaFX アプリのシーングラフをリアルタイムに可視化するツール。アイデアの着想元。
- [`docs/tools-reference.md`](docs/tools-reference.md) — MCP ツールの詳細リファレンス（入出力スキーマ付き）
- [`skills/fxgraph/SKILL.md`](skills/fxgraph/SKILL.md) — Agent Skills の定義とワークフロー
- [`skills/fxgraph/references/inspect-commands.md`](skills/fxgraph/references/inspect-commands.md) — CLI 読み取り系コマンドの詳細
- [`skills/fxgraph/references/interact-commands.md`](skills/fxgraph/references/interact-commands.md) — CLI 操作系コマンドの詳細
- [`AGENTS.md`](AGENTS.md) — コード生成・レビュー向けの開発ガイドライン
