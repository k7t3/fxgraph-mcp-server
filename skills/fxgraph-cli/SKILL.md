# FXGraph CLI Skill

FXGraph CLI は、`fxgraph-mcp-server` が提供している JavaFX シーングラフ調査・操作機能を、MCP ではなく CLI から使うためのスキルです。  
各コマンドは JSON を標準出力に返すため、`jq` や他の CLI とパイプで連携しやすくなっています。

## 起動

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli --help
```

## 典型フロー

### 1. JavaFX アプリを見つける

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli discoverApplications
```

### 2. 既存エージェントを常駐化する

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli connectApplication --pid 12345
```

> `connectApplication` は対象 JVM にエージェントを注入し、以降の CLI 実行から再利用できる状態にします。

### 3. ステージやシーングラフを取得する

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli getStages --pid 12345
java -jar mcp-server/build/libs/mcp-server.jar cli getScenegraph --pid 12345 --depth 3 --includeBounds
```

### 4. 特定ノードを調べる

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli getNodeDetails --pid 12345 --nodeId 456 --propertyFilter text,visible
```

### 5. ノードを変更・操作する

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli setProperty --pid 12345 --nodeId 456 --propertyName text --value "Updated"
java -jar mcp-server/build/libs/mcp-server.jar cli selectNode --pid 12345 --nodeId 456
java -jar mcp-server/build/libs/mcp-server.jar cli clickNode --pid 12345 --nodeId 456
```

### 6. スクリーンショットを取る

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli takeScreenshot --pid 12345 --stageId 789 --savePath /tmp/fxgraph.png
```

### 7. 常駐エージェントを停止する

```bash
java -jar mcp-server/build/libs/mcp-server.jar cli disconnectApplication --pid 12345
```

## 使い方の指針

- `discoverApplications` 以外のコマンドは `--pid` を受け取り、必要に応じて自動的に接続します。
- `connectApplication` 済みの JVM に対しては、後続コマンドが既存エージェントへ再接続します。
- 出力はコンパクト JSON なので、必要なら呼び出し側で整形してください。
