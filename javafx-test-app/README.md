# javafx-test-app

FXGraph MCP Server の動作確認に使用する **テスト用 JavaFX アプリケーション**です。  
AtlantaFX テーマを使ったシンプルな Todo リストアプリです。

## 概要

MCP Server や CLI ツールを使ってシーングラフを検査・操作する際のターゲットアプリとして使用します。

## 起動方法

```bash
./gradlew :javafx-test-app:run
```

または Shadow JAR（Fat JAR）を使う場合:

```bash
./gradlew :javafx-test-app:build
java -jar javafx-test-app/build/libs/javafx-test-app-1.0-SNAPSHOT.jar
```

> **注記:** 起動後に表示される PID を MCP Server または CLI に渡して接続します。

## 技術スタック

| 項目 | 内容 |
|------|------|
| UI フレームワーク | JavaFX 22.0.1 |
| テーマライブラリ | AtlantaFX 2.0.1 |
| アーキテクチャ | MVVM パターン |

## パッケージ構成

| パッケージ | クラス | 説明 |
|-----------|-------|------|
| `simplefx` | `Main` | アプリケーションエントリポイント |
| `simplefx.model` | `TodoItem` | Todo データモデル |
| `simplefx.viewmodel` | `TodoViewModel` | 状態管理・ビジネスロジック |
| `simplefx.view` | `TodoView`, `TodoListCell` | UI コンポーネント |
| `simplefx.controller` | `TodoController` | イベントハンドラ |
