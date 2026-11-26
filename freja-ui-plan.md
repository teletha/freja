# freja 二UI「Swing / Browser (Apocrypha)」実装計画

## 背景
- freja はローカル写真ライブラリ管理(Swing 版 `freja.Freja` に加え、Browser 版 `freja.web.FrejaWeb` を追加実装。JavaFX 版は廃止済み)
- apocrypha は Electron 系UIフレームワーク(Java バックエンド + avaje-webview)。HTML/CSS/JS をクラスパスの `web` から配信し、`freja:*` IPC チャネルでやり取りする
- 目標: apocrypha を使った二番目のUIを実装し、既存ライブラリ/スキャナ/モデルをそのまま共有する

## 実装内容
### freja (Browser 版)
- `src/main/java/freja/web/FrejaWeb.java` — Application 寿命。INSTANCE_PORT 45671 の単一インスタンス機構(ポート取得→リタイア→待機)、openLibrary でカタログをバックグラウンド開放、IPC ハンドラと `/photo/` ルートを onStart で登録、`freja:ready` emit、`onExit` で scanner/library を close。dataDirectory は `~/.freja-web`
- `WebHandlers.java` — `freja:browse`(mode/folderId/sort/search/tagIds → Sections+visibleFolders+Stats)、`freja:photo/stats/state/folders/about`、写真アクション(reimport/removePhotos/deleteFromDisk/reveal/revealFolder/copyPaths/favorite/rating/caption)、フォルダ(add/rename/remove/rescan/scanEnabled/clear/sort/pickFolder)、タグ(list/allLinks/add/rename/delete/bulkRename/setLinks/setFolderTags)、scan.all、settings
- `WebModels.java` — Bootstrap/BrowseRequest/BrowseResult/Section/ScannerReport/SettingsMap/PhotoIds/FavoriteRequest/FolderIds/TagIds/BulkRename/SetLinks/SetFolderTags
- `PhotoRoutes.java` — `/photo/{id}/thumb`(image/webp + キャッシュ)と `/photo/{id}/original`(ブラウザ非対応形式は Imagify で JPEG トランスコード)
- `WebSettings.java` — settings.json を Map<String,String> として永続化(synchronized + sinobu I.json/I.write)
- `src/main/resources/web/` — `index.html` / `app.js` / `style.css`(ブラウザ UI。menubar/activitybar/sidebar/toolbar/grid/inspector/viewer overlay/statusbar/floating、JA 辞書、i18n、popover、ダイアログ)

### apocrypha (フレームワーク拡張)
- `Application.serve(String prefix, Route route)` — 動的 HTTP ルート登録 API
- `Route` — `(path, query) -> Response`(null で静的フォールバック)
- `Response` — ok/notFound/cache/header ファクトリ付きレコード
- README / describe() に Dynamic Content 節を追加

## 検証
- [x] `bee.bat compile`(freja 本体 + web 5 ファイル)→ SUCCESS
- [x] `bee.bat test` → 全34テスト成功
- [x] `node --check app.js` → 構文 OK
- [x] 起動確認: `freja.web.FrejaWeb` → サーバ 127.0.0.1:65172、"Browser edition started"、致命的エラーなし
- README に Browser 版の起動方法・依存(apocrypha/avaje-webview)を記載

## Svelte の段階導入
- `src/main/frontend/` — Svelte 5 のソース（`Settings.svelte` / `main.js` / `package.json`）。bun4j の `Bun.bundle()` + `Framework.SVELTE` でビルド
- `freja.web.SvelteUI` — 起動時にソースをバンドルして `/svelte/` から配信。ソースツリーが無い環境では同梱済みの `web/svelte/` にフォールバック
- `app.js` は `window.frejaSvelteBridge` で状態とアクションを公開。設定ページの静的 DOM とイベントバインディングを削除し、Svelte コンポーネントへ移管
- 検証: `bee.bat test` 全34テスト成功 / happy-dom 上でマウント・テーマ切替・日本語ラベルを確認

## 依存と生成物
- freja → sinobu, psychopath, imagify, apocrypha, flatlaf, flatlaf-intellij-themes, ikonli, javafx (Browser 版の WebView), jna, miglayout, sqlite-jdbc, swing-tree, controlsfx, lycoris, conjure, altfx, BorderlessSceneFX, jsvg, sprouts, bee-api, antibug
- 実行: `java --enable-native-access=ALL-UNNAMED -cp <libs>;target\classes freja.web.FrejaWeb --library=<path> [folders...]`
