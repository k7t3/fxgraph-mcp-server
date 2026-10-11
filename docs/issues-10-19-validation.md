# Issues #10〜#19 の対応・検証記録

検証日: 2026-10-09。対象: [fxgraph-mcp-server の Issues](https://github.com/k7t3/fxgraph-mcp-server/issues)。
変更はローカル作業ツリーにあります。この作業ではコミット、プッシュ、PR 作成、Issue の更新・クローズを行っていません。
開始時の既存コミット `09e2ead` と未コミットの Robot 廃止作業を引き継いでいます。

## 対応内容と根拠

| Issue | 対応内容 | 検証 |
|---|---|---|
| [#10](https://github.com/k7t3/fxgraph-mcp-server/issues/10) 修飾キー | SHIFT / CTRL / ALT / CMD(META) の JavaFX 合成キー入力を維持。旧 Robot 指定は入力前に拒否。OS の閉じるショートカットの代替として `close-window` を追加 | `TypeKeyGestureTest`、CLI/MCP 転送テスト。実 macOS で Unicode 入力、Ctrl+K / Cmd+K の Scene accelerator、Shift+Tab の Scene 内フォーカス移動を確認 |
| [#11](https://github.com/k7t3/fxgraph-mcp-server/issues/11) ウィンドウ操作 | `window-details`、`set-window-property`、`close-window` を CLI / MCP / Agent に追加。閉じる操作は `WINDOW_CLOSE_REQUEST` を配送し、consume によるキャンセルを尊重 | `WindowOperationsTest`。実機で座標・サイズ・title・alwaysOnTop・opacity を変更して再読取。閉じる要求が 2 回、hiding が 1 回となるキャンセル／通常終了を確認 |
| [#12](https://github.com/k7t3/fxgraph-mcp-server/issues/12) スクロール | `scroll-node` の画素差分・端指定、`scroll-to-index` の行番号指定を追加。ListView / TableView / ScrollPane / JavaFX VirtualFlow / Flowless VirtualFlow とその VirtualizedScrollPane をサポート | `ScrollOperationsTest`。実機でリスト、テーブル、Flowless の未表示行 80 の実体化、最終行、画素移動と ScrollPane 両軸移動を確認。Flowless 0.7.4 はテスト依存のみ |
| [#13](https://github.com/k7t3/fxgraph-mcp-server/issues/13) 祖先情報 | `parentId` と `node-details --ancestors`、MCP の `includeAncestors` を追加。近い親からシーンルートまで返し、SubScene の包含境界も扱う | `NodeInspectionTest` の SubScene 経路テスト。実機で parentId と最初の ancestor が一致し、最後がルートとなることを確認 |
| [#14](https://github.com/k7t3/fxgraph-mcp-server/issues/14) メニュー／モーダル | 標準サブメニューへマウス enter を配送。`showAndWait()` によるネストしたイベントループでは `handlerPending=true` を返し、後続の検査・閉じる操作を可能にした。例外理由も明示 | `ClickNodeGestureTest`。実 JavaFX 21 / 22.0.1 でサブメニューからダイアログを開き、応答が 10 秒未満で返ること、ダイアログ終了後に元のハンドラが再開することを確認 |
| [#15](https://github.com/k7t3/fxgraph-mcp-server/issues/15) 実効可視状態 | `inScene` / `effectiveVisible` / `clipped` / 不可視時の `visibilityReason` と `find-nodes --visible-only` を追加。操作の拒否理由に祖先・透明・クリップ・ゼロサイズ等を含める | `NodeInspectionTest`。実機で非表示祖先と折りたたみコンテナの子を区別し、visible-only から除外し、操作エラーに理由が含まれることを確認 |
| [#16](https://github.com/k7t3/fxgraph-mcp-server/issues/16) モーダル／オーバーレイ | ウィンドウには close-window、PopupWindow には close-popup を追加。シーン内の AtlantaFX ModalPane は `display=false` の回避策を文書化 | 実 AtlantaFX 2.0.1 の ModalPane を表示し、set-property で display が true→false に変わることを確認。ContextMenu の終了で所有 Stage が残ることも確認 |
| [#17](https://github.com/k7t3/fxgraph-mcp-server/issues/17) 空の引数 | フラグの空値・未指定値・直後の別フラグを値として消費せず、当該フラグの非空値エラーと Usage を返す | CLI テストと実 JAR で `--stageId "" --depth 2` / `--stageId --depth 2` を確認。set-property の位置引数 `text ""` は許可 |
| [#18](https://github.com/k7t3/fxgraph-mcp-server/issues/18) 原寸画像 | screenshot の既定を原寸に変更。0 / --no-limit、縮小時の sourceWidth / sourceHeight / scaled、確保前の安全上限を追加 | `ScreenshotOptionsTest` と既存 Inspector テスト。実機で原寸 1400×850、指定縮小 640×388、PNG ヘッダーとメタデータの一致を確認。8192px 境界に小数座標の切り上げを加える回帰テストも実施 |
| [#19](https://github.com/k7t3/fxgraph-mcp-server/issues/19) リファレンス | Node クラス検索、非 Node の controller / MenuItem、旧 Robot の廃止、ポップアップと仮想セルの ID 再取得、閉じる回避策、処理中応答を追記。CLI の JSON 例を実際の出力に合わせた | 実 JAR で MenuItem の型検索が空となることと、表示後の popup ID を検索して操作できることを確認。Skill 内の相対リンクを検査 |

クリックの `MouseEvent.isSynthesized` はタッチ由来を示すため `false` に修正しました。
タッチ由来の入力を除外するハンドラに対する単一クリック・ダブルクリックは、回帰テストと実機の両方で受理されました。

## 自動検証

```bash
./gradlew build :fxgraph-cli:installSkillJars
```

最終結果: `BUILD SUCCESSFUL`。JUnit XML から集計し、失敗・エラー・スキップはすべて 0 です。

| モジュール | テスト数 |
|---|---:|
| fxgraph-core | 12 |
| fxgraph-agent | 163 |
| fxgraph-cli | 84 |
| fxgraph-mcp-server | 64 |
| 合計 | 323 |

Agent の自動テストは Monocle を使います。javafx-test-app はテストソースがなく `NO-SOURCE` であり、
上記のテスト数には含めていません。MCP の STDIO 統合テストで、21 ツールの登録と新オプションのスキーマを確認しました。
追加 API の CLI/MCP 転送と一時接続の後処理もテストしています。

## 実 macOS ウィンドウの検証

macOS 26.6.2 arm64 / Temurin 21 で、JavaFX 21 と 22.0.1 を個別に起動しました。
Monocle を使わず、Skill に配置した CLI から Attach API で Agent を注入しています。
各実行は 117 回の CLI 呼び出しと 41 項目の状態検証を実施し、両バージョンとも成功しました。
検証専用 JVM は終了済みです。

生成した以下のローカル証跡は `build/` 配下のため Git 管理対象外です。

- [全体ビルドのログ](../build/issue-validation/build.log)
- [JUnit 集計・JAR 検査](../build/issue-validation/build-artifacts.json)
- [JavaFX 21: CLI 引数・応答・時間・検証項目](../build/issue-validation/native-21.json)
- [JavaFX 22.0.1: CLI 引数・応答・時間・検証項目](../build/issue-validation/native-22-0-1.json)
- [原寸画像](../build/issue-validation/native-22-0-1-original.png) / [縮小画像](../build/issue-validation/native-22-0-1-scaled.png)
- [実機検証用ソース](../build/issue-validation/NativeIssueProbe.java) / [再実行スクリプト](../build/issue-validation/verify_native.py)

再実行は、上記ビルド後に `python3 build/issue-validation/verify_native.py 21 22.0.1`。
スクリプトはこのホストの Gradle キャッシュにある JavaFX / AtlantaFX / Flowless / ReactFX JAR を使います。
両実機記録には実行時の CLI / Agent の SHA-256 も保存しています。

## 成果物

ビルドした JAR と `skills/fxgraph/scripts/` のコピーは SHA-256 が一致しました。

| JAR | SHA-256 |
|---|---|
| fxgraph-cli.jar | `d4ef291de4d590ea4ecb13e44b04ceaab419db69d3957522aea6d97afea7bccf` |
| fxgraph-agent.jar | `97dbdfbadc381f76b19c0ec41c0ccdc55054cda78605b06c6cbc1ec4c33f4cd7` |

Agent JAR の全 class を検査し、JavaFX Robot への参照および同梱 Flowless class はありません。
core と agent の重複プロトコル enum は一致しています。`git diff --check` も成功しました。

## 制約と操作結果の扱い

- 合成入力は JavaFX 内のイベント配送です。OS 固有の Cmd+Q / Cmd+W、IME 変換、実ポインター・hover、ネイティブのヒットテストは再現しません。ウィンドウの閉じる要求には close-window を使用します。
- `handlerPending=true` は配送済みでハンドラが処理中という意味です。完了や保存結果を保証せず、ダイアログ操作後のアプリ状態を再確認する必要があります。JVM が終了した場合は応答前に接続が閉じることがあります。
- 実効可視状態は矩形の境界・祖先状態を使う推定です。兄弟や別ウィンドウの遮蔽、非矩形クリップの穴は判定しません。検索範囲は表示中のウィンドウのシーングラフです。
- ModalPane の display 変更はコンポーネントのプロパティ操作です。任意の独自オーバーレイやアプリ側の終了コールバックを一律に再現するものではありません。
- screenshot の安全上限は一辺 8192px、総画素数 16,777,216 です。縮小指定や --no-limit にも適用します。動画の既定上限は 1280×720 のままです。
- 実ウィンドウ検証は macOS / JavaFX 21・22.0.1 / Flowless 0.7.4 / AtlantaFX 2.0.1 での確認です。Windows・Linux の実ウィンドウは未検証です。

仕様確認には [JavaFX MouseEvent](https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/input/MouseEvent.html)、
[JavaFX Platform](https://openjfx.io/javadoc/21/javafx.graphics/javafx/application/Platform.html)、
[WindowEvent](https://openjfx.io/javadoc/21/javafx.graphics/javafx/stage/WindowEvent.html)、
[OpenJFX Scene の snapshot 実装](https://github.com/openjdk/jfx/blob/jfx21/modules/javafx.graphics/src/main/java/javafx/scene/Scene.java)、
[Flowless の公開実装](https://github.com/FXMisc/Flowless)、
[AtlantaFX ModalPane 2.0.1](https://github.com/mkpaz/atlantafx/blob/v2.0.1/base/src/main/java/atlantafx/base/controls/ModalPane.java) を参照しました。
