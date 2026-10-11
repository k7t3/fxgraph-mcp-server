# javafx-test-app

FXGraph MCP サーバーと `skills/fxgraph` の機能を試すための JavaFX アプリです。
機能別の7タブ、操作結果、直近100件のイベントログを備えています。
画面上部に対象 JVM の PID を表示します。「初期状態に戻す」で補助ウィンドウ、
ポップアップ、アニメーションを終了し、全タブの内容を作り直せます。
データはメモリ内だけに保存されます。

## 起動

リポジトリのルートで実行します。

```bash
./gradlew :javafx-test-app:run
```

Windows では `gradlew.bat :javafx-test-app:run` を使います。
起動時の JavaFX モジュール設定が必要なので、上記の Gradle タスクで起動してください。

## 確認画面

CSS ID は検索用の固定名です。数値の `nodeId`、`stageId` は実行時に取得します。
タブの見出しも `tab-input` などの CSS ID を持つ Label なので、`click-node` で切り替えられます。
操作前に対象のタブを選択してください。既に開いたタブの非表示ノードも検索結果に含まれるため、
`--visible-only` で実効可視性を絞り込めます。表示中の補助ウィンドウ・ポップアップも検索できます。

| タブ / 見出しの ID | 確認対象 | 主な CSS ID / 結果 |
|---|---|---|
| 入力・イベント / `tab-input` | 文字列・数値・boolean、クリックのイベント列、起動、キー、フォーカス、Ctrl+K / Meta+K | `input-text`, `input-submit`, `input-check`, `input-number`, `input-values`, `input-result`, `gesture-target`, `mouse-status`, `action-status`, `key-target`, `key-status`, `shortcut-status`, `focus-status` |
| プロパティ・可視性 / `tab-inspection` | 色・CSS・変形、ハイライト、無効状態、非表示の祖先、透明な祖先、クリップ、SubScene の祖先経路 | `property-target`（style class: `inspection-target`）, `disabled-target`, `hidden-parent`, `hidden-target`, `transparent-target`, `partial-target`, `clipped-target`, `clip-parent`, `inspection-subscene`, `subscene-target` |
| ウィンドウ / `tab-windows` | 通常・終了拒否・終了確認の Stage、非モーダル・待機するモーダル、Popup、Tooltip、AtlantaFX ModalPane | `open-window`, `open-guarded-window`, `open-confirm-window`, `open-dialog`, `open-modal-dialog`, `show-popup`, `show-tooltip`, `show-overlay`, `window-status`, `dialog-status`, `popup-status`, `overlay-status` |
| メニュー / `tab-menus` | ContextMenu、MenuButton、MenuBar、無効項目、チェック項目、2段のサブメニュー、モーダルを開く項目 | `context-target`, `open-context-menu`, `menu-button`, `menu-bar`, `menu-status` |
| スクロール / `tab-scroll` | ListView、TableView、2方向の ScrollPane、JavaFX VirtualFlow、Flowless VirtualFlow、VirtualizedScrollPane | `scroll-list`, `scroll-table`, `scroll-pane`, `flowless-flow`, `wrapped-flow`, `flowless-wrapper`, `scroll-position`, `scroll-selection` |
| 画像・動画 / `tab-capture` | 原寸・縮小 PNG、動く対象の MP4、再生・一時停止・停止 | `capture-target`（640 × 300）, `animation-marker`, `animation-start`, `animation-pause`, `animation-stop`, `animation-status` |
| Todo / `tab-todo` | 既存のタスク追加・完了・フィルター・削除 | `todo-title`, `todo-description`, `todo-add`, `todo-list`, `todo-filter-all`, `todo-filter-active`, `todo-filter-completed`, `todo-clear-completed`, `todo-status` |

共通: `workbench-reset` は全体の再初期化、`app-pid` は PID、`last-event` と `event-log` は操作ログです。

## MCP とスキルの対応

MCP サーバーには21個のツールがあります。同じ確認対象を CLI からも操作できます。

| MCP ツール | スキルの CLI コマンド | 確認方法 |
|---|---|---|
| `discoverApplications` | `discover` | mainClass が `io.github.k7t3.simplefx.Main`、PID が画面表示と一致する |
| `connectApplication`, `disconnectApplication` | CLI は各操作で自動接続 | 接続後に検査できる。切断ツールでエージェントを停止し、再接続して検査できる |
| `getStages`, `getWindowDetails` | `stages`, `window-details` | 補助 Stage や Popup / Tooltip を開き、種類・所有者・寸法を確認する |
| `setWindowProperty`, `closeWindow` | `set-window-property`, `close-window` | 補助 Stage の位置・寸法・タイトル・opacity・resizable・alwaysOnTop・maximized・iconified を変更し、詳細を再取得する。終了要求の許可・拒否・確認を試す |
| `closePopup` | `close-popup` | 表示中の Popup / Tooltip / メニューだけを閉じる |
| `getScenegraph`, `findNodes`, `getNodeDetails` | `scenegraph`, `find-nodes`, `node-details` | 深さ・bounds・プロパティ絞り込み・CSS ID・型・テキスト・style class・祖先・実効可視性を確認する |
| `setProperty` | `set-property` | 入力や確認用 Label の text・selected・value・opacity・textFill・style を変更して再取得する |
| `selectNode` | `select-node` | `property-target` をハイライトし、nodeId=0 で解除する |
| `clickNode` | `click-node`, `right-click-node`, `double-click-node` | primary / secondary / middle、clickCount=1 / 2 を送る。メニューのサブメニューや無効項目も試す |
| `activateNode` | `activate-node` | `gesture-target` の action だけが増え、マウスイベントが増えないことを確認する |
| `requestFocus`, `typeKey` | `focus`, `type-key` | `key-target` のキーと修飾フラグ、Tab / Shift+Tab の移動、Ctrl+K / Meta+K のカウントを確認する |
| `scrollNode`, `scrollToIndex` | `scroll-node`, `scroll-to-index` | 各仮想リストの index=80 / 99、pixel delta、端への移動、ScrollPane の横移動を確認する |
| `takeScreenshot`, `captureVideo` | `screenshot`, `capture-video` | 固定領域、Stage、Popup を個別に撮影する。sourceWidth / sourceHeight / scaled と動画のフレーム数を確認する |

## CLI での確認例

`jq` が必要です。CLI の JAR を準備した後、起動中アプリを発見します。

```bash
./gradlew :fxgraph-cli:installSkillJars
FXGRAPH_CLI="$PWD/skills/fxgraph/scripts/fxgraph"
"$FXGRAPH_CLI" discover

# 画面に表示された PID を設定する
FXGRAPH_PID=12345
"$FXGRAPH_CLI" "$FXGRAPH_PID" stages

# メインの Stage を一意に選ぶ。タイトルを変更した場合は実際のタイトルに合わせる
FXGRAPH_STAGE=$("$FXGRAPH_CLI" "$FXGRAPH_PID" stages | jq -er \
  '[.[] | select(.windowType == "Stage" and .title == "FXGraph 機能確認アプリ")] |
   if length == 1 then .[0].stageId else error("main Stage is not unique") end')

node_id() {
  "$FXGRAPH_CLI" "$FXGRAPH_PID" find-nodes --id "$1" --stageId "$FXGRAPH_STAGE" |
    jq -er 'if length == 1 then .[0].nodeId else error("node is not unique") end'
}
```

### 入力とイベント

```bash
"$FXGRAPH_CLI" "$FXGRAPH_PID" set-property "$(node_id input-text)" text "日本語の入力"
"$FXGRAPH_CLI" "$FXGRAPH_PID" activate-node "$(node_id input-submit)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id input-result)" --filter text
"$FXGRAPH_CLI" "$FXGRAPH_PID" set-property "$(node_id input-check)" selected true --type boolean
"$FXGRAPH_CLI" "$FXGRAPH_PID" set-property "$(node_id input-number)" value 65 --type number
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id input-values)" --filter text

"$FXGRAPH_CLI" "$FXGRAPH_PID" double-click-node "$(node_id gesture-target)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" click-node "$(node_id gesture-target)" --button middle
"$FXGRAPH_CLI" "$FXGRAPH_PID" activate-node "$(node_id gesture-target)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id mouse-status)" --filter text
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id action-status)" --filter text

"$FXGRAPH_CLI" "$FXGRAPH_PID" focus "$(node_id key-target)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" type-key K --modifiers CTRL
"$FXGRAPH_CLI" "$FXGRAPH_PID" type-key K --nodeId "$(node_id key-target)" --modifiers META
"$FXGRAPH_CLI" "$FXGRAPH_PID" type-key TAB --nodeId "$(node_id key-target)" --modifiers SHIFT
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id key-status)" --filter text
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id focus-status)" --filter text
```

`mouse-status` とログには MOUSE_PRESSED / RELEASED / CLICKED、button、clickCount、synthesized が出ます。
キーは KEY_PRESSED / TYPED / RELEASED、code、character、修飾フラグを記録します。
Control / Alt / Meta を付けると KEY_TYPED が送信されないこともログで確認できます。

### 検査・プロパティ・可視性

```bash
"$FXGRAPH_CLI" "$FXGRAPH_PID" click-node "$(node_id tab-inspection)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" scenegraph --stageId "$FXGRAPH_STAGE" --depth 4 --bounds
"$FXGRAPH_CLI" "$FXGRAPH_PID" find-nodes --styleClass inspection-target
"$FXGRAPH_CLI" "$FXGRAPH_PID" select-node "$(node_id property-target)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" set-property "$(node_id property-target)" textFill "#FFAA00" --type color
"$FXGRAPH_CLI" "$FXGRAPH_PID" set-property "$(node_id property-target)" style "-fx-background-color: #4c566a;"
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id property-target)" --filter text,textFill,opacity,scaleX,rotate
"$FXGRAPH_CLI" "$FXGRAPH_PID" select-node 0
"$FXGRAPH_CLI" "$FXGRAPH_PID" node-details "$(node_id subscene-target)" --filter text --ancestors
"$FXGRAPH_CLI" "$FXGRAPH_PID" find-nodes --id hidden-target
"$FXGRAPH_CLI" "$FXGRAPH_PID" find-nodes --id hidden-target --visible-only
```

`hidden-target` は自身の visible が true でも親により非表示です。visible-only では空になります。
`transparent-target` は親の opacity=0、`clipped-target` は完全なクリップ、`partial-target` は部分的なクリップです。
`disabled-target` へのクリックは拒否されます。SubScene 内のノードの ancestors は SubScene 境界を含みます。

### ウィンドウ・モーダル・メニュー

ウィンドウタブで補助 Stage を開き、`stages` でその ID を取得してから操作します。
`set-window-property` はメインを残せる補助 Stage で試すと、その後の結果確認を続けられます。

```bash
"$FXGRAPH_CLI" "$FXGRAPH_PID" click-node "$(node_id tab-windows)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" activate-node "$(node_id open-window)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" stages
# 取得した補助 Stage の ID に置き換える
FXGRAPH_AUX_STAGE=123456789
"$FXGRAPH_CLI" "$FXGRAPH_PID" set-window-property "$FXGRAPH_AUX_STAGE" width 700
"$FXGRAPH_CLI" "$FXGRAPH_PID" set-window-property "$FXGRAPH_AUX_STAGE" title "変更したタイトル"
"$FXGRAPH_CLI" "$FXGRAPH_PID" window-details "$FXGRAPH_AUX_STAGE"
"$FXGRAPH_CLI" "$FXGRAPH_PID" close-window "$FXGRAPH_AUX_STAGE"
```

- **終了拒否**: `open-guarded-window` → close-window の closed=false と `window-status` を確認。
  補助画面の `window-allow-close` を selected=true にすると終了を許可します。
- **終了確認**: `open-confirm-window` → close-window → 確認ダイアログで取消なら維持、確定なら終了。
- **モーダル**: `open-modal-dialog`、またはメニューの「モーダルを開く」→ handlerPending=true。
  新しい Stage の `dialog-confirm` / `dialog-cancel` を起動して閉じ、`dialog-status` の最終結果を確認。
- **非モーダル**: `open-dialog` は即座に戻り、メイン画面と補助ダイアログを並行して検査できます。
- **Popup / Tooltip**: `show-popup` / `show-tooltip` で明示的に表示し、stages の windowType と
  ownerWindowId を確認。取得した popup の stageId に close-popup を送って閉じます。
- **ModalPane**: `show-overlay` → `embedded-overlay` の display=true。
  `overlay-close` または Escape で閉じます。`set-property display false --type boolean` の確認もできます。
- **メニュー**: `tab-menus` に切り替え、context-target の right-click-node、menu-button の click-node、
  MenuBar の「確認メニュー」から開きます。「詳細メニュー」→「入れ子のアクション」などをクリック。
  MenuItem は Node ではないため、表示中 popup のテキスト検索から ancestors で MenuItemContainer を特定します。
  「実行不可」は node.disabled=true で、クリックしても menu-status を変更しません。

モーダルの応答後はダイアログを閉じ、結果を確認してから次の操作へ進めてください。
handlerPending=true は処理完了を意味しません。

### スクロール・画像・動画

```bash
"$FXGRAPH_CLI" "$FXGRAPH_PID" click-node "$(node_id tab-scroll)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" find-nodes --text "List item 080" --visible-only
"$FXGRAPH_CLI" "$FXGRAPH_PID" scroll-to-index "$(node_id scroll-list)" --index 80
"$FXGRAPH_CLI" "$FXGRAPH_PID" find-nodes --text "List item 080" --visible-only
"$FXGRAPH_CLI" "$FXGRAPH_PID" scroll-node "$(node_id scroll-table)" --align bottom
"$FXGRAPH_CLI" "$FXGRAPH_PID" scroll-node "$(node_id scroll-pane)" --dx 500 --dy 400
"$FXGRAPH_CLI" "$FXGRAPH_PID" scroll-to-index "$(node_id flowless-flow)" --index 99
"$FXGRAPH_CLI" "$FXGRAPH_PID" scroll-to-index "$(node_id flowless-wrapper)" --index 99

"$FXGRAPH_CLI" "$FXGRAPH_PID" click-node "$(node_id tab-capture)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" screenshot /tmp/fxgraph-original.png --nodeId "$(node_id capture-target)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" screenshot /tmp/fxgraph-small.png --nodeId "$(node_id capture-target)" --maxWidth 320 --maxHeight 150
"$FXGRAPH_CLI" "$FXGRAPH_PID" activate-node "$(node_id animation-start)"
"$FXGRAPH_CLI" "$FXGRAPH_PID" capture-video /tmp/fxgraph-motion.mp4 --nodeId "$(node_id capture-target)" --durationSeconds 3 --framesPerSecond 10
"$FXGRAPH_CLI" "$FXGRAPH_PID" activate-node "$(node_id animation-stop)"
```

各リストのデータは index=0〜99、テキストは `List item 080` / `Table row 080` /
`Flowless item 080` / `Wrapped item 080` の形式です。セルは再利用されるため移動後に再検索します。
JavaFX VirtualFlow を直接試す場合は ListView または TableView 内の `.virtual-flow` を検索し、
ancestors で目的のコンテナーを確認してから使ってください。

原寸画像は sourceWidth=640、sourceHeight=300、scaled=false、縮小画像は320 × 150で scaled=true。
3秒・10fps の動画は30フレームです。ウィンドウ全体や表示中の popup の撮影は、それぞれ stageId を指定します。

## 検証と制約

```bash
./gradlew :javafx-test-app:test
./gradlew build
```

アプリのテストは Monocle で起動した画面を実際の SceneGraphInspector から操作し、
イベントの到達、可視性、SubScene の祖先、ウィンドウ、モーダル、メニュー、
仮想スクロール、画像、リセットと既存 Todo の結果を検証します。
実デスクトップの Attach API、MCP の STDIO、動画とウィンドウマネージャーの挙動は
起動したアプリへ接続して確認してください。

入力は JavaFX の合成イベントです。OS のポインター移動、hover、IME の変換、
ネイティブな Cmd+Q / Cmd+W は再現しません。Ctrl+K / Meta+K はアプリ内の Scene accelerator です。
PNG・動画は指定した Node または1つの Scene の内容を撮影し、OS の装飾や別ウィンドウの popup を合成しません。

Java 21、JavaFX 22.0.1、AtlantaFX 2.0.1、Flowless 0.7.4 を使用します。
