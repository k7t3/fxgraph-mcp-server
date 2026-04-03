# Skills

このディレクトリには、FXGraph CLI ツールと連携するための Agent Skills が含まれています。

## Skills

| Skill | 説明 |
|-------|-------------|
| [`fxgraph/`](fxgraph/SKILL.md) | 実行中の JavaFX アプリケーションの検査および操作 |

## CLI jar のビルドとインストール

`fxgraph` skill を使用するには、`skills/fxgraph/scripts/` に `fxgraph-cli.jar` と `fxgraph-agent.jar` が必要です。`installSkillJars` Gradle タスクを使用すると、一度で両方のビルドとデプロイが可能です。

### 前提条件

- Java 21+
- リポジトリに含まれる Gradle wrapper (`./gradlew`)

### インストール

```bash
# プロジェクトルートから実行 — jars をビルドして skills/fxgraph/scripts/ にコピー
./gradlew :cli:installSkillJars
```

このタスクは以下の処理を行います：
1. `fxgraph-cli.jar` のコンパイルとパッケージ化（`shadowJar` を経由）
2. `fxgraph-agent.jar` を CLI jar の隣にコピー（`copyAgentJar` を経由）
3. 両方の jars を `skills/fxgraph/scripts/` にデプロイ

タスク完了後の状態：
```
skills/fxgraph/scripts/
├── fxgraph-cli.jar    # CLI ツール
└── fxgraph-agent.jar  # ターゲット JVM に注入されるエージェント
```

> **注意**: これらの jar ファイルはバージョン管理から除外されています (`.gitignore`)。
> ソース変更後は `installSkillJars` を再実行してください。

### クイックスタート

```bash
# ビルドとインストール
./gradlew :cli:installSkillJars

# CLI 変数の設定（シェルセッションに追加）
CLI="java -jar <path-to-skill>/scripts/fxgraph-cli.jar"

# 実行中の JavaFX プロセス一覧
$CLI discover

# 最初に見つかったプロセスのシーングラフを探索
PID=$($CLI discover | jq '.[0].pid')
$CLI $PID scenegraph --depth 3
```

### 注意事項

- `fxgraph-agent.jar` は、PID に対して最初のコマンドを実行した際に自動的にターゲット JVM に注入されます — 手動のステップは不要です。
- エージェントは Java Attach API を使用しており、一部の JVM 設定では `--add-opens` フラグが必要になる場合があります。`AttachNotSupportedException` に遭遇した場合は、プロジェクトの `README.md` を参照してください。
- ソース変更後は同じ `./gradlew :cli:installSkillJars` コマンドで再ビルドしてください。
