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
- ノード/任意の JavaFX ウィンドウシーンの短時間 MP4 動画取得

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

PIDを指定してJavaFXアプリケーションを検査できる状態にします。必要に応じて対象JVMへインスペクタエージェントを注入し、通信確認後に一時接続を閉じます。

**説明**: Prepare a JavaFX application for inspection by PID. This injects the inspection agent when necessary, verifies communication, and then closes the transient connection. Other tools connect independently using the same PID.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |

**出力例**:
```json
{
  "success": true,
  "agentPort": 54321
}
```

---

### 3. disconnectApplication

対象JavaFXアプリケーションに注入されたインスペクタエージェントを停止します。通常のツール呼び出しは一時接続を自動的に閉じるため、この操作は必須ではありません。

**説明**: Stop the injected inspection agent in a JavaFX application by PID. Normal tool calls close their transient connections automatically and do not require this operation.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |

**出力例**:
```json
{
  "success": true
}
```

---

### 4. getStages

接続済みアプリケーションで表示中のウィンドウ一覧を取得します。通常の `Stage` に加え、
`ContextMenu`・`Tooltip` などの `PopupWindow` も含まれます。

**説明**: Get the list of showing JavaFX windows, including Stages and PopupWindows such as ContextMenu and Tooltip. Each entry has a stageId (the legacy name for a window ID), windowType, dimensions, and rootNodeId. Popup entries also include ownerWindowId; Stage entries include title.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |

**出力例**:
```json
{
  "success": true,
  "data": [
    {
      "stageId": "123456789",
      "windowType": "Stage",
      "title": "Main Window",
      "width": 800,
      "height": 600,
      "x": 100,
      "y": 100,
      "focused": true,
      "rootNodeId": 987654321
    },
    {
      "stageId": "234567890",
      "windowType": "ContextMenu",
      "ownerWindowId": "123456789",
      "width": 180,
      "height": 96,
      "x": 240,
      "y": 180,
      "focused": false,
      "rootNodeId": 876543210
    }
  ]
}
```

`stageId` は互換性のため残された名前で、Stage とポップアップのどちらのウィンドウ ID
にも使用します。ポップアップは表示中のみ列挙され、`ownerWindowId` で所有 Stage と対応付けられます。

---

### 5. getScenegraph

接続済みのJavaFXアプリケーションからシーングラフ構造を取得します。デフォルトではノードタイプ・ID・子ノードのみを含むコンパクトなツリー構造を返します。

**説明**: Get scene graph trees for showing JavaFX Stage and PopupWindow scenes. Returns compact hierarchical trees by default. Use depth to limit tree depth, includeBounds to include node bounding boxes, includeProperties to get property details, propertyFilter to limit which properties, and includeTransforms for transform details.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
| stageId | string | いいえ | Window ID from the stageId field (omit to get all showing windows) |
| depth | integer | いいえ | Maximum depth to traverse (default: unlimited) |
| includeBounds | boolean | いいえ | Include bounding box (x,y,w,h) for each node (default: false) |
| includeProperties | boolean | いいえ | Include property details for each node (default: false) |
| propertyFilter | array<string> | いいえ | List of property names to include (e.g., ["text", "value"]). Only used when includeProperties=true. Omit to get all properties. |
| includeTransforms | boolean | いいえ | Include transform properties (opacity, scale, rotate) when they differ from defaults (default: false) |

**出力例（デフォルト - コンパクトモード）**:
```json
{
  "success": true,
  "stages": [
    {
      "stageId": "123456789",
      "windowType": "Stage",
      "title": "Main Window",
      "rootNodeId": 987654321
    }
  ],
  "rootNodes": [
    {
      "nodeId": 987654321,
      "id": "root",
      "type": "VBox",
      "styleClass": ["root"],
      "children": [
        {
          "nodeId": 123456,
          "type": "Button"
        }
      ]
    }
  ]
}
```

> **注意**: `stages` キー名も互換性のため維持され、ポップアップのエントリを含む場合があります。`visible: false` のノードのみ `visible` フィールドが含まれます。CSS IDが未設定のノードは `id` フィールドを持ちません。ウィンドウの位置・サイズが必要な場合は `getStages` を使用してください。

**出力例（includeBounds=true）**:
```json
{
  "nodeId": 987654321,
  "id": "root",
  "type": "VBox",
  "styleClass": ["root"],
  "bounds": { "x": 0, "y": 0, "w": 800, "h": 600 },
  "children": [
    {
      "nodeId": 123456,
      "type": "Button",
      "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 }
    }
  ]
}
```

**出力例（includeProperties=true, propertyFilter=["text"])**:
```json
{
  "nodeId": 123456,
  "type": "Button",
  "properties": [
    {
      "name": "text",
      "value": "Click Me",
      "type": "string",
      "writable": true,
      "category": "content"
    }
  ]
}
```

---

### 6. getNodeDetails

特定のノードの詳細情報を取得します。

**説明**: Get detailed information about a specific node including all its properties, children summary, bounds, style classes, and more. Use the nodeId obtained from getScenegraph. Optionally filter properties with propertyFilter.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
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

### 7. findNodes

表示中の Stage とポップアップのシーングラフから条件に一致するノードを検索します。タイプ・CSS ID・テキスト・スタイルクラスを組み合わせて指定できます。

**説明**: Search showing JavaFX Stage and PopupWindow scene graphs by type, CSS id, text content, or style class. Returns matching nodes with their nodeId, type, id, and text. Use stageId to limit search to a specific window.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
| type | string | いいえ | JavaFX class name to filter (e.g., 'Button', 'TextField') |
| id | string | いいえ | CSS id (fx:id) to match exactly |
| text | string | いいえ | Text content to search for (case-sensitive contains match) |
| styleClass | string | いいえ | Style class name to filter |
| stageId | string | いいえ | Window ID from the stageId field; omit to search all showing windows |

**出力例**:
```json
{
  "success": true,
  "data": [
    {
      "nodeId": 104383453,
      "type": "Button",
      "id": "submitBtn",
      "text": "Submit",
      "visible": true
    },
    {
      "nodeId": 548650702,
      "type": "Button",
      "text": "Clear",
      "visible": true
    }
  ]
}
```

**検索例**:
```json
{
  "pid": 12345,
  "type": "Button"
}
```

```json
{
  "pid": 12345,
  "id": "usernameField"
}
```

```json
{
  "pid": 12345,
  "text": "Submit",
  "stageId": "123456789"
}
```

---

### 8. setProperty

ノードのプロパティ値を設定します。

**説明**: Set a property value on a JavaFX node. Supports setting text, numbers, booleans, colors, and style strings. Returns the old and new values.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
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
  "pid": 12345,
  "nodeId": 123,
  "propertyName": "style",
  "value": "-fx-background-color: #FF0000;",
  "valueType": "string"
}
```

---

### 9. selectNode

対象アプリケーションでノードを選択/ハイライト表示します。

**説明**: Highlight/select a node in the target JavaFX application by drawing a visual overlay (red border). Pass nodeId=0 to clear the highlight.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
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

### 10. clickNode

指定したノードの中央へクリックジェスチャーを送ります。

**説明**: 既定では `MOUSE_PRESSED`、`MOUSE_RELEASED`、`MOUSE_CLICKED` の完全な合成ジェスチャーを送り、システムポインターとウィンドウフォーカスを変更しません。`mode=robot` を明示すると JavaFX `Robot` がウィンドウへフォーカスを要求し、マウスをノード中央へ移動してプライマリボタンの press/release を送ります。明示した `Robot` が利用できない場合は合成ジェスチャーへ自動的にフォールバックします。ノードまたは祖先が非表示の場合、disabled の場合、またはサイズがゼロの場合はエラーを返します。

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
| nodeId | integer | はい | Node ID |
| mode | string | いいえ | `synthetic`（既定）または `robot` |

**出力例**:
```json
{
  "success": true,
  "clicked": true,
  "mode": "synthetic"
}
```

明示した `Robot` から合成ジェスチャーへフォールバックした場合は、`mode` が `synthetic` となり、`fallbackReason` が追加されます。

---

### 11. activateNode

指定した `ButtonBase` をマウス入力なしに論理的に起動します。

**説明**: `ButtonBase.fire()` を呼び出します。マウスハンドラやヒットテストを検証するときは `clickNode` を使用してください。

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
| nodeId | integer | はい | ButtonBase node ID |

**出力例**:
```json
{
  "success": true,
  "activated": true
}
```

---

### 12. requestFocus

指定したノードにフォーカスを要求します。

**説明**: Request keyboard focus for a JavaFX node by nodeId.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
| nodeId | integer | はい | Node ID |

**出力例**:
```json
{
  "success": true,
  "focused": true
}
```

---

### 13. typeKey

キー入力イベントを送信します。

**説明**: Type a key into a JavaFX node using JavaFX Event System. If nodeId is omitted, the currently focused node is used.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
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

### 14. takeScreenshot

指定したノード、または1つの JavaFX ウィンドウシーンのスクリーンショットを取得します。
ポップアップの ID を指定した場合は、そのポップアップシーン単体を取得します。

**説明**: Take a screenshot of a specific node or one JavaFX window scene, including an individually selected popup scene. Saves PNG to the specified path.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
| nodeId | integer | いいえ | Target node ID (optional; if omitted, captures full scene graph) |
| stageId | string | いいえ | Window ID from the stageId field; first available Stage when omitted |
| savePath | string | はい | Path to save the PNG screenshot |
| maxWidth | integer | いいえ | Maximum screenshot width (default: 1280) |
| maxHeight | integer | いいえ | Maximum screenshot height (default: 720) |

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

`Scene.snapshot` は1ウィンドウ単位です。所有 Stage とポップアップを合成した画像が必要な場合は、
OS の画面キャプチャを使用してください。

---

### 15. captureVideo

指定したノード、または1つの JavaFX ウィンドウシーンを短い動画クリップとして取得します。録音は行いません。

**説明**: Capture a silent MP4/H.264 video clip of a specific JavaFX node or one window scene, including an individually selected popup scene. Duration is limited to 30 seconds.

**入力パラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| pid | integer | はい | Process ID of the target JavaFX application |
| nodeId | integer | いいえ | Target node ID (takes precedence over stageId) |
| stageId | string | いいえ | Window ID from the stageId field; first available Stage when omitted |
| savePath | string | はい | Path to save the MP4 video clip |
| durationSeconds | integer | いいえ | Clip duration from 1 through 30 seconds (default: 5) |
| framesPerSecond | integer | いいえ | Frame rate from 1 through 30 (default: 10) |
| maxWidth | integer | いいえ | Maximum video width (default: 1280) |
| maxHeight | integer | いいえ | Maximum video height (default: 720) |

**出力例**:
```json
{
  "success": true,
  "mimeType": "video/mp4",
  "codec": "H.264",
  "savedPath": "/tmp/fxgraph/clip.mp4",
  "width": 1280,
  "height": 720,
  "durationSeconds": 5,
  "framesPerSecond": 10,
  "frameCount": 50,
  "targetType": "scenegraph",
  "targetId": "123456789"
}
```

録画は同期処理で、完了した MP4 が保存されてから応答します。フレームは `Node.snapshot` または
1ウィンドウの `Scene.snapshot` で取得します。ポップアップ ID を指定するとそのシーン単体を録画できますが、
所有 Stage・別ウィンドウ・OS のウィンドウ装飾は同じ映像へ合成されません。

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

### WindowInfo（旧 StageInfo）
```json
{
  "stageId": "123456789",
  "windowType": "Stage",
  "title": "Window Title",
  "width": 800,
  "height": 600,
  "x": 100,
  "y": 100,
  "focused": true,
  "rootNodeId": 987654321
}
```

`title` は Stage、`ownerWindowId` は所有ウィンドウを持つポップアップでのみ出力されます。

### SceneGraphNode

**デフォルト（コンパクトモード）:**
```json
{
  "nodeId": 987654321,
  "type": "Button"
}
```

> フィールドの省略ルール:
> - `id` (CSS ID): 設定されている場合のみ出力
> - `visible`: `false` の場合のみ出力（デフォルトは `true`）
> - `styleClass`: 空でない場合のみ出力
> - `bounds`: `includeBounds=true` の場合のみ出力
> - `children`: 子ノードがある場合のみ出力

**CSS IDが設定されたノード:**
```json
{
  "nodeId": 987654321,
  "id": "myButton",
  "type": "Button",
  "styleClass": ["button", "primary"]
}
```

**非表示ノード:**
```json
{
  "nodeId": 987654321,
  "type": "Label",
  "visible": false
}
```

**includeBounds=true の場合:**
```json
{
  "nodeId": 987654321,
  "type": "Button",
  "bounds": { "x": 10, "y": 10, "w": 100, "h": 30 }
}
```

**includeTransforms=true の場合（デフォルト値と異なる場合のみ）:**
```json
{
  "nodeId": 987654321,
  "type": "Button",
  "opacity": 0.5,
  "scaleX": 1.2,
  "rotate": 45.0
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
   → { "agentPort": 54321, "success": true }
   ```

3. **表示中のウィンドウ一覧を取得**:
   ```
   getStages(pid: 12345)
   → { "data": [{ "stageId": "123", "windowType": "Stage", "title": "Main Window", ... }] }
   ```

4. **シーングラフを取得**:
   ```
   getScenegraph(pid: 12345, depth: 3)
   → { "stages": [{"stageId":"123","windowType":"Stage","title":"Main Window","rootNodeId":987654321}], "rootNodes": [...] }
   ```

5. **特定ノードの詳細を取得**:
   ```
   getNodeDetails(pid: 12345, nodeId: 123456)
   → { "node": {...}, "properties": [...], "children": [...] }
   ```

6. **ノードをハイライト**:
   ```
   selectNode(pid: 12345, nodeId: 123456)
   ```

7. **切断**:
   ```
   disconnectApplication(pid: 12345)
   ```

### プロパティ変更フロー

1. **ノードの現在のプロパティを確認**:
   ```
   getNodeDetails(pid: 12345, nodeId: 123456)
   ```

2. **プロパティを変更**:
   ```
   setProperty(
     pid: 12345,
     nodeId: 123456,
     propertyName: "text",
     value: "New Text"
   )
   → { "oldValue": "Old Text", "newValue": "New Text" }
   ```

3. **ノードをハイライトして視覚的に確認**:
   ```
   selectNode(pid: 12345, nodeId: 123456, showBounds: true)
   ```

4. **ハイライトを解除**:
   ```
   selectNode(pid: 12345, nodeId: 0)
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

- `PID must be a positive integer` - PIDに0以下の値が指定された
- `Communication error with PID ...` - 対象JVMへの接続または通信に失敗
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
- **ノードID**: `System.identityHashCode(node)` を使用
