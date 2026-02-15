# FXGraph MCP Server - ツールリファレンス

FXGraph MCP Serverは、JavaFXアプリケーションのシーングラフを分析・操作するためのMCPサーバーです。

## 概要

このMCPサーバーは以下の機能を提供します：
- 実行中のJavaFXアプリケーションの検出と接続
- シーングラフ構造の取得と分析
- ノードプロパティの監視と変更
- ノードの選択とハイライト

## ツール一覧

### 1. discoverApplications

実行中のJavaFXアプリケーションを検出します。

**説明**: Discover running JavaFX applications

**入力パラメータ**: なし（Void）

**出力例**:
```json
{
  "applications": [
    {
      "pid": 12345,
      "name": "MyJavaFxApp",
      "mainClass": "com.example.Main"
    }
  ],
  "success": true
}
```

**エラー時の出力**:
```json
{
  "success": false,
  "error": "Failed to access JVM processes"
}
```

---

### 2. connectApplication

PIDを指定してJavaFXアプリケーションに接続します。

**説明**: Connect to a JavaFX application by PID

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the JavaFX application |

**入力例**:
```json
{
  "pid": 12345
}
```

**出力例**:
```json
{
  "success": true,
  "sessionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**エラー時の出力**:
```json
{
  "success": false,
  "error": "Failed to connect to application"
}
```

---

### 3. getScenegraph

接続済みのJavaFXアプリケーションからシーングラフ構造を取得します。

**説明**: Get the scenegraph structure from a JavaFX application

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| stageId | string | いいえ | Stage ID（特定のステージを指定する場合） |
| depth | integer | いいえ | 取得する階層の深さ制限 |
| includeProperties | boolean | いいえ | プロパティ情報を含めるかどうか |

**入力例**:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "stageId": "stage-1",
  "depth": 3,
  "includeProperties": true
}
```

**出力例**:
```json
{
  "stages": [
    {
      "stageId": "stage-1",
      "title": "Main Window",
      "width": 800,
      "height": 600,
      "x": 100,
      "y": 100,
      "focused": true,
      "rootNodeId": 1
    }
  ],
  "rootNodes": [
    {
      "nodeId": 1,
      "id": "root",
      "nodeClass": "VBox",
      "nodeClassName": "javafx.scene.layout.VBox",
      "visible": true,
      "layoutBounds": {
        "minX": 0,
        "minY": 0,
        "width": 800,
        "height": 600
      },
      "boundsInParent": {
        "minX": 0,
        "minY": 0,
        "width": 800,
        "height": 600
      },
      "layoutX": 0,
      "layoutY": 0,
      "nodeType": "REAL_NODE",
      "children": []
    }
  ],
  "totalNodeCount": 1,
  "success": true
}
```

---

### 4. getNodeDetails

特定のノードの詳細情報を取得します。

**説明**: Get detailed information about a specific node

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID |
| detailTypes | array[string] | いいえ | 取得する詳細情報の種類 |

**入力例**:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "nodeId": 123,
  "detailTypes": ["properties", "children", "style"]
}
```

**出力例**:
```json
{
  "node": {
    "nodeId": 123,
    "nodeClass": "VBox",
    "nodeClassName": "javafx.scene.layout.VBox",
    "visible": true,
    "layoutBounds": {
      "minX": 0,
      "minY": 0,
      "width": 800,
      "height": 600
    },
    "boundsInParent": {
      "minX": 0,
      "minY": 0,
      "width": 800,
      "height": 600
    },
    "nodeType": "REAL_NODE"
  },
  "properties": [
    {
      "name": "spacing",
      "value": 10,
      "type": "number",
      "writable": true,
      "category": "layout"
    }
  ],
  "children": [],
  "success": true
}
```

---

### 5. watchNode

ノードの変更を監視します。

**説明**: Watch a node for changes

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID |
| watchChildren | boolean | いいえ | 子ノードの変更も監視するかどうか |
| watchProperties | array[string] | いいえ | 監視するプロパティ名のリスト |

**入力例**:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "nodeId": 123,
  "watchChildren": true,
  "watchProperties": ["text", "visible", "layoutBounds"]
}
```

**出力例**:
```json
{
  "subscriptionId": "sub-a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "success": true
}
```

**注意**: 監視は非同期で行われ、変更が検出されるとMCPサーバーから通知が送信されます。

---

### 6. unwatchNode

ノードの監視を停止します。

**説明**: Stop watching a node

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| subscriptionId | string | はい | watchNodeで取得したサブスクリプションID |

**入力例**:
```json
{
  "subscriptionId": "sub-a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**出力例**:
```json
{
  "success": true
}
```

---

### 7. setProperty

ノードのプロパティ値を設定します。

**説明**: Set a property value on a node

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID |
| propertyName | string | はい | 設定するプロパティ名 |
| value | any | はい | 新しい値 |
| valueType | string | いいえ | 値の型（string, number, boolean, color等） |

**入力例**:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "nodeId": 123,
  "propertyName": "text",
  "value": "Hello World",
  "valueType": "string"
}
```

**出力例**:
```json
{
  "success": true,
  "oldValue": "Previous Text",
  "newValue": "Hello World"
}
```

**色を設定する例**:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "nodeId": 123,
  "propertyName": "style",
  "value": "-fx-background-color: #FF0000;",
  "valueType": "string"
}
```

---

### 8. selectNode

対象アプリケーションでノードを選択/ハイライト表示します。

**説明**: Select/highlight a node in the target application

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| sessionId | string | はい | Session ID |
| nodeId | integer | はい | Node ID |
| showBounds | boolean | いいえ | 境界ボックスを表示するかどうか |
| showBaseline | boolean | いいえ | ベースラインを表示するかどうか |

**入力例**:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "nodeId": 123,
  "showBounds": true,
  "showBaseline": false
}
```

**出力例**:
```json
{
  "success": true
}
```

**効果**: 対象のJavaFXアプリケーション内で指定されたノードが視覚的にハイライトされます（赤い枠線など）。

---

## データモデル

### JavaFxApplication
```json
{
  "pid": 12345,
  "name": "ApplicationName",
  "mainClass": "com.example.Main"
}
```

### StageInfo
```json
{
  "stageId": "stage-1",
  "title": "Window Title",
  "width": 800,
  "height": 600,
  "x": 100,
  "y": 100,
  "focused": true,
  "rootNodeId": 1
}
```

### SVNode（Scenegraph Node）
```json
{
  "nodeId": 1,
  "id": "node-id",
  "nodeClass": "Button",
  "nodeClassName": "javafx.scene.control.Button",
  "visible": true,
  "layoutBounds": {
    "minX": 0,
    "minY": 0,
    "width": 100,
    "height": 30
  },
  "boundsInParent": {
    "minX": 10,
    "minY": 10,
    "width": 100,
    "height": 30
  },
  "layoutX": 10,
  "layoutY": 10,
  "nodeType": "REAL_NODE",
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
  "category": "properties"
}
```

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
   → { "applications": [{ "pid": 12345, ... }] }
   ```

2. **アプリケーションに接続**:
   ```
   connectApplication(pid: 12345)
   → { "sessionId": "xxx", "success": true }
   ```

3. **シーングラフを取得**:
   ```
   getScenegraph(sessionId: "xxx")
   → { "stages": [...], "rootNodes": [...] }
   ```

4. **特定ノードの詳細を取得**:
   ```
   getNodeDetails(sessionId: "xxx", nodeId: 123)
   → { "node": {...}, "properties": [...] }
   ```

5. **ノードをハイライト**:
   ```
   selectNode(sessionId: "xxx", nodeId: 123, showBounds: true)
   ```

### プロパティ変更フロー

1. **ノードの現在のプロパティを確認**:
   ```
   getNodeDetails(sessionId: "xxx", nodeId: 123)
   ```

2. **プロパティを変更**:
   ```
   setProperty(
     sessionId: "xxx",
     nodeId: 123,
     propertyName: "text",
     value: "New Text"
   )
   ```

3. **変更を確認**（オプション）:
   ```
   watchNode(sessionId: "xxx", nodeId: 123, watchProperties: ["text"])
   → { "subscriptionId": "sub-xxx" }
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

- `Session not found`: 無効なセッションIDが指定された
- `Node not found`: 指定されたノードIDが存在しない
- `Failed to connect to application`: JavaFXアプリケーションへの接続に失敗
- `Property is not writable`: 読み取り専用プロパティへの書き込みを試みた

---

## 技術仕様

- **MCP Server Type**: SYNC
- **Transport**: STDIO
- **Server Name**: fxgraph-mcp-server
- **Version**: 1.0.0
- **Java Version**: 17+
- **Spring Boot**: 3.2.0
- **Spring AI MCP**: 1.1.2
