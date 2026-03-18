# FXGraph MCP Server - ツールリファレンス

FXGraph MCP Serverは、JavaFXアプリケーションのシーングラフを分析・操作するためのMCPサーバーです。

## 概要

このMCPサーバーは以下の機能を提供します：
- 実行中のJavaFXアプリケーションの検出と接続
- シーングラフ構造の取得と分析
- ノードプロパティの取得と変更
- ノードの選択とハイライト
- ノードのクリック・フォーカス要求・キー入力（JavaFXイベントシステム）
- ノード/シーングラフのスクリーンショット取得

## アーキテクチャ

MCPサーバーはJava Attach APIを使用して対象のJavaFX JVMにインスペクタエージェントを注入します。エージェントはTCPソケット上でJSON行区切りプロトコルを使用してMCPサーバーと通信します。

```
MCP Client  <-->  MCP Server (STDIO)  <-->  Agent (TCP Socket)  <-->  JavaFX Scene Graph
```

### トランスポートの制限

このMCPサーバーはSTDIO（標準入出力）トランスポートのみをサポートします。STDIOトランスポートは1つのAIエージェントとの1対1通信を前提としているため、複数のAIエージェントから同時に利用することはできません。

## ツール一覧

### 1. discoverApplications

実行中のJavaFXアプリケーションを検出します。

**説明**: Discover running JavaFX applications. Returns a list of JVM processes that are identified as JavaFX applications, with their PIDs and main classes.

**入力パラメータ**: なし

**出力例**:
```json
{
  "success": true,
  "applications": [
    {
      "pid": 12345,
      "mainClass": "com.example.MyApp",
      "vmName": "OpenJDK 64-Bit Server VM",
      "javaFX": true,
      "connected": false
    }
  ]
}
```

---

### 2. connectApplication

PIDを指定してJavaFXアプリケーションに接続します。対象JVMにインスペクタエージェントを注入し、通信チャネルを確立します。すでに接続済みのPIDを指定した場合は既存のセッションを返します。

**説明**: Connect to a JavaFX application by PID. This injects an inspection agent into the target JVM and establishes a communication channel. If the application is already connected, the existing session is returned. Returns a sessionId to use with other tools.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |

**出力例**:
```json
{
  "success": true,
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "agentPort": 54321
}
```

---

### 3. disconnectApplication

接続済みのJavaFXアプリケーションから切断します。

**説明**: Disconnect from a connected JavaFX application and clean up resources.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID obtained from connectApplication |

**出力例**:
```json
{
  "success": true
}
```

---

### 4. getStages

接続済みアプリケーションのステージ（ウィンドウ）一覧を取得します。

**説明**: Get the list of JavaFX Stages (windows) in the connected application. Each stage has a stageId, title, dimensions, and a rootNodeId pointing to the root of its scene graph.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |

**出力例**:
```json
{
  "success": true,
  "data": [
    {
      "stageId": "123456789",
      "title": "Main Window",
      "width": 800,
      "height": 600,
      "x": 100,
      "y": 100,
      "focused": true,
      "rootNodeId": 987654321
    }
  ]
}
```

---

### 5. getScenegraph

接続済みのJavaFXアプリケーションからシーングラフ構造を取得します。デフォルトでは軽量なツリー構造のみを返します。

**説明**: Get the scene graph tree structure from a connected JavaFX application. Returns a lightweight hierarchical tree by default. Use depth to limit tree depth, includeProperties to get property details, propertyFilter to limit which properties, and includeTransforms for transform details.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| stageId | string | いいえ | Stage ID to inspect (omit to get all stages) |
| depth | integer | いいえ | Maximum depth to traverse (default: unlimited) |
| includeProperties | boolean | いいえ | Include property details for each node (default: false) |
| propertyFilter | array<string> | いいえ | List of property names to include (e.g., ["text", "value"]). Only used when includeProperties=true. Omit to get all properties. |
| includeTransforms | boolean | いいえ | Include transform properties (opacity, scale, rotate) when they differ from defaults (default: false) |

**出力例（デフォルト - 軽量モード）**:
```json
{
  "success": true,
  "stages": [
    {
      "stageId": "123456789",
      "title": "Main Window",
      "width": 800,
      "height": 600,
      "x": 100,
      "y": 100,
      "focused": true,
      "rootNodeId": 987654321
    }
  ],
  "rootNodes": [
    {
      "nodeId": 987654321,
      "id": "root",
      "type": "VBox",
      "visible": true,
      "styleClass": ["root"],
      "bounds": { "x": 0, "y": 0, "w": 800, "h": 600 },
      "children": [
        {
          "nodeId": 123456,
          "type": "Button",
          "visible": true,
          "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 }
        }
      ]
    }
  ],
  "totalNodeCount": 5
}
```

**出力例（includeProperties=true, propertyFilter=["text"])**:
```json
{
  "nodeId": 123456,
  "id": null,
  "type": "Button",
  "visible": true,
  "styleClass": ["button"],
  "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 },
  "properties": [
    {
      "name": "text",
      "value": "Click Me",
      "type": "string",
      "writable": true,
      "category": "content"
    }
  ],
  "children": []
}
```

---

### 6. getNodeDetails

特定のノードの詳細情報を取得します。

**説明**: Get detailed information about a specific node including all its properties, children summary, bounds, style classes, and more. Use the nodeId obtained from getScenegraph. Optionally filter properties with propertyFilter.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID (identityHashCode of the JavaFX Node) |
| propertyFilter | array<string> | いいえ | List of property names to include (e.g., ["text", "value"]). Omit to get all properties. |

**出力例**:
```json
{
  "success": true,
  "node": {
    "nodeId": 123456,
    "id": "myButton",
    "type": "Button",
    "visible": true,
    "styleClass": ["button", "primary"],
    "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 },
    "opacity": 1.0,
    "scaleX": 1.0,
    "scaleY": 1.0,
    "rotate": 0.0
  },
  "properties": [
    {
      "name": "text",
      "value": "Click Me",
      "type": "string",
      "writable": true,
      "category": "content"
    },
    {
      "name": "visible",
      "value": true,
      "type": "boolean",
      "writable": true,
      "category": "visual"
    }
  ],
  "children": [
    {
      "nodeId": 789012,
      "type": "LabeledText",
      "id": null,
      "visible": true
    }
  ]
}
```

---

### 7. setProperty

ノードのプロパティ値を設定します。

**説明**: Set a property value on a JavaFX node. Supports setting text, numbers, booleans, colors, and style strings. Returns the old and new values.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID |
| propertyName | string | はい | Property name (e.g. 'text', 'style', 'visible', 'opacity') |
| value | string | はい | New value as string |
| valueType | string | いいえ | Value type hint: string, number, boolean, color (optional) |

**出力例**:
```json
{
  "success": true,
  "oldValue": "Previous Text",
  "newValue": "Hello World"
}
```

**スタイルを設定する例**:
```json
{
  "sessionId": "...",
  "nodeId": 123,
  "propertyName": "style",
  "value": "-fx-background-color: #FF0000;",
  "valueType": "string"
}
```

---

### 8. selectNode

対象アプリケーションでノードを選択/ハイライト表示します。

**説明**: Highlight/select a node in the target JavaFX application by drawing a visual overlay (red border). Pass nodeId=0 to clear the highlight.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID (use 0 to clear selection) |
| showBounds | boolean | いいえ | Show bounds rectangle overlay (default: true) |

**出力例**:
```json
{
  "success": true,
  "highlighted": true
}
```

**効果**: 対象のJavaFXアプリケーション内で指定されたノードが視覚的にハイライトされます（赤い枠線）。`nodeId=0`でハイライトを解除します。

---

### 9. clickNode

指定したノードにクリックイベントを送信します。

**説明**: Click a JavaFX node by nodeId using JavaFX Event System for simulated input. The click event is fired directly on the target node.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID |

**出力例**:
```json
{
  "success": true,
  "clicked": true
}
```

---

### 10. requestFocus

指定したノードにフォーカスを要求します。

**説明**: Request keyboard focus for a JavaFX node by nodeId.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID |

**出力例**:
```json
{
  "success": true,
  "focused": true
}
```

---

### 11. typeKey

キー入力イベントを送信します。

**説明**: Type a key into a JavaFX node using JavaFX Event System. If nodeId is omitted, the currently focused node is used.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| key | string | はい | Key text or key code name (e.g. 'a', 'ENTER') |
| nodeId | integer | いいえ | Target node ID (optional, defaults to focused node) |

**出力例**:
```json
{
  "success": true,
  "typed": true
}
```

---

### 12. takeScreenshot

指定したノード、またはシーングラフ全体のスクリーンショットを取得します。

**説明**: Take a screenshot of a specific node or the whole scene graph. Saves PNG to the specified path.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | いいえ | Target node ID (optional; if omitted, captures full scene graph) |
| stageId | string | いいえ | Stage ID for full scene graph capture (optional) |
| savePath | string | はい | Path to save the PNG screenshot |

**出力例**:
```json
{
  "success": true,
  "mimeType": "image/png",
  "savedPath": "/tmp/fxgraph/screenshot.png",
  "width": 800,
  "height": 600,
  "targetType": "scenegraph",
  "targetId": "123456789"
}
```

---

## データモデル

### JavaFxApplication
```json
{
  "pid": 12345,
  "mainClass": "com.example.Main",
  "vmName": "OpenJDK 64-Bit Server VM",
  "javaFX": true,
  "connected": false
}
```

### StageInfo
```json
{
  "stageId": "123456789",
  "title": "Window Title",
  "width": 800,
  "height": 600,
  "x": 100,
  "y": 100,
  "focused": true,
  "rootNodeId": 987654321
}
```

### SVNode（Scenegraph Node）

**軽量モード（デフォルト）:**
```json
{
  "nodeId": 987654321,
  "id": "node-id",
  "type": "Button",
  "visible": true,
  "styleClass": ["button"],
  "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 },
  "children": []
}
```

**includeTransforms=true の場合:**
```json
{
  "nodeId": 987654321,
  "id": "node-id",
  "type": "Button",
  "visible": true,
  "styleClass": ["button"],
  "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 },
  "opacity": 0.5,
  "scaleX": 1.2,
  "rotate": 45.0,
  "children": []
}
```

**includeProperties=true の場合:**
```json
{
  "nodeId": 987654321,
  "id": "node-id",
  "type": "Button",
  "visible": true,
  "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 },
  "properties": [...],
  "children": []
}
```

### PropertyDetail
```json
{
  "name": "text",
  "value": "Button Text",
  "type": "string",
  "writable": true,
  "category": "content"
}
```

プロパティカテゴリ:
- `layout` - レイアウト関連（spacing, padding, alignment等）
- `style` - スタイル関連（background, border, font, color等）
- `visual` - 表示関連（visible, opacity, rotate, scale等）
- `content` - コンテンツ関連（text, graphic, value, selected等）
- `interaction` - インタラクション関連（event, handler, mouse, focus等）
- `properties` - その他

### Bounds
```json
{
  "minX": 0,
  "minY": 0,
  "width": 800,
  "height": 600
}
```

---

## 使用フロー例

### 基本的な調査フロー

1. **アプリケーションを検出**:
   ```
   discoverApplications()
   → { "applications": [{ "pid": 12345, "javaFX": true, ... }] }
   ```

2. **アプリケーションに接続**:
   ```
   connectApplication(pid: 12345)
   → { "sessionId": "xxx", "agentPort": 54321, "success": true }
   ```

3. **ステージ一覧を取得**:
   ```
   getStages(sessionId: "xxx")
   → { "data": [{ "stageId": "123", "title": "Main Window", ... }] }
   ```

4. **シーングラフを取得**:
   ```
   getScenegraph(sessionId: "xxx", depth: 3)
   → { "stages": [...], "rootNodes": [...], "totalNodeCount": 42 }
   ```

5. **特定ノードの詳細を取得**:
   ```
   getNodeDetails(sessionId: "xxx", nodeId: 123456)
   → { "node": {...}, "properties": [...], "children": [...] }
   ```

6. **ノードをハイライト**:
   ```
   selectNode(sessionId: "xxx", nodeId: 123456)
   ```

7. **切断**:
   ```
   disconnectApplication(sessionId: "xxx")
   ```

### プロパティ変更フロー

1. **ノードの現在のプロパティを確認**:
   ```
   getNodeDetails(sessionId: "xxx", nodeId: 123456)
   ```

2. **プロパティを変更**:
   ```
   setProperty(
     sessionId: "xxx",
     nodeId: 123456,
     propertyName: "text",
     value: "New Text"
   )
   → { "oldValue": "Old Text", "newValue": "New Text" }
   ```

3. **ノードをハイライトして視覚的に確認**:
   ```
   selectNode(sessionId: "xxx", nodeId: 123456, showBounds: true)
   ```

4. **ハイライトを解除**:
   ```
   selectNode(sessionId: "xxx", nodeId: 0)
   ```

---

## エラーハンドリング

すべてのツールは以下の形式でエラーを返します：

```json
{
  "success": false,
  "error": "エラーメッセージ"
}
```

### 一般的なエラー

- `Session not found or disconnected` - 無効なセッションIDが指定されたか、接続が切れた
- `Node not found` - 指定されたノードIDが存在しない
- `Failed to connect to JavaFX application` - JavaFXアプリケーションへの接続に失敗
- `Property not found or not writable` - 指定されたプロパティが存在しないか読み取り専用
- `Agent started but port not found` - エージェントの起動に失敗

---

## 技術仕様

- **MCP Server Type**: SYNC
- **Transport**: STDIO
- **Server Name**: fxgraph-mcp-server
- **Version**: 1.0.0
- **Java Version**: 21+
- **Spring Boot**: 4.0.2
- **Spring AI MCP**: 1.1.2
- **ノードID**: `System.identityHashCode(node)` を使用（Scenic Viewの `node.hashCode()` と同等のアプローチ）
