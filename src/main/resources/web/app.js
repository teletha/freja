/*
 * Copyright (C) 2026 The FREJA Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
/*
 * Freja — browser edition (Apocrypha).
 *
 * The renderer mirrors the Swing and JavaFX editions: the backend owns the catalog and the
 * scanner, this file owns the DOM. All data crosses the boundary through the `freja:*` IPC
 * channels; thumbnails and originals are plain <img> loads served by the /photo/ route, which
 * gives the browser native lazy loading and caching.
 */
(function () {
    'use strict';

    /* ============================================================== i18n ==== */

    // Interface strings are written in English and resolved through this dictionary, the same
    // scheme the ARX widgets use in the Swing edition. Only Japanese is bundled offline; other
    // languages fall back to English on the client while the backend translates the date labels.
    const JA = {
        'Library': 'ライブラリ',
        'Folders': 'フォルダ',
        'All photos': 'すべての写真',
        'Favorites': 'お気に入り',
        'Recent photos': '最近の写真',
        'No folders are registered': 'フォルダが登録されていません',
        'Manage tags': 'タグを管理',
        'Manage watched folders': '監視フォルダを管理',
        'Add folder': 'フォルダを追加',
        'Rescan': '再スキャン',
        'Rescan all': 'すべてのフォルダを再スキャン (F5)',
        'Search': '検索',
        'Slideshow': 'スライドショー',
        'Tags': 'タグ',
        'Filter by tags': 'タグで絞り込み',
        'Search tags': 'タグを検索',
        'Clear selection': '選択を解除',
        'No tags match': '該当するタグがありません',
        'folders': 'フォルダ',
        'By name': '名前順',
        'Newest first': '日時順 (新しい順)',
        'Oldest first': '日時順 (古い順)',
        'By size': 'サイズ順',
        'Folder display order': 'このフォルダの表示順',
        'Display order': '並び順',
        'Thumbnail size': 'サムネイルサイズ',
        'Open': '開く',
        'Favorite': 'お気に入り',
        'Favorited': 'お気に入り済み',
        'Add to favorites': 'お気に入りに追加',
        'Remove from favorites': 'お気に入りから外す',
        'Rating': '評価',
        '{0} stars': '{0} つの星',
        'Caption': 'キャプション',
        'Save': '保存',
        'Open in Explorer': 'エクスプローラーで表示',
        'Capture': '撮影日',
        'Dimensions': 'サイズ',
        'Size': 'ファイルサイズ',
        'Camera': 'カメラ',
        'Lens': 'レンズ',
        'Exposure': '露出',
        'Select a photo': '写真を選択してください',
        'No photos match the current filter': '条件に一致する写真がありません',
        'Show {0} more': '他に {0} 枚を表示',
        '{0} photos': '{0} 枚',
        '(auto-detected)': '（自動検出）',
        'Rename': '名前を変更',
        'Edit tags…': 'タグを編集…',
        'Copy paths': 'パスをコピー',
        'Reload': '再読み込み',
        'Remove from library': 'ライブラリから削除',
        'Delete from disk': 'ディスクから削除',
        '({0} photos)': ' ({0}枚)',
        'File': 'ファイル',
        'View': '表示',
        'Help': 'ヘルプ',
        'Watched folders…': '監視フォルダ…',
        'Settings…': '設定…',
        'Quit': '終了',
        'Go to search': '検索に移動',
        'Manage tags…': 'タグを管理…',
        'Inspector': 'インスペクター',
        'Dark mode': 'ダークモード',
        'About Freja': 'Freja について',
        'Ready': '準備完了',
        'Scanning': 'スキャン中',
        '{0} photos updated ({1})': '{0} 枚の写真を更新しました ({1})',
        'Scan complete ({0})': 'スキャン完了 ({0})',
        '{0} files could not be read': '{0} 件のファイルを読み込めませんでした',
        'Rescan started': '再スキャンを開始しました',
        'Rescanning the folder…': 'フォルダを再スキャンしています…',
        'Reloading {0} photos…': '{0} 枚を再読み込みしています…',
        '{0} paths copied': '{0} 枚のパスをコピーしました',
        'Path copied': 'パスをコピーしました',
        'Deleted {0} photos from disk': '{0} 枚をディスクから削除しました',
        'Deleted {0}, failed {1}': '{0} 枚を削除し、{1} 枚は削除できませんでした',
        'Folder renamed: {0}': 'フォルダ名を変更しました: {0}',
        'Enter a folder name': 'フォルダ名を入力してください',
        'Folders removed': 'フォルダを削除しました',
        'Watched folders cleared': '監視フォルダをクリアしました',
        'No photos for a slideshow': 'スライドショーにする写真がありません',
        'Loading…': '読み込み中…',
        'Cannot load the image': '画像を読み込めません',
        'Cannot read the library': 'ライブラリを読み込めません',
        'Language changed': '言語を変更しました',
        'Settings': '設定',
        'Appearance': '外観',
        'Theme, colors and text live here.': 'テーマと色使いを調整します。',
        'Theme': 'テーマ',
        'Dark': 'ダーク',
        'Light': 'ライト',
        'Scanning and watched folders.': 'スキャンと監視フォルダの設定です。',
        'Scan on startup': '起動時にスキャン',
        'Periodic sweep': '定期見回りスキャン',
        'Manage watched folders…': '監視フォルダを管理…',
        'Grid': 'グリッド',
        'Thumbnail size for the photo wall.': '写真ウォールのサムネイルサイズです。',
        'Language': '言語',
        'The interface follows the language here; date labels update immediately.': '表示言語を選ぶと、翻訳済みラベルがその場で更新されます。',
        'Display language': '表示言語',
        'System': 'システム',
        'Application information and restart.': 'アプリケーション情報の確認と再起動です。',
        'Restart': '再起動',
        'Library: {0}': 'ライブラリ: {0}',
        'Watched folder management': '監視フォルダの管理',
        'Photos': '写真',
        'Path': 'パス',
        'Scan': 'スキャン',
        'Mute / unmute': 'スキャン停止/再開',
        'Remove': '削除',
        'Clear all': 'すべて削除',
        'Close': '閉じる',
        'Scan folders when Freja starts': 'Freja 起動時にフォルダをスキャンする',
        'Sweep for missed changes every 5 minutes': '5 分ごとに变更を見回してスキャンします',
        'Photo files are never moved, renamed or deleted here; only the catalog changes.': '写真ファイルは移動・名前変更・削除されません。カタログのみが更新されます。',
        'Remove {0} folders and their catalog data? The photo files stay on disk.': 'フォルダ {0} 件とカタログデータを削除します。写真ファイルはディスクに残ります。',
        'Remove every folder and its catalog data? The photo files stay on disk.': 'すべてのフォルダとカタログデータを削除します。写真ファイルはディスクに残ります。',
        'Manage folders': '監視フォルダ',
        'Tags live on folders; every photo inherits the tags of its folder.': 'タグはフォルダに付けます。写真はフォルダのタグをそのまま受け継ぎます。',
        'Manage tag names and links': 'タグの名前とリンクを管理',
        'New tag name': '新しいタグ名',
        'Add': '追加',
        'Delete': '削除',
        'Replace…': '一括置換…',
        'Links…': 'リンク…',
        'Tag': 'タグ',
        '{0} tags': 'タグ {0} 件',
        '{0} selected': '{0} 件選択',
        'Rename tag': 'タグの名前を変更',
        'Delete tags': 'タグを削除',
        'Remove {0} tags from every folder? The photos are not deleted.': 'タグ {0} 件をすべてのフォルダから削除します。写真自体は削除されません。',
        'Find which tag name': '検索するタグ名',
        'Replace with': '置換後の文字列',
        '{0} tags renamed': '{0} 件のタグ名を変更しました',
        'Edit tag links': 'タグのリンクを編集',
        'Linked tags are found when you search for this tag.': 'リンクしたタグは、このタグで検索したときに見つかります。',
        'No other tags yet': '他のタグがまだありません',
        'Select labels': 'ラベルを選択',
        'Search or create labels…': '検索または新しいラベルを作成…',
        'Create new label': '新しいラベルを作成',
        'No tags yet': 'タグがまだありません',
        'Delete tag': 'タグを削除',
        'Delete tag {0} from every folder? The photos are not deleted.': 'タグ『{0』をすべてのフォルダから削除します。写真自体は削除されません。',
        'Delete this tag': 'このタグを削除',
        'Edit tags for {0}': '「{0}」のタグを編集',
        'Cancel': 'キャンセル',
        'OK': 'OK',
        'Clear': 'クリア',
        'Folder display name': 'フォルダの表示名',
        'Remove photos from library': '写真を削除',
        '{0} photos will be removed from the library.': '{0} 枚の写真をライブラリから削除します。',
        '{0} will be removed from the library.': '「{0}」をライブラリから削除します。',
        'The photo files themselves are not deleted.': '写真ファイル自体は削除されません。',
        'Delete from disk permanently': 'ディスクから削除',
        '{0} image files will be permanently deleted from disk.': '{0} 枚の画像ファイルをディスクから完全に削除します。',
        '{0} will be permanently deleted from disk.': '「{0}」をディスクから完全に削除します。',
        'This cannot be undone. The catalog entry is removed too.': '\n\nこの操作は取り消せません。ライブラリからも削除されます。',
        'Remove folder': 'フォルダを削除',
        '"{0}" and its subfolders will be removed from the library.': '「{0}」と配下のサブフォルダをライブラリから削除します。',
        'Connecting to the library…': 'ライブラリを開いています…',
        'Freja cannot start': 'Freja を起動できません',
        'Photos: {0}': '写真: {0} 枚',
        'Favorites: {0}': 'お気に入り: {0} 枚',
        'Recent: {0}': '最近の写真: {0} 枚',
        'Folders: {0}': 'フォルダ: {0}',
        'Java {0}': 'Java {0}',
        'Browser edition (Apocrypha {0})': 'Apocrypha / browser 版 ({0})',
        'A fast, local-first photo library for the desktop.': 'Picasa ライクなローカル写真ライブラリ',
        'Put photos together, find them fast.': '写真をまとめて、すぐに見つける',
        'Register folders and Freja builds thumbnails and metadata automatically,': 'フォルダを登録すると、サムネイルとメタデータを自動で作成し、',
        'then follows additions, changes and deletions in the background.': 'その後の追加・変更・削除もバックグラウンドで追跡します。',
        'Photos are never moved or changed. Metadata lives in a local SQLite file.': '写真は移動・変更されません。メタデータはこのコンピューターの SQLite に保存されます。',
        'Move to {0}': '「{0}」へ移動',
        'Next / Previous': '次へ / 前へ',
        'Select a directory': 'フォルダを選択',
        'Choose folder': 'フォルダを選ぶ',
        'Pick a folder and Freja will watch it.': 'フォルダを選ぶと、監視とスキャンを開始します。',
        'Choose a folder…': 'フォルダを選択…',
        'Register': '登録',
        'Previous': '前へ',
        '{0} / {1}   ·   {2}   ·   {3}': '{0} / {1}   ·   {2}   ·   {3}',
        '← → or scroll to move   ·   drag to pan   ·   +/- to zoom   ·   0 or F to fit   ·   Space for slideshow   ·   Esc to close': '← → またはスクロールで移動   ·   ドラッグで移動   ·   + / - でズーム   ·   0 または F でフィット   ·   Space でスライドショー   ·   Esc で閉じる',
        'Fit': 'フィット',
        'Stop the slideshow': 'スライドショーを停止',
        'Selected folder will open after scanning.': '選択したフォルダはスキャン後に表示されます。',
        'Added {0}': '「{0}」を登録しました'
    };

    const LANG_NAMES = { en: 'English', ja: '日本語', ko: '한국어', 'zh-CN': '简体中文', 'zh-TW': '繁體中文', de: 'Deutsch', fr: 'Français', es: 'Español', it: 'Italiano', pt: 'Português', ru: 'Русский', nl: 'Nederlands' };

    function t(key, args) {
        var text = LANG === 'ja' && JA[key] ? JA[key] : key;
        if (args) {
            for (var i = 0; i < args.length; i++) {
                text = text.split('{' + i + '}').join(String(args[i]));
            }
        }
        return text;
    }

    var LANG = 'ja';

    function locale() {
        return LANG === 'en' ? 'en-US' : LANG;
    }

    /* =========================================================== state ==== */

    /** Photos per IPC page: metadata for the rest of the catalog is fetched as the user scrolls. */
    const PAGE_SIZE = 300;

    const S = {
        ready: false,
        page: 'viewer',
        mode: 'ALL',
        folderId: null,
        previousFolderId: null,
        sort: 'NAME',
        search: '',
        tagIds: [],
        thumb: 170,
        theme: 'dark',
        inspector: true,
        settings: {},
        stats: null,
        folders: [],
        sections: [],
        photos: [],
        photoSections: [],
        loadedThrough: 0,
        total: 0,
        hasMore: false,
        loadingMore: false,
        generation: 0,
        selected: new Map(),
        anchor: null,
        scrollFolder: null,
        scrollPending: false,
        tags: [],
        links: [],
        library: '',
        dataDirectory: '',
        startupFolders: [],
        bust: new Map()
    };

    /* ============================================================ utils ==== */

    const $ = function (id) { return document.getElementById(id); };

    function el(tag, className, textContent) {
        var node = document.createElement(tag);
        if (className) { node.className = className; }
        if (textContent !== undefined && textContent !== null) { node.textContent = textContent; }
        return node;
    }

    function clear(node) { while (node.firstChild) { node.removeChild(node.firstChild); } }

    function debounce(fn, ms) {
        var timer = 0;
        return function () {
            var args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () { fn.apply(null, args); }, ms);
        };
    }

    var SORTS = ['NAME', 'DATE_NEWEST', 'DATE_OLDEST', 'SIZE'];
    var SORT_KEYS = { NAME: 'By name', DATE_NEWEST: 'Newest first', DATE_OLDEST: 'Oldest first', SIZE: 'By size' };

    function fmtSize(bytes) {
        if (bytes < 1024) { return bytes + ' B'; }
        if (bytes < 1048576) { return Math.round(bytes / 1024) + ' KB'; }
        if (bytes < 1073741824) { return (bytes / 1048576).toFixed(1) + ' MB'; }
        return (bytes / 1073741824).toFixed(2) + ' GB';
    }

    function displaySize(p) {
        var flip = p.orientation >= 5 && p.orientation <= 8;
        return [flip ? p.height : p.width, flip ? p.width : p.height];
    }

    function fmtDate(ms) {
        var d = new Date(ms);
        if (LANG === 'ja') { return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日'; }
        return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium' }).format(d);
    }

    function fmtDateTime(ms) {
        var d = new Date(ms);
        if (LANG === 'ja') {
            return fmtDate(ms) + ' ' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
        }
        return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(d);
    }

    function fmtDuration(millis) {
        if (millis < 1000) { return millis + 'ms'; }
        var seconds = Math.floor(millis / 1000);
        if (seconds < 60) { return (Math.floor(millis / 100) / 10).toFixed(1).replace(/\.0$/, '') + (LANG === 'ja' ? '秒' : 's'); }
        var minutes = Math.floor(seconds / 60);
        if (minutes < 60) { return minutes + (LANG === 'ja' ? '分' + (seconds % 60) + '秒' : 'm ' + (seconds % 60) + 's'); }
        return Math.floor(minutes / 60) + (LANG === 'ja' ? '時間' + (minutes % 60) + '分' : 'h ' + (minutes % 60) + 'm');
    }

    // Tag colors mirror the Swing / JavaFX palette, which is picked by java.lang.String.hashCode.
    var TAG_PALETTE = [0x5473B5, 0x528A67, 0xB88947, 0xB85F52, 0x7A66A3, 0x438391];

    function javaHash(text) {
        var h = 0;
        for (var i = 0; i < text.length; i++) {
            h = (Math.imul(31, h) + text.charCodeAt(i)) | 0;
        }
        return h;
    }

    function tagColor(name) {
        var index = ((javaHash(name || '') % TAG_PALETTE.length) + TAG_PALETTE.length) % TAG_PALETTE.length;
        return '#' + TAG_PALETTE[index].toString(16).padStart(6, '0');
    }

    /* ============================================================== api ==== */

    function call(channel) {
        var args = Array.prototype.slice.call(arguments, 1);
        return apocrypha.invoke.apply(null, [channel].concat(args)).catch(function (error) {
            console.error('[freja] ' + channel, error);
            setStatus(t('Cannot read the library') + ': ' + error.message);
            throw error;
        });
    }

    function saveSettings() {
        call('freja:settings', { values: S.settings }).catch(function () { /* status reported */ });
    }

    function setSetting(key, value) {
        S.settings[key] = String(value);
        saveSettingsSoon();
    }

    var saveSettingsSoon = debounce(saveSettings, 250);

    /* ========================================================= bootstrap ==== */

    apocrypha.on('freja:ready', function (bootstrap) {
        S.ready = true;
        S.settings = bootstrap.settings || {};
        S.library = bootstrap.libraryPath;
        S.dataDirectory = bootstrap.dataDirectory;
        S.startupFolders = bootstrap.startupFolders || [];
        S.tags = bootstrap.tags || [];
        S.folders = bootstrap.folders || [];
        S.stats = bootstrap.stats;
        LANG = pickLanguage(S.settings.language, bootstrap.language);
        S.theme = S.settings.theme || 'dark';
        S.thumb = parseInt(S.settings.thumbSize, 10) || 170;
        S.sort = S.settings.sort || 'NAME';
        S.inspector = S.settings.inspectorVisible !== 'false';
        applyInspector();
        S.mode = 'ALL';
        S.folderId = null;
        S.previousFolderId = null;
        var selectedPath = S.settings.selectedFolder;
        if (selectedPath) {
            for (var i = 0; i < S.folders.length; i++) {
                if (norm(S.folders[i].path) === norm(selectedPath)) {
                    S.mode = 'FOLDER';
                    S.folderId = S.folders[i].id;
                    S.scrollFolder = S.folderId;
                    break;
                }
            }
        }
        applyChrome();
        syncSortSelect();
        $('welcome-library').textContent = t('Library: {0}', [S.library]);
        refreshTags().then(function () { browse(); });
        applyState(bootstrap.scanner);
        if (S.startupFolders.length > 0) {
            setStatus(t('Added {0}', [shortName(S.startupFolders[S.startupFolders.length - 1])]));
        }
    });

    apocrypha.on('freja:state', function (report) { applyState(report); });

    var debouncedRefresh = debounce(function () { browse(); }, 320);

    apocrypha.on('freja:changed', function () {
        refreshTags();
        debouncedRefresh();
    });

    apocrypha.on('freja:fatal', function (info) {
        modal({
            title: t('Freja cannot start'),
            node: textNode((info.message || '') + '\n\n' + t('Library: {0}', [info.library || ''])),
            buttons: [{ label: t('Quit'), value: 'quit', danger: true }]
        }).then(function (choice) { apocrypha.app.quit(); });
    });

    function pickLanguage(stored, fallback) {
        var code = stored || fallback || 'ja';
        return code.split('-')[0].toLowerCase();
    }

    function norm(path) {
        return String(path || '').replace(/[\\/]+/g, '\\').replace(/\\+$/, '').toLowerCase();
    }

    function shortName(path) {
        var parts = String(path).split(/[\\/]/);
        return parts[parts.length - 1] || path;
    }

    function textNode(text) {
        var node = el('div', 'modal-text', text);
        return node;
    }

    /* ===================================================== chrome / menu ==== */

    function applyChrome() {
        document.documentElement.dataset.theme = S.theme;
        $('tb-theme').textContent = S.theme === 'dark' ? '☀' : '☾';
        document.documentElement.lang = LANG.indexOf('ja') === 0 ? 'ja' : LANG;
        applySplitters();
        translateStatic();
        renderSortOptions();
        renderLibraryRows();
        renderFolders();
        syncSettingsPage();
    }

    var STATIC = {
        'heading-library': 'Library', 'heading-folders': 'Folders', 'folder-empty': 'No folders are registered',
        'manage-tags': 'Manage tags', 'add-folder': 'Manage watched folders', 'tb-add-label': 'Add folder',
        'tb-rescan-label': 'Rescan', 'tb-tags-label': 'Tags', 'tb-slideshow-label': 'Slideshow', 'search': 'Search',
        'menu-file': 'File', 'menu-view': 'View', 'menu-help': 'Help', 'insp-name': 'Select a photo',
        'insp-rating-label': 'Rating', 'lbl-capture': 'Capture', 'lbl-dimensions': 'Dimensions', 'lbl-size': 'Size',
        'lbl-camera': 'Camera', 'lbl-lens': 'Lens', 'lbl-exposure': 'Exposure', 'lbl-caption': 'Caption',
        'insp-save-caption': 'Save', 'insp-reveal': 'Open in Explorer', 'insp-open': 'Open', 'insp-favorite': 'Favorite',
        'settings-title': 'Settings', 'settings-appearance': 'Appearance', 'settings-appearance-note': 'Theme, colors and text live here.',
        'lbl-theme': 'Theme', 'settings-library': 'Library', 'settings-library-note': 'Scanning and watched folders.',
        'lbl-scan-start': 'Scan on startup', 'lbl-sweep': 'Periodic sweep', 'lbl-watch': 'Watched folders',
        'set-manage-folders': 'Manage watched folders…', 'settings-grid': 'Grid', 'settings-grid-note': 'Thumbnail size for the photo wall.',
        'lbl-thumb': 'Thumbnail size', 'settings-language': 'Language',
        'settings-language-note': 'The interface follows the language here; date labels update immediately.',
        'lbl-language': 'Display language', 'settings-system': 'System', 'settings-system-note': 'Application information and restart.',
        'set-about': 'About Freja', 'set-restart': 'Restart', 'welcome-title': 'Put photos together, find them fast.',
        'welcome-line1': 'Register folders and Freja builds thumbnails and metadata automatically,',
        'welcome-line2': 'then follows additions, changes and deletions in the background.',
        'welcome-add': 'Add folder', 'welcome-note': 'Photos are never moved or changed. Metadata lives in a local SQLite file.',
        'tb-add': 'Add folder', 'status-message': 'Ready'
    };

    function translateStatic() {
        for (var id in STATIC) {
            var node = $(id);
            if (node && node.tagName !== 'INPUT') {
                if (node.tagName === 'OPTION') { node.textContent = t(STATIC[id]); } else { node.textContent = t(STATIC[id]); }
            }
        }
        $('search').placeholder = t('Search');
        $('tb-rescan').title = t('Rescan all');
        $('tb-tags').title = t('Filter by tags');
        $('tb-slideshow').title = t('Slideshow') + ' (F11)';
        $('zoom').title = t('Thumbnail size');
        $('sort').title = S.mode === 'FOLDER' ? t('Folder display order') : t('Display order');
    }

    function renderSortOptions() {
        var select = $('sort');
        clear(select);
        SORTS.forEach(function (code) {
            var option = el('option', null, t(SORT_KEYS[code]));
            option.value = code;
            select.appendChild(option);
        });
        select.value = S.sort;
    }

    function syncSortSelect() {
        var select = $('sort');
        var order = S.sort;
        if (S.mode === 'FOLDER' && S.folderId) {
            var folder = findFolder(S.folderId);
            if (folder) { order = folder.sort; }
        }
        S.sort = order;
        select.value = order;
        select.title = S.mode === 'FOLDER' ? t('Folder display order') : t('Display order');
    }

    function findFolder(id) {
        for (var i = 0; i < S.folders.length; i++) {
            if (S.folders[i].id === id) { return S.folders[i]; }
        }
        return null;
    }

    /* menus */

    function fileMenu() {
        return [
            { label: t('Watched folders…'), accel: 'Ctrl+O', action: openFolderManager },
            { label: t('Rescan'), accel: 'F5', action: rescanAll },
            { label: t('Settings…'), accel: 'Ctrl+,', action: function () { showPage('settings'); } },
            { sep: true },
            { label: t('Quit'), accel: 'Ctrl+Q', action: function () { apocrypha.app.quit(); } }
        ];
    }

    function viewMenu() {
        return [
            { label: t('All photos'), accel: 'Ctrl+1', action: function () { selectMode('ALL', null); } },
            { label: t('Favorites'), accel: 'Ctrl+2', action: function () { selectMode('FAVORITES', null); } },
            { label: t('Recent photos'), accel: 'Ctrl+3', action: function () { selectMode('RECENT', null); } },
            { sep: true },
            { label: t('Slideshow'), accel: 'F11', action: startSlideshow },
            { label: t('Go to search'), accel: 'Ctrl+F', action: function () { $('search').focus(); $('search').select(); } },
            { label: t('Manage tags…'), accel: 'Ctrl+T', action: openTagManager },
            { label: t('Inspector'), accel: 'Ctrl+I', check: S.inspector, action: toggleInspector },
            { label: t('Dark mode'), accel: 'Ctrl+Shift+D', check: S.theme === 'dark', action: toggleTheme }
        ];
    }

    function helpMenu() {
        return [
            { label: t('About Freja'), action: openAbout },
            { label: 'DevTools', accel: 'F12', action: function () { apocrypha.window.openDevTools(); } }
        ];
    }

    document.querySelectorAll('#menubar .menu').forEach(function (node) {
        node.addEventListener('click', function (event) {
            event.stopPropagation();
            var kind = node.dataset.menu;
            var items = kind === 'file' ? fileMenu() : kind === 'view' ? viewMenu() : helpMenu();
            node.classList.add('open');
            showMenu(event.clientX, node.getBoundingClientRect().bottom, items, function () { node.classList.remove('open'); });
        });
    });

    /* ======================================================= sidebar ==== */

    function renderLibraryRows() {
        var list = $('library-list');
        clear(list);
        [['ALL', '🖼', 'All photos', S.stats ? S.stats.photos : 0],
         ['FAVORITES', '★', 'Favorites', S.stats ? S.stats.favorites : 0],
         ['RECENT', '🕘', 'Recent photos', S.stats ? S.stats.recent : 0]].forEach(function (row) {
            var node = el('div', 'nav-row' + (S.mode === row[0] && !S.folderId ? ' selected' : ''));
            node.appendChild(el('span', 'nav-icon', row[1]));
            node.appendChild(el('span', 'nav-title', t(row[2])));
            node.appendChild(el('span', 'nav-count', String(row[3])));
            node.addEventListener('click', function () { selectMode(row[0], null); });
            list.appendChild(node);
        });
    }

    function renderFolders() {
        var list = $('folder-list');
        clear(list);
        $('folder-count').textContent = String(S.folders.length);
        $('folder-empty').style.display = S.folders.length === 0 ? 'block' : 'none';
        S.folders.forEach(function (folder) {
            var selected = S.mode === 'FOLDER' && S.folderId === folder.id;
            var node = el('div', 'nav-row' + (selected ? ' selected' : '') + (folder.scanEnabled ? '' : ' muted-folder'));
            node.appendChild(el('span', 'nav-icon', folder.explicit ? '📁' : '📂'));
            var title = el('span', 'nav-title', folder.name);
            node.appendChild(title);
            node.appendChild(el('span', 'nav-count', String(folder.photoCount)));
            var tooltip = folder.path + (folder.explicit ? '' : '  ' + t('(auto-detected)'));
            if (folder.tags.length) { tooltip += '\n' + t('Tags') + ': ' + folder.tags.join(', '); }
            node.title = tooltip;
            node.addEventListener('click', function () { selectMode('FOLDER', folder.id); });
            node.addEventListener('contextmenu', function (event) {
                event.preventDefault();
                folderMenu(folder, event.clientX, event.clientY);
            });
            list.appendChild(node);
        });
    }

    function folderMenu(folder, x, y) {
        var items = SORTS.map(function (code) {
            return {
                label: t(SORT_KEYS[code]), check: folder.sort === code, action: function () {
                    call('freja:folder.sort', folder.id, code).then(function () {
                        if (S.mode === 'FOLDER' && S.folderId === folder.id) {
                            S.sort = code;
                            $('sort').value = code;
                        }
                        browse();
                    });
                }
            };
        });
        items.push({ sep: true });
        items.push({ label: t('Rename'), action: function () { renameFolder(folder); } });
        items.push({ label: t('Edit tags…'), action: function () { openTagPicker(folder.id, x, y); } });
        items.push({ label: t('Rescan'), action: function () { call('freja:folder.rescan', { ids: [folder.id] }).then(function () { setStatus(t('Rescanning the folder…')); }); } });
        items.push({ label: t('Open in Explorer'), action: function () { call('freja:revealFolder', folder.id).catch(function () { }); } });
        if (folder.explicit) {
            items.push({ sep: true });
            items.push({ label: t('Remove from library'), action: function () { removeFolder(folder); } });
        }
        showMenu(x, y, items);
    }

    function renameFolder(folder) {
        inputDialog(t('Folder display name'), folder.name).then(function (name) {
            if (name === null) { return; }
            var trimmed = name.trim();
            if (!trimmed) { setStatus(t('Enter a folder name')); return; }
            call('freja:folder.rename', folder.id, trimmed).then(function () {
                setStatus(t('Folder renamed: {0}', [trimmed]));
                browse();
            }).catch(function () { });
        });
    }

    function removeFolder(folder) {
        confirmDialog(t('Remove folder'),
            t('"{0}" and its subfolders will be removed from the library.', [folder.name])
            + '\n' + t('The photo files themselves are not deleted.'), t('Remove')).then(function (ok) {
            if (!ok) { return; }
            call('freja:folder.remove', { ids: [folder.id] }).then(function () {
                if (S.folderId === folder.id) { S.mode = 'ALL'; S.folderId = null; }
                browse();
            }).catch(function () { });
        });
    }

    /* =================================================== select / browse ==== */

    function selectMode(mode, folderId) {
        // Remember where the jump starts: smoothness is decided by folder order, not pixel distance.
        S.previousFolderId = S.mode === 'FOLDER' ? S.folderId : null;
        if (S.mode === mode && S.folderId === folderId) {
            if (mode === 'FOLDER' && folderId) {
                if (!scrollToSection('F' + folderId)) { browse(); }
                else { ensureFolderPhotos(folderId); }
            } else {
                stopScrollTween();
                $('grid-wrap').scrollTop = 0;
            }
            return;
        }
        var folderJump = S.mode === 'FOLDER' && mode === 'FOLDER' && folderId;
        S.mode = mode;
        S.folderId = folderId || null;
        if (mode === 'FOLDER' && folderId) {
            var folder = findFolder(folderId);
            if (folder) { setSetting('selectedFolder', folder.path); }
            S.scrollFolder = folderId;
        } else {
            S.scrollFolder = null;
        }
        syncSortSelect();
        renderLibraryRows();
        renderFolders();
        if (folderJump && scrollToSection('F' + folderId)) {
            S.scrollFolder = null;
            ensureFolderPhotos(folderId);
            return;
        }
        browse();
    }

    /**
     * Loads one metadata page. 'reset' replaces the whole grid state, 'append' takes the next
     * contiguous page and 'window' takes a page at an explicit absolute offset. The backend
     * already supports arbitrary offsets, so a jump to a distant section costs one call instead of
     * walking through every preceding page.
     */
    function browse(mode, offset) {
        if (!S.ready) { return Promise.resolve(); }
        mode = mode || 'reset';
        if (mode === 'append' && (!S.hasMore || S.loadingMore)) { return Promise.resolve(); }
        var generation = mode === 'append' ? S.generation : ++S.generation;
        var at = mode === 'append' ? S.loadedThrough : (offset || 0);
        if (mode === 'append') { S.loadingMore = true; }
        syncSortSelect();
        return call('freja:browse', {
            mode: S.mode, folderId: S.folderId, sort: S.sort, search: S.search || null, tagIds: S.tagIds,
            offset: at, limit: PAGE_SIZE
        }).then(function (result) {
            if (mode === 'append') { S.loadingMore = false; }
            if (generation !== S.generation) { return; }
            var rows = result.photos || [];
            var indexes = result.photoSections || [];
            if (mode === 'reset') {
                S.mode = result.mode;
                S.folderId = result.folderId;
                S.stats = result.stats;
                S.folders = result.folders;
                S.sections = result.sections || [];
                S.photos = rows.slice();
                S.photoSections = indexes.slice();
                S.loadedThrough = rows.length;
                S.total = result.total || rows.length;
                S.hasMore = !!result.hasMore;
                // keep the selection alive across refreshes
                var alive = new Map();
                S.photos.forEach(function (p) { if (S.selected.has(p.id)) { alive.set(p.id, p); } });
                S.selected = alive;
                S.anchor = null;
                alive.forEach(function (p) { S.anchor = p; });
                renderLibraryRows();
                renderFolders();
                renderGrid();
                updateCounts();
                updateSelectionInfo();
                showInspector(S.anchor);
                if (S.scrollFolder !== null && S.scrollFolder !== undefined) {
                    var key = 'F' + S.scrollFolder;
                    // Preload the selected folder's rows; the scroll jump itself may not fire an event.
                    ensureFolderPhotos(S.scrollFolder);
                    requestAnimationFrame(function () {
                        if (scrollToSection(key)) { S.scrollFolder = null; }
                    });
                }
                return;
            }
            if (result.offset !== at) { return; }
            fillWindow(at, rows, indexes);
            S.total = result.total || S.total;
            S.hasMore = !!result.hasMore;
            updateVirtualWindow();
            updateSelectionInfo();
        }, function () {
            if (mode === 'append') { S.loadingMore = false; }
        });
    }

    /** Windows currently being fetched, keyed by their aligned absolute offset. */
    var pendingWindows = new Set();

    /** Merges a metadata page into the sparse absolute-index arrays. */
    function fillWindow(offset, rows, indexes) {
        if (S.photos.length < offset) { S.photos.length = offset; }
        if (S.photoSections.length < offset) { S.photoSections.length = offset; }
        for (var i = 0; i < rows.length; i++) {
            S.photos[offset + i] = rows[i];
            S.photoSections[offset + i] = indexes[i];
        }
        S.loadedThrough = Math.max(S.loadedThrough || 0, offset + rows.length);
    }

    /** Folder jumps up to this many positions apart animate; farther jumps teleport. */
    var SMOOTH_FOLDER_DISTANCE = 5;

    /** Positions between two folders in the displayed folder order (fileName-independent). */
    function folderDistance(from, to) {
        if (from === null || from === undefined || to === null || to === undefined) { return Infinity; }
        var a = -1;
        var b = -1;
        for (var i = 0; i < S.folders.length; i++) {
            if (S.folders[i].id === from) { a = i; }
            if (S.folders[i].id === to) { b = i; }
        }
        if (a < 0 || b < 0) { return Infinity; }
        return Math.abs(a - b);
    }

    var scrollTween = 0;

    function stopScrollTween() {
        if (scrollTween) { cancelAnimationFrame(scrollTween); scrollTween = 0; }
    }

    /** Short eased tween. Native smooth scrolling was both too slow and easy to interrupt, which
     * left the previous section's cards visible above the target header. */
    function animateScroll(wrap, target) {
        stopScrollTween();
        var start = wrap.scrollTop;
        var delta = target - start;
        if (!delta) { return; }
        var duration = Math.min(500, Math.max(200, 200 + Math.abs(delta) / 4));
        var began = performance.now();
        function frame(now) {
            var t = Math.min(1, (now - began) / duration);
            var eased = 1 - Math.pow(1 - t, 3);
            wrap.scrollTop = start + delta * eased;
            if (t < 1) {
                scrollTween = requestAnimationFrame(frame);
            } else {
                wrap.scrollTop = target;
                scrollTween = 0;
            }
        }
        scrollTween = requestAnimationFrame(frame);
    }

    function scrollToSection(key) {
        var anchor = $('grid').querySelector('[data-section="' + CSS.escape(key) + '"]');
        if (!anchor) { return false; }
        var wrap = $('grid-wrap');
        // Measure in viewport coordinates. offsetTop is relative to #grid-wrap's padding box, while
        // wrap.offsetTop belongs to a different offset parent; subtracting it stopped the jump short
        // and left the previous section's sticky header pinned at the top.
        var top = anchor.getBoundingClientRect().top - wrap.getBoundingClientRect().top + wrap.scrollTop;
        var target = Math.max(0, top);
        var toFolder = key.charAt(0) === 'F' ? Number(key.slice(1)) : null;
        var near = folderDistance(S.previousFolderId, toFolder) <= SMOOTH_FOLDER_DISTANCE;
        if (near && Math.abs(target - wrap.scrollTop) > 0) {
            animateScroll(wrap, target);
        } else {
            stopScrollTween();
            wrap.scrollTop = target;
        }
        // A programmatic scroll does not always schedule a scroll event (for example when the
        // layout has just been rebuilt), so refresh the virtual window explicitly as well.
        scheduleVirtualUpdate(false);
        return true;
    }

    /** Fetches the single metadata page containing the given absolute photo index. */
    function ensurePhotosThrough(index) {
        if (index === null || index === undefined || index < 0) { return Promise.resolve(); }
        var start = Math.floor(index / PAGE_SIZE) * PAGE_SIZE;
        if (pendingWindows.has(start) || start >= (S.total || 0)) { return Promise.resolve(); }
        var end = Math.min(start + PAGE_SIZE, S.total || start + PAGE_SIZE);
        var complete = true;
        for (var i = start; i < end; i++) {
            if (!S.photos[i]) { complete = false; break; }
        }
        if (complete) { return Promise.resolve(); }
        pendingWindows.add(start);
        return browse('window', start).then(function () {
            pendingWindows.delete(start);
        }, function () {
            pendingWindows.delete(start);
        });
    }

    /** Starts loading the photos of a folder section selected from the sidebar. */
    function ensureFolderPhotos(folderId) {
        if (folderId === null || folderId === undefined) { return; }
        for (var i = 0; i < S.sections.length; i++) {
            var section = S.sections[i];
            if (section.folderId == folderId) {
                // The first rows of the section are enough to show; further rows follow on scroll.
                ensurePhotosThrough((section.start || 0) + Math.min(section.count || 0, 60));
                return;
            }
        }
    }

    function updateCounts() {
        if (!S.stats) { return; }
        $('status-count').textContent = S.stats.photos + ' 枚  ·  ' + fmtSize(S.stats.bytes);
        document.title = 'Freja — ' + S.stats.photos + ' ' + (LANG === 'ja' ? '枚の写真' : 'photos');
        $('welcome').hidden = S.stats.folders > 0;
    }

    /* ========================================================== grid ==== */

    var gridWrap = $('grid-wrap');

    /**
     * Virtual grid. Every section keeps its full height via a spacer, but only the rows inside
     * (or near) the viewport own real card nodes. The pool is indexed by photo position, so a card
     * is created once when it scrolls into view and removed again when it leaves the window.
     */
    var virtualSections = [];
    var virtualPending = false;
    var virtualGeometryDirty = false;
    var virtualLastWidth = -1;
    var OVERSCAN_ROWS = 2;
    /** Fullscreen changes the webview width before the viewer covers the grid and back again after
     * the viewer is hidden. Recomputing columns in that window flashes a wrong column count. */
    var gridGeometryLock = false;

    function gridGap() {
        var cells = gridWrap.querySelector('.cells');
        return cells ? parseFloat(getComputedStyle(cells).columnGap) || 10 : 10;
    }

    function scheduleVirtualUpdate(geometry) {
        if (geometry) { virtualGeometryDirty = true; }
        if (virtualPending) { return; }
        virtualPending = true;
        requestAnimationFrame(function () {
            virtualPending = false;
            if (virtualGeometryDirty) {
                virtualGeometryDirty = false;
                updateVirtualGeometry();
            }
            updateVirtualWindow();
        });
    }

    /** Recomputes columns and placeholder heights after a resize or a thumbnail-size change. */
    function updateVirtualGeometry() {
        var size = S.thumb || 170;
        var gap = gridGap();
        virtualSections.forEach(function (v) {
            var available = v.cells.clientWidth || 0;
            v.cols = Math.max(1, Math.floor((available + gap) / (size + gap)));
            v.rowHeight = size + gap;
            v.rows = Math.ceil(v.count / v.cols);
            v.cells.style.height = Math.max(0, v.rows * v.rowHeight - gap) + 'px';
            // Cards keep their <img> elements; updateVirtualWindow repositions them. Rebuilding the
            // pool here made every tag toggle reload and decode every thumbnail again.
        });
    }

    /** Pixel offset of the first row whose metadata has not been fetched yet. */
    function loadedEnd() {
        var loaded = S.loadedThrough || S.photos.length;
        for (var i = 0; i < virtualSections.length; i++) {
            var v = virtualSections[i];
            if (loaded < v.start) { break; }
            if (loaded <= v.start + v.count) {
                var wrapTop = gridWrap.getBoundingClientRect().top;
                var cellsTop = v.cells.getBoundingClientRect().top - wrapTop + gridWrap.scrollTop;
                return cellsTop + Math.ceil((loaded - v.start) / v.cols) * v.rowHeight;
            }
        }
        return gridWrap.scrollHeight;
    }

    /** Materializes only the rows intersecting the scroll viewport, with a small overscan. */
    function updateVirtualWindow() {
        if (!virtualSections.length) { return; }
        var size = S.thumb || 170;
        var gap = gridGap();
        var wrapTop = gridWrap.getBoundingClientRect().top;
        var margin = OVERSCAN_ROWS * (size + gap);
        var top = gridWrap.scrollTop - margin;
        var bottom = gridWrap.scrollTop + gridWrap.clientHeight + margin;
        var missing = -1;

        virtualSections.forEach(function (v) {
            if (!v.rows) {
                v.nodes.forEach(function (node) { node.remove(); });
                v.nodes.clear();
                return;
            }
            var cellsTop = v.cells.getBoundingClientRect().top - wrapTop + gridWrap.scrollTop;
            var cellsBottom = cellsTop + v.rows * v.rowHeight;
            if (cellsBottom < top || cellsTop > bottom) {
                v.nodes.forEach(function (node) { node.remove(); });
                v.nodes.clear();
                return;
            }
            var fromRow = Math.max(0, Math.floor((top - cellsTop) / v.rowHeight));
            var toRow = Math.min(v.rows - 1, Math.floor((bottom - cellsTop) / v.rowHeight));
            var from = v.start + fromRow * v.cols;
            var to = Math.min(v.start + v.count, v.start + (toRow + 1) * v.cols);

            // Release the cards which scrolled out of the window. The pool is keyed by photo id so
            // a filter change can move an existing card to a new index without reloading its image.
            v.nodes.forEach(function (node, key) {
                var index = Number(node.dataset.index);
                if (index < from || index >= to) {
                    node.remove();
                    v.nodes.delete(key);
                }
            });
            for (var i = from; i < to; i++) {
                var local = i - v.start;
                var left = (local % v.cols) * (size + gap);
                var upper = Math.floor(local / v.cols) * v.rowHeight;
                var photo = S.photos[i];
                var ready = !!photo;
                if (!ready && missing < 0) { missing = i; }
                var key = ready ? photo.id : 'p' + i;
                var card = v.nodes.get(key);
                var usable = card && (card.dataset.placeholder === '1') === !ready;
                if (usable) {
                    card.dataset.index = i;
                    if (card.style.left !== left + 'px') { card.style.left = left + 'px'; }
                    if (card.style.top !== upper + 'px') { card.style.top = upper + 'px'; }
                    continue;
                }
                if (card) { card.remove(); }
                card = ready ? cardEl(photo) : el('div', 'card placeholder');
                card.dataset.index = i;
                if (!ready) { card.dataset.placeholder = '1'; }
                card.style.position = 'absolute';
                card.style.left = left + 'px';
                card.style.top = upper + 'px';
                v.cells.appendChild(card);
                v.nodes.set(key, card);
            }
        });
        // A hole means the viewport reached metadata that was never fetched (for example after a
        // jump). Fetch just that aligned page instead of walking from index zero.
        if (missing >= 0) { ensurePhotosThrough(missing); }
        paintSelection();
        // The scroll height already covers the full catalog; ask for the next metadata page when
        // the viewport approaches the end of the rows loaded so far, not the end of the catalog.
        if (missing < 0 && S.hasMore && !S.loadingMore
                && gridWrap.scrollTop + gridWrap.clientHeight * 1.5 >= loadedEnd()) {
            browse('append');
        }
    }

    gridWrap.addEventListener('scroll', function () { scheduleVirtualUpdate(false); }, { passive: true });
    if (typeof ResizeObserver !== 'undefined') {
        new ResizeObserver(function () {
            var width = Math.round(gridWrap.clientWidth);
            if (gridGeometryLock) {
                // Keep the pre-fullscreen columns until the webview returns to its original width.
                if (Math.abs(width - virtualLastWidth) <= 2) { gridGeometryLock = false; }
                return;
            }
            if (width !== virtualLastWidth) {
                virtualLastWidth = width;
                scheduleVirtualUpdate(true);
            }
        }).observe(gridWrap);
    }

    function renderGrid() {
        var grid = $('grid');
        var previous = new Map();
        virtualSections.forEach(function (v) { previous.set(v.key, v); });
        var used = new Set();
        grid.style.setProperty('--thumb', S.thumb + 'px');
        if (S.sections.length === 0 && S.stats && S.stats.folders > 0) {
            clear(grid);
            virtualSections = [];
            grid.appendChild(el('div', 'grid-empty', t('No photos match the current filter')));
            return;
        }
        var next = [];
        S.sections.forEach(function (section, sectionIndex) {
            var key = section.folderId >= 0 ? 'F' + section.folderId : 'S' + sectionIndex + '-' + (section.label || '');
            used.add(key);
            var v = previous.get(key);
            var block;
            if (v) {
                // Keep the cells (and their <img> children) so a tag toggle only moves cards.
                block = v.cells.parentElement;
                var oldHead = block.querySelector('.section-head');
                if (oldHead) { oldHead.remove(); }
                if (section.label) { block.insertBefore(sectionHead(section), v.cells); }
                v.start = section.start || 0;
                v.count = section.count || 0;
            } else {
                block = el('div', 'section');
                block.dataset.section = key;
                if (section.label) { block.appendChild(sectionHead(section)); }
                var cells = el('div', 'cells');
                // The flex wrap of the CSS layout is replaced by absolute rows inside a full-height
                // spacer: the scroll range covers every photo, even the ones not fetched yet.
                cells.style.display = 'block';
                cells.style.position = 'relative';
                block.appendChild(cells);
                v = {
                    key: key, cells: cells, start: section.start || 0, count: section.count || 0,
                    cols: 1, rows: 0, rowHeight: 0, nodes: new Map()
                };
            }
            // appendChild also moves an existing block, restoring the requested section order.
            grid.appendChild(block);
            next.push(v);
        });
        previous.forEach(function (v, key) {
            if (!used.has(key) && v.cells.parentElement) { v.cells.parentElement.remove(); }
        });
        virtualSections = next;
        updateVirtualGeometry();
        updateVirtualWindow();
    }

    function sectionHead(section) {
        var folder = section.folderId >= 0;
        var head = el('div', 'section-head' + (folder ? ' clickable folder-head' : ' date-head'));

        // First line: identity and size.
        var main = el('div', 'section-head-main');
        main.appendChild(el('span', 'section-icon', folder ? '📁' : '🗓'));
        var title = el('span', 'section-title', section.label);
        title.title = section.label || '';
        main.appendChild(title);
        main.appendChild(el('span', 'section-count', t('{0} photos', [section.count])));
        main.appendChild(el('span', 'grow'));
        main.appendChild(el('span', 'section-chevron', '›'));
        head.appendChild(main);

        // Second line: where the section comes from and its tags.
        var sub = el('div', 'section-head-sub');
        var found = folder ? findFolder(section.folderId) : null;
        var detail = folder ? (found ? found.path : '') : t('Capture');
        var path = el('span', 'section-path', detail);
        path.title = detail;
        sub.appendChild(path);
        if (folder) {
            var chips = el('span', 'chips');
            if (section.tags.length === 0) {
                var empty = el('span', 'chip empty', '#+');
                empty.addEventListener('click', function (event) {
                    event.stopPropagation();
                    openTagPicker(section.folderId, event.clientX, event.clientY);
                });
                chips.appendChild(empty);
            }
            section.tags.forEach(function (tag) {
                var chip = el('span', 'chip');
                var dot = el('span', 'chip-dot');
                dot.style.background = tagColor(tag);
                chip.appendChild(dot);
                chip.appendChild(document.createTextNode('#' + tag));
                chip.addEventListener('click', function (event) {
                    event.stopPropagation();
                    openTagPicker(section.folderId, event.clientX, event.clientY);
                });
                chips.appendChild(chip);
            });
            sub.appendChild(chips);
            head.addEventListener('click', function () {
                S.previousFolderId = S.mode === 'FOLDER' ? S.folderId : null;
                S.mode = 'FOLDER';
                S.folderId = section.folderId;
                if (found) { setSetting('selectedFolder', found.path); }
                renderLibraryRows();
                renderFolders();
            });
        }
        head.appendChild(sub);
        return head;
    }

    function cardEl(photo) {
        var card = el('div', 'card');
        card.dataset.id = photo.id;
        var img = el('img');
        // Virtualisation already bounds the number of cards to the visible rows. Native lazy
        // loading delays images inserted during a scroll jump (e.g. selecting a folder), which
        // leaves only an empty card until the next scroll; eager avoids that.
        img.loading = 'eager';
        img.decoding = 'async';
        img.alt = photo.fileName;
        img.src = thumbUrl(photo);
        img.addEventListener('error', function () {
            if (!img.dataset.retry) {
                img.dataset.retry = '1';
                img.src = thumbUrl(photo) + '&retry=1';
            } else {
                card.classList.add('image-error');
                img.style.visibility = 'hidden';
            }
        });
        card.appendChild(img);
        if (photo.favorite) { card.appendChild(el('span', 'badge-fav', '★')); }
        if (S.selected.has(photo.id)) { card.classList.add('selected'); }
        card.addEventListener('click', function (event) {
            if (event.ctrlKey || event.metaKey) { toggleSelection(photo); }
            else if (event.shiftKey && S.anchor) { selectRange(photo); }
            else { selectOnly(photo); }
        });
        card.addEventListener('dblclick', function () { openViewer(photo); });
        card.addEventListener('contextmenu', function (event) {
            event.preventDefault();
            if (!S.selected.has(photo.id)) { selectOnly(photo); }
            photoMenu(photo, event.clientX, event.clientY);
        });
        return card;
    }

    function thumbUrl(photo) {
        var bust = S.bust.get(photo.id);
        return '/photo/' + photo.id + '/thumb?v=' + photo.modifiedAt + (bust ? '&b=' + bust : '');
    }

    /* selection */

    function selectOnly(photo) {
        S.selected = new Map([[photo.id, photo]]);
        S.anchor = photo;
        paintSelection();
        updateSelectionInfo();
        showInspector(photo);
    }

    function toggleSelection(photo) {
        if (S.selected.has(photo.id)) {
            S.selected.delete(photo.id);
            S.anchor = null;
            S.selected.forEach(function (p) { S.anchor = p; });
        } else {
            S.selected.set(photo.id, photo);
            S.anchor = photo;
        }
        paintSelection();
        updateSelectionInfo();
        showInspector(S.anchor);
    }

    function selectRange(photo) {
        var from = indexOf(S.anchor);
        var to = indexOf(photo);
        if (from < 0 || to < 0) { selectOnly(photo); return; }
        if (from > to) { var swap = from; from = to; to = swap; }
        S.selected = new Map();
        for (var i = from; i <= to; i++) {
            var p = S.photos[i];
            if (p) { S.selected.set(p.id, p); }
        }
        S.anchor = photo;
        paintSelection();
        updateSelectionInfo();
        showInspector(photo);
    }

    function indexOf(photo) {
        if (!photo) { return -1; }
        for (var i = 0; i < S.photos.length; i++) {
            var p = S.photos[i];
            if (p && p.id === photo.id) { return i; }
        }
        return -1;
    }

    function paintSelection() {
        gridWrap.querySelectorAll('.card').forEach(function (card) {
            card.classList.toggle('selected', S.selected.has(Number(card.dataset.id)));
        });
    }

    function clearSelection() {
        S.selected = new Map();
        S.anchor = null;
        paintSelection();
        updateSelectionInfo();
        showInspector(null);
    }

    function selectedPhotos() {
        return Array.from(S.selected.values());
    }

    function updateSelectionInfo() {
        var node = $('status-selection');
        var photos = selectedPhotos();
        if (photos.length === 0) { node.textContent = ''; return; }
        if (photos.length === 1) {
            var p = photos[0];
            var dims = displaySize(p);
            node.textContent = p.fileName + '  ·  ' + dims[0] + '×' + dims[1] + '  ·  ' + fmtDateTime(p.capturedAt) + '  ·  ' + fmtSize(p.byteSize);
            return;
        }
        var bytes = photos.reduce(function (sum, photo) { return sum + photo.byteSize; }, 0);
        node.textContent = photos.length + ' 枚  ·  ' + (LANG === 'ja' ? '合計 ' : 'total ') + fmtSize(bytes);
    }

    /* ================================================= context menu ==== */

    function photoMenu(photo, x, y) {
        var targets = selectedPhotos().length > 1 ? selectedPhotos() : [photo];
        var suffix = targets.length > 1 ? t('({0} photos)', [targets.length]) : '';
        var ids = targets.map(function (p) { return p.id; });
        showMenu(x, y, [
            { label: t('Open') + suffix, action: function () { openViewer(photo, targets); } },
            { label: t(photo.favorite ? 'Remove from favorites' : 'Add to favorites') + suffix, action: function () { setFavorites(ids, !photo.favorite); } },
            { sep: true },
            { label: t('Open in Explorer'), action: function () { call('freja:reveal', photo.id).catch(function () { }); } },
            { label: t('Copy paths') + suffix, action: function () { copyPaths(ids); } },
            { label: t('Reload') + suffix, action: function () { reimport(ids); } },
            { sep: true },
            { label: t('Remove from library') + suffix, action: function () { removeFromLibrary(ids, targets); } },
            { label: t('Delete from disk') + suffix, action: function () { deleteFromDisk(ids, targets); } }
        ]);
    }

    function setFavorites(ids, value) {
        call('freja:favorite', { ids: ids, value: value }).then(function () {
            ids.forEach(function (id) {
                var photo = S.photos.find(function (p) { return p && p.id === id; });
                if (photo) { photo.favorite = value; }
                S.selected.forEach(function (p, key) { if (key === id) { p.favorite = value; } });
                var card = gridWrap.querySelector('.card[data-id="' + id + '"]');
                if (card) {
                    var badge = card.querySelector('.badge-fav');
                    if (value && !badge) { card.appendChild(el('span', 'badge-fav', '★')); }
                    if (!value && badge) { badge.remove(); }
                }
            });
            showInspector(S.anchor);
            browse();
        }).catch(function () { });
    }

    function copyPaths(ids) {
        call('freja:copyPaths', { ids: ids }).then(function (count) {
            setStatus(count > 1 ? t('{0} paths copied', [count]) : t('Path copied'));
        }).catch(function () { });
    }

    function reimport(ids) {
        ids.forEach(function (id) { S.bust.set(id, Date.now()); });
        call('freja:reimport', { ids: ids }).then(function () {
            setStatus(t('Reloading {0} photos…', [ids.length]));
            renderGrid();
        }).catch(function () { });
    }

    function removeFromLibrary(ids, targets) {
        var description = targets.length > 1
            ? t('{0} photos will be removed from the library.', [targets.length])
            : t('{0} will be removed from the library.', [targets[0].fileName]);
        confirmDialog(t('Remove photos from library'), description + '\n' + t('The photo files themselves are not deleted.'), t('Delete'), true).then(function (ok) {
            if (!ok) { return; }
            call('freja:removePhotos', { ids: ids }).then(function () {
                clearSelection();
                browse();
            }).catch(function () { });
        });
    }

    function deleteFromDisk(ids, targets) {
        var description = targets.length > 1
            ? t('{0} image files will be permanently deleted from disk.', [targets.length])
            : t('{0} will be permanently deleted from disk.', [targets[0].fileName]);
        confirmDialog(t('Delete from disk permanently'), description + '\n' + t('This cannot be undone. The catalog entry is removed too.'), t('Delete'), true).then(function (ok) {
            if (!ok) { return; }
            call('freja:deleteFromDisk', { ids: ids }).then(function (result) {
                clearSelection();
                setStatus(result.failed === 0
                    ? t('Deleted {0} photos from disk', [result.deleted])
                    : t('Deleted {0}, failed {1}', [result.deleted, result.failed]));
                browse();
            }).catch(function () { });
        });
    }

    /* ===================================================== inspector ==== */

    function showInspector(photo) {
        if (!photo) {
            $('insp-preview').hidden = true;
            var name = $('insp-name');
            name.textContent = t('Select a photo');
            name.classList.add('placeholder');
            $('insp-folder').textContent = '';
            $('val-capture').textContent = '—';
            $('val-dimensions').textContent = '—';
            $('val-size').textContent = '—';
            $('val-camera').textContent = '—';
            $('val-lens').textContent = '—';
            $('val-exposure').textContent = '—';
            $('insp-caption').value = '';
            $('insp-caption').disabled = true;
            $('insp-open').disabled = true;
            $('insp-favorite').disabled = true;
            $('insp-reveal').style.display = 'none';
            $('insp-save-caption').style.display = 'none';
            renderStars(0, false);
            return;
        }
        call('freja:photo', photo.id).then(function (detail) {
            if (!detail) { return; }
            S.inspectorPhoto = detail;
            var name = $('insp-name');
            name.textContent = detail.fileName;
            name.classList.remove('placeholder');
            $('insp-folder').textContent = shortenMiddle(detail.folderPath, 46);
            $('insp-folder').title = detail.folderPath;
            var preview = $('insp-preview');
            preview.src = '/photo/' + detail.id + '/thumb?v=' + detail.modifiedAt;
            preview.hidden = false;
            $('val-capture').textContent = fmtDateTime(detail.capturedAt);
            var dims = displaySize(detail);
            $('val-dimensions').textContent = dims[0] + ' × ' + dims[1] + '  ·  ' + (detail.extension || '').toUpperCase();
            $('val-size').textContent = fmtSize(detail.byteSize);
            $('val-camera').textContent = cameraLabel(detail);
            $('val-lens').textContent = detail.lens || '—';
            $('val-exposure').textContent = exposureLabel(detail);
            var caption = $('insp-caption');
            caption.value = detail.caption || '';
            caption.disabled = false;
            $('insp-save-caption').style.display = 'none';
            $('insp-open').disabled = false;
            $('insp-favorite').disabled = false;
            $('insp-favorite').textContent = detail.favorite ? t('Favorited') : t('Favorite');
            $('insp-reveal').style.display = '';
            renderStars(detail.rating, true);
        }).catch(function () { });
    }

    function cameraLabel(p) {
        if (!p.cameraMake) { return p.cameraModel || '—'; }
        if (!p.cameraModel) { return p.cameraMake; }
        var upper = function (s) { return s.toUpperCase(); };
        return upper(p.cameraModel).indexOf(upper(p.cameraMake)) === 0 ? p.cameraModel : p.cameraMake + ' ' + p.cameraModel;
    }

    function exposureLabel(p) {
        var parts = [];
        if (p.exposure) { parts.push(p.exposure); }
        if (p.aperture !== null && p.aperture !== undefined) { parts.push('f/' + trimNumber(p.aperture)); }
        if (p.iso !== null && p.iso !== undefined) { parts.push('ISO ' + p.iso); }
        if (p.focalLength !== null && p.focalLength !== undefined) { parts.push(trimNumber(p.focalLength) + ' mm'); }
        return parts.length ? parts.join('  ·  ') : '—';
    }

    function trimNumber(value) {
        return value === Math.round(value) ? String(Math.round(value)) : String(value);
    }

    function shortenMiddle(text, max) {
        if (!text || text.length <= max) { return text || ''; }
        var side = Math.max(4, Math.floor((max - 1) / 2));
        return text.slice(0, side) + '…' + text.slice(text.length - side);
    }

    function renderStars(rating, enabled) {
        var holder = $('insp-stars');
        clear(holder);
        for (var i = 1; i <= 5; i++) {
            (function (value) {
                var star = el('button', value <= rating ? 'filled' : null, value <= rating ? '★' : '☆');
                star.title = t('{0} stars', [value]);
                star.disabled = !enabled;
                star.addEventListener('click', function () {
                    var photo = S.inspectorPhoto;
                    if (!photo) { return; }
                    var next = photo.rating === value ? 0 : value;
                    call('freja:rating', photo.id, next).then(function () {
                        photo.rating = next;
                        var card = S.photos.find(function (p) { return p && p.id === photo.id; });
                        if (card) { card.rating = next; }
                        renderStars(next, true);
                    }).catch(function () { });
                });
                holder.appendChild(star);
            })(i);
        }
    }

    $('insp-caption').addEventListener('input', function () {
        var photo = S.inspectorPhoto;
        if (!photo) { return; }
        var changed = (photo.caption || '') !== $('insp-caption').value.trim();
        $('insp-save-caption').style.display = changed ? '' : 'none';
    });

    $('insp-caption').addEventListener('keydown', function (event) {
        if (event.key === 'Enter' && event.ctrlKey) { saveCaption(); }
    });

    $('insp-caption').addEventListener('blur', function () {
        if ($('insp-save-caption').style.display !== 'none') { saveCaption(); }
    });

    function saveCaption() {
        var photo = S.inspectorPhoto;
        if (!photo) { return; }
        var value = $('insp-caption').value.trim();
        call('freja:caption', photo.id, value).then(function () {
            photo.caption = value || null;
            var card = S.photos.find(function (p) { return p && p.id === photo.id; });
            if (card) { card.caption = value || null; }
            $('insp-save-caption').style.display = 'none';
        }).catch(function () { });
    }

    $('insp-open').addEventListener('click', function () {
        if (S.inspectorPhoto) { openViewer(S.inspectorPhoto); }
    });

    $('insp-favorite').addEventListener('click', function () {
        var photo = S.inspectorPhoto;
        if (!photo) { return; }
        setFavorites([photo.id], !photo.favorite);
    });

    $('insp-reveal').addEventListener('click', function () {
        if (S.inspectorPhoto) { call('freja:reveal', S.inspectorPhoto.id).catch(function () { }); }
    });

    $('insp-save-caption').addEventListener('click', saveCaption);

    /* ====================================================== statusbar ==== */

    function setStatus(message) {
        $('status-message').textContent = message;
    }

    var lastState = null;

    function applyState(report) {
        lastState = report;
        if (!report) { return; }
        var progress = $('status-progress');
        if (report.scanning) {
            progress.hidden = false;
            if (report.total > 0) {
                progress.classList.remove('indeterminate');
                var ratio = Math.min(1, report.processed / report.total);
                $('progress-fill').style.width = Math.max(3, ratio * 100) + '%';
            } else {
                progress.classList.add('indeterminate');
                $('progress-fill').style.width = '40%';
            }
            var detail = report.message ? report.message : t('Scanning');
            if (report.total > 0) { detail += '  (' + report.processed + ' / ' + report.total + ')'; }
            detail += '  ·  ' + fmtDuration(report.elapsedMillis);
            setStatus(detail.length > 110 ? detail.slice(0, 109) + '…' : detail);
        } else {
            progress.hidden = true;
            if (report.errors > 0) {
                setStatus(report.message ? report.message : t('{0} files could not be read', [report.errors]));
            } else if (report.imported > 0) {
                setStatus(t('{0} photos updated ({1})', [report.imported, fmtDuration(report.elapsedMillis)]));
            } else if (report.elapsedMillis > 0) {
                setStatus(t('Scan complete ({0})', [fmtDuration(report.elapsedMillis)]));
            } else {
                setStatus(t('Ready'));
            }
        }
        if (report.scanning === false && lastReportWasScanning) {
            lastReportWasScanning = false;
            debouncedRefresh();
        }
        if (report.scanning) { lastReportWasScanning = true; }
    }

    var lastReportWasScanning = false;

    var toastTimer = 0;

    function toast(message) {
        var node = $('toast');
        if (!node) {
            node = el('div');
            node.id = 'toast';
            document.body.appendChild(node);
        }
        node.textContent = message;
        node.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { node.classList.remove('show'); }, 2200);
    }

    /* ======================================================== viewer ==== */

    const V = { photos: [], index: 0, open: false, fit: true, zoom: 1, panX: 0, panY: 0, timer: 0, generation: 0, fullscreen: false, cache: new Map() };

    var viewerImage = $('viewer-image');
    var viewerCanvas = $('viewer-canvas');

    function openViewer(photo, subset) {
        var list = subset && subset.length ? subset.slice() : S.photos.filter(Boolean).slice();
        if (list.length === 0 && photo) { list = [photo]; }
        if (list.length === 0) { return; }
        var index = 0;
        for (var i = 0; i < list.length; i++) {
            if (list[i].id === photo.id) { index = i; break; }
        }
        V.photos = list;
        V.open = true;
        gridGeometryLock = true;
        $('viewer').hidden = false;
        setFullscreen(true);
        showPhoto(index);
    }

    function closeViewer() {
        V.open = false;
        setFullscreen(false);
        stopSlideshow();
        $('viewer').hidden = true;
        viewerImage.hidden = true;
        // Normally the ResizeObserver releases the lock when the webview regains its original
        // width. This is a safety net for the case where no resize notification arrives.
        setTimeout(function () {
            if (!V.open) { gridGeometryLock = false; }
        }, 1000);
    }

    /** Switches the native window between normal and fullscreen while the viewer is open. */
    function setFullscreen(on) {
        if (V.fullscreen === on) { return; }
        V.fullscreen = on;
        var button = $('v-fullscreen');
        button.classList.toggle('on', on);
        button.title = t(on ? 'Exit fullscreen' : 'Fullscreen');
        call('freja:fullscreen', on).catch(function () { });
    }

    function toggleFullscreen() {
        setFullscreen(!V.fullscreen);
    }

    /** Decoded originals kept warm so back-and-forth navigation never blanks the canvas. */
    function loadOriginal(photo) {
        var cached = V.cache.get(photo.id);
        if (cached && cached.complete && cached.naturalWidth) { return Promise.resolve(cached); }
        var url = '/photo/' + photo.id + '/original?v=' + photo.modifiedAt;
        return new Promise(function (resolve, reject) {
            var img = new Image();
            img.onload = function () {
                // decode() waits for a paintable frame; without it the swap can show a blank frame.
                if (img.decode) { img.decode().then(function () { resolve(img); }, function () { resolve(img); }); }
                else { resolve(img); }
            };
            img.onerror = function () { reject(new Error('Cannot load the image')); };
            img.src = url;
        }).then(function (img) {
            V.cache.delete(photo.id);
            V.cache.set(photo.id, img);
            while (V.cache.size > 6) { V.cache.delete(V.cache.keys().next().value); }
            return img;
        });
    }

    function prefetchNeighbours() {
        [-1, 1].forEach(function (delta) {
            var photo = V.photos[V.index + delta];
            if (photo && !V.cache.has(photo.id)) { loadOriginal(photo).catch(function () { }); }
        });
    }

    function showPhoto(index) {
        if (!V.photos.length || index < 0 || index >= V.photos.length) { return; }
        V.index = index;
        var photo = V.photos[index];
        updateViewerToolbar();
        var token = ++V.generation;
        // Keep the previous frame on screen while the next one is being decoded. Only the very
        // first photo shows the loading text; later navigation replaces the image in place.
        if (viewerImage.hidden) {
            $('viewer-status').textContent = t('Loading…');
            $('viewer-status').hidden = false;
        }
        loadOriginal(photo).then(function (img) {
            if (token !== V.generation) { return; }
            V.fit = true;
            V.zoom = 1;
            V.panX = 0;
            V.panY = 0;
            viewerImage.src = img.src;
            if (viewerImage.hidden) { viewerImage.hidden = false; }
            $('viewer-status').hidden = true;
            applyTransform();
            prefetchNeighbours();
        }).catch(function () {
            if (token !== V.generation) { return; }
            $('viewer-status').textContent = t('Cannot load the image');
            $('viewer-status').hidden = viewerImage.hidden;
        });
    }

    function updateViewerToolbar() {
        var photo = V.photos[V.index];
        if (!photo) { return; }
        $('viewer-title').textContent = photo.fileName;
        var dims = displaySize(photo);
        $('viewer-counter').textContent = (V.index + 1) + ' / ' + V.photos.length + '   ·   ' + dims[0] + '×' + dims[1] + '   ·   ' + fmtSize(photo.byteSize);
        var favorite = $('v-favorite');
        favorite.textContent = photo.favorite ? '★' : '☆';
        favorite.classList.toggle('on', photo.favorite);
        favorite.title = t(photo.favorite ? 'Remove from favorites' : 'Add to favorites');
        // keep the grid card in sync after in-viewer favorites
        var card = S.photos.find(function (p) { return p && p.id === photo.id; });
        if (card) { card.favorite = photo.favorite; }
    }

    function viewerMove(delta) {
        if (!V.photos.length) { return; }
        showPhoto((V.index + delta + V.photos.length) % V.photos.length);
    }

    function viewerFavorite() {
        var photo = V.photos[V.index];
        if (!photo) { return; }
        var value = !photo.favorite;
        call('freja:favorite', { ids: [photo.id], value: value }).then(function () {
            photo.favorite = value;
            updateViewerToolbar();
            var card = gridWrap.querySelector('.card[data-id="' + photo.id + '"]');
            if (card) {
                var badge = card.querySelector('.badge-fav');
                if (value && !badge) { card.appendChild(el('span', 'badge-fav', '★')); }
                if (!value && badge) { badge.remove(); }
            }
            debouncedBrowse();
        }).catch(function () { });
    }

    var debouncedBrowse = debounce(function () { browse(); }, 600);

    function setFit() {
        V.fit = true;
        V.zoom = 1;
        V.panX = 0;
        V.panY = 0;
        applyTransform();
    }

    function zoomBy(factor) {
        if (viewerImage.hidden) { return; }
        V.fit = false;
        V.zoom = Math.max(0.05, Math.min(8, V.zoom * factor));
        applyTransform();
    }

    function applyTransform() {
        viewerImage.classList.toggle('zoomed', !V.fit);
        viewerImage.style.transform = V.fit ? '' : 'translate(' + V.panX + 'px,' + V.panY + 'px) scale(' + V.zoom + ')';
    }

    $('v-prev').addEventListener('click', function () { viewerMove(-1); });
    $('v-next').addEventListener('click', function () { viewerMove(1); });
    $('v-favorite').addEventListener('click', viewerFavorite);
    $('v-fit').addEventListener('click', setFit);
    $('v-close').addEventListener('click', closeViewer);
    $('v-slideshow').addEventListener('click', toggleSlideshow);
    $('v-fullscreen').addEventListener('click', toggleFullscreen);

    function toggleSlideshow() {
        if (V.timer) {
            stopSlideshow();
        } else {
            V.timer = setInterval(function () { viewerMove(1); }, 3600);
            $('v-slideshow').textContent = '❚❚';
            $('v-slideshow').title = t('Stop the slideshow');
        }
    }

    function stopSlideshow() {
        if (V.timer) {
            clearInterval(V.timer);
            V.timer = 0;
        }
        $('v-slideshow').textContent = '▶';
        $('v-slideshow').title = t('Slideshow');
    }

    viewerCanvas.addEventListener('wheel', function (event) {
        if (event.deltaY > 0) { viewerMove(1); } else if (event.deltaY < 0) { viewerMove(-1); }
        event.preventDefault();
    }, { passive: false });

    var dragging = null;

    viewerCanvas.addEventListener('mousedown', function (event) {
        if (event.detail === 2 && V.fit) { closeViewer(); return; }
        dragging = { x: event.clientX, y: event.clientY };
    });

    window.addEventListener('mousemove', function (event) {
        if (!dragging || V.fit) { return; }
        V.panX += event.clientX - dragging.x;
        V.panY += event.clientY - dragging.y;
        dragging = { x: event.clientX, y: event.clientY };
        applyTransform();
    });

    window.addEventListener('mouseup', function () { dragging = null; });

    /* ================================================== floating menus ==== */

    function closeFloating() {
        clear($('floating'));
        document.querySelectorAll('#menubar .menu.open').forEach(function (node) { node.classList.remove('open'); });
    }

    window.addEventListener('click', closeFloating);
    window.addEventListener('resize', closeFloating);
    window.addEventListener('scroll', closeFloating, true);

    function showMenu(x, y, items, onDismiss) {
        closeFloating();
        var menu = el('div', 'ctxmenu');
        items.forEach(function (item) {
            if (item.sep) { menu.appendChild(el('div', 'sep')); return; }
            var row = el('div', 'item' + (item.disabled ? ' disabled' : ''));
            var check = el('span', 'check');
            if (item.check) { check.textContent = '✓'; }
            row.appendChild(check);
            row.appendChild(el('span', null, item.label));
            if (item.accel) { row.appendChild(el('span', 'accel', item.accel)); }
            if (!item.disabled) {
                row.addEventListener('click', function (event) {
                    event.stopPropagation();
                    closeFloating();
                    if (onDismiss) { onDismiss(); }
                    item.action();
                });
            }
            menu.appendChild(row);
        });
        menu.addEventListener('click', function (event) { event.stopPropagation(); });
        $('floating').appendChild(menu);
        var rect = menu.getBoundingClientRect();
        if (x + rect.width > window.innerWidth - 8) { x = Math.max(8, window.innerWidth - rect.width - 8); }
        if (y + rect.height > window.innerHeight - 8) { y = Math.max(8, window.innerHeight - rect.height - 8); }
        menu.style.left = x + 'px';
        menu.style.top = y + 'px';
        return menu;
    }

    function showPopover(anchorRect, node) {
        closeFloating();
        var pop = el('div', 'popover');
        pop.appendChild(node);
        pop.addEventListener('click', function (event) { event.stopPropagation(); });
        $('floating').appendChild(pop);
        var rect = pop.getBoundingClientRect();
        var x = anchorRect.left;
        var y = anchorRect.bottom + 6;
        if (x + rect.width > window.innerWidth - 12) { x = Math.max(12, window.innerWidth - rect.width - 12); }
        if (y + rect.height > window.innerHeight - 12) { y = Math.max(12, anchorRect.top - rect.height - 6); }
        pop.style.left = x + 'px';
        pop.style.top = y + 'px';
        return pop;
    }

    /* ======================================================== dialogs ==== */

    function modal(options) {
        return new Promise(function (resolve) {
            var backdrop = el('div', 'modal-backdrop');
            var box = el('div', 'modal' + (options.wide ? ' wide' : ''));
            if (options.title) { box.appendChild(el('h2', null, options.title)); }
            if (options.node) { box.appendChild(options.node); }
            var footer = el('div', 'modal-footer');
            (options.buttons || [{ label: t('Close'), value: 'close' }]).forEach(function (button) {
                var node = el('button', button.primary ? 'primary' : (button.danger ? 'danger' : null), button.label);
                if (button.primary) { node.classList.add('primary'); }
                node.addEventListener('click', function () {
                    if (options.validate && !options.validate()) { return; }
                    backdrop.remove();
                    resolve(button.value);
                });
                footer.appendChild(node);
            });
            box.appendChild(footer);
            backdrop.appendChild(box);
            backdrop.addEventListener('click', function (event) { if (event.target === backdrop && !options.sticky) { backdrop.remove(); resolve(null); } });
            document.body.appendChild(backdrop);
            var focus = box.querySelector('input, textarea, button');
            if (focus) { focus.focus(); }
            box.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') { backdrop.remove(); resolve(null); }
                if (event.key === 'Enter' && event.target.tagName !== 'TEXTAREA' && footer.firstChild) { footer.firstChild.click(); }
            });
        });
    }

    function confirmDialog(title, message, okLabel, danger) {
        return modal({
            title: title,
            node: textNode(message),
            buttons: [{ label: okLabel || t('OK'), value: true, primary: !danger, danger: danger }, { label: t('Cancel'), value: false }]
        }).then(function (value) { return value === true; });
    }

    function inputDialog(title, initial, placeholder) {
        var input = el('input');
        input.type = 'text';
        input.value = initial || '';
        if (placeholder) { input.placeholder = placeholder; }
        input.style.width = '100%';
        return modal({
            title: title,
            node: input,
            buttons: [{ label: t('OK'), value: true, primary: true }, { label: t('Cancel'), value: null }]
        }).then(function (value) { return value === true ? input.value : null; });
    }

    /* ---- watched folders ---- */

    function openFolderManager() {
        var body = el('div');
        var table = el('table');
        var head = el('tr');
        ['Folder', 'Photos', 'Path'].forEach(function (key) { head.appendChild(el('th', null, t(key))); });
        table.appendChild(head);
        var tbody = el('tbody');
        table.appendChild(tbody);
        body.appendChild(table);

        var picked = new Set();

        function refill() {
            return call('freja:folders').then(function (folders) {
                S.folders = folders;
                clear(tbody);
                folders.forEach(function (folder) {
                    var row = el('tr', 'pick' + (picked.has(folder.id) ? ' on' : ''));
                    row.appendChild(el('td', null, folder.name + (folder.explicit ? '' : ' ' + t('(auto-detected)'))));
                    row.appendChild(el('td', null, String(folder.photoCount)));
                    var path = el('td', 'muted', folder.path);
                    row.appendChild(path);
                    row.addEventListener('click', function () {
                        if (picked.has(folder.id)) { picked.delete(folder.id); } else { picked.add(folder.id); }
                        row.classList.toggle('on');
                        syncButtons();
                    });
                    tbody.appendChild(row);
                });
            });
        }

        var rowButtons = el('div', 'grow-row');
        rowButtons.style.marginTop = '12px';
        var addButton = el('button', 'primary', t('Add folder'));
        var scanButton = el('button', null, t('Scan'));
        var muteButton = el('button', null, t('Mute / unmute'));
        var removeButton = el('button', null, t('Remove'));
        var clearButton = el('button', null, t('Clear all'));
        [addButton, scanButton, muteButton, removeButton, clearButton].forEach(function (b) { rowButtons.appendChild(b); });
        body.appendChild(rowButtons);

        var scanStart = el('label', null);
        var scanStartBox = el('input');
        scanStartBox.type = 'checkbox';
        scanStartBox.checked = S.settings.scanOnStartup === 'true';
        scanStart.appendChild(scanStartBox);
        scanStart.appendChild(el('span', null, t('Scan folders when Freja starts')));
        var sweep = el('label', null);
        var sweepBox = el('input');
        sweepBox.type = 'checkbox';
        sweepBox.checked = S.settings.sweep === 'true';
        sweep.appendChild(sweepBox);
        sweep.appendChild(el('span', null, t('Sweep for missed changes every 5 minutes')));
        var checks = el('div');
        checks.style.cssText = 'display:flex;flex-direction:column;gap:8px;margin-top:14px';
        checks.appendChild(scanStart);
        checks.appendChild(sweep);
        body.appendChild(checks);
        body.appendChild(el('p', 'hint', t('Photo files are never moved, renamed or deleted here; only the catalog changes.')));

        scanStartBox.addEventListener('change', function () {
            setSetting('scanOnStartup', String(scanStartBox.checked));
            syncSettingsPage();
        });
        sweepBox.addEventListener('change', function () {
            setSetting('sweep', String(sweepBox.checked));
            syncSettingsPage();
        });

        function syncButtons() {
            scanButton.disabled = picked.size === 0;
            muteButton.disabled = picked.size === 0;
            removeButton.disabled = picked.size === 0;
        }
        syncButtons();

        var changed = false;

        addButton.addEventListener('click', function () {
            call('freja:pickFolder').then(function (directory) {
                if (!directory) { return; }
                return call('freja:folder.add', directory).then(function (folder) {
                    changed = true;
                    setStatus(t('Added {0}', [folder.name]));
                    refill();
                    debouncedBrowse();
                });
            }).catch(function () { });
        });

        scanButton.addEventListener('click', function () {
            call('freja:folder.rescan', { ids: Array.from(picked) }).then(function () {
                changed = true;
                setStatus(t('Rescanning the folder…'));
                refill();
            }).catch(function () { });
        });

        muteButton.addEventListener('click', function () {
            var chain = Promise.resolve();
            picked.forEach(function (id) {
                var folder = findFolder(id) || S.folders.find(function (f) { return f.id === id; });
                if (folder) {
                    chain = chain.then(function () { return call('freja:folder.scanEnabled', id, !folder.scanEnabled); });
                }
            });
            chain.then(function () { changed = true; refill(); syncSettingsPage(); }).catch(function () { });
        });

        removeButton.addEventListener('click', function () {
            confirmDialog(t('Remove'), t('Remove {0} folders and their catalog data? The photo files stay on disk.', [picked.size]), t('Remove')).then(function (ok) {
                if (!ok) { return; }
                call('freja:folder.remove', { ids: Array.from(picked) }).then(function () {
                    changed = true;
                    picked.clear();
                    setStatus(t('Folders removed'));
                    refill();
                    debouncedBrowse();
                }).catch(function () { });
            });
        });

        clearButton.addEventListener('click', function () {
            confirmDialog(t('Clear all'), t('Remove every folder and its catalog data? The photo files stay on disk.'), t('Clear'), true).then(function (ok) {
                if (!ok) { return; }
                call('freja:folder.clear').then(function () {
                    changed = true;
                    picked.clear();
                    setStatus(t('Watched folders cleared'));
                    refill();
                    debouncedBrowse();
                }).catch(function () { });
            });
        });

        refill();
        modal({ title: t('Watched folder management'), node: body, wide: true, buttons: [{ label: t('Close'), value: true, primary: true }] })
            .then(function () { browse(); });
    }

    /* ---- tags ---- */

    function refreshTags() {
        return Promise.all([call('freja:tag.list'), call('freja:tag.allLinks')]).then(function (both) {
            S.tags = both[0];
            S.links = both[1];
        }).catch(function () { });
    }

    function linkedMap() {
        var map = new Map();
        S.links.forEach(function (link) {
            if (!map.has(link.source)) { map.set(link.source, []); }
            map.get(link.source).push(link.target);
        });
        return map;
    }

    function tagById(id) {
        return S.tags.find(function (tag) { return tag.id === id; });
    }

    var tagsChangedPending = false;

    function tagsChanged() {
        refreshTags();
        if (S.ready) {
            S.tagIds = S.tagIds.filter(function (id) { return S.tags.some(function (tag) { return tag.id === id; }); });
            updateTagButton();
            debouncedBrowse();
        }
    }

    function updateTagButton() {
        $('tb-tags-label').textContent = S.tagIds.length === 0 ? t('Tags') : t('Tags') + ' (' + S.tagIds.length + ')';
    }

    function openTagManager(anchorRect) {
        var body = el('div');
        var addRow = el('div', 'grow-row');
        var newTag = el('input');
        newTag.type = 'text';
        newTag.placeholder = t('New tag name');
        var addButton = el('button', 'primary', t('Add'));
        addRow.appendChild(newTag);
        addRow.appendChild(addButton);
        body.appendChild(addRow);

        var search = el('input');
        search.type = 'search';
        search.placeholder = t('Search tags');
        search.style.cssText = 'width:100%;margin-top:8px';
        body.appendChild(search);

        var table = el('table');
        table.style.marginTop = '8px';
        var head = el('tr');
        ['Tag', 'Folders'].forEach(function (key) { head.appendChild(el('th', null, t(key))); });
        table.appendChild(head);
        var tbody = el('tbody');
        table.appendChild(tbody);
        body.appendChild(table);

        var buttons = el('div', 'grow-row');
        buttons.style.marginTop = '10px';
        var renameButton = el('button', null, t('Rename'));
        var deleteButton = el('button', null, t('Delete'));
        var replaceButton = el('button', null, t('Replace…'));
        var linkButton = el('button', null, t('Links…'));
        [renameButton, deleteButton, replaceButton, linkButton].forEach(function (b) { buttons.appendChild(b); });
        body.appendChild(buttons);
        var status = el('div', 'muted');
        body.appendChild(status);
        var error = el('div', 'error-text');
        body.appendChild(error);

        var picked = new Set();
        var changed = false;

        function rows() {
            var query = search.value.trim().toLowerCase();
            return S.tags.filter(function (tag) { return !query || tag.name.toLowerCase().indexOf(query) >= 0; });
        }

        function refill() {
            clear(tbody);
            rows().forEach(function (tag) {
                var row = el('tr', 'pick' + (picked.has(tag.id) ? ' on' : ''));
                var nameCell = el('td');
                var dot = el('span', 'tag-dot');
                dot.style.cssText = 'display:inline-block;width:10px;height:10px;border-radius:99px;background:' + tagColor(tag.name) + ';margin-right:8px';
                nameCell.appendChild(dot);
                nameCell.appendChild(document.createTextNode(tag.name));
                row.appendChild(nameCell);
                row.appendChild(el('td', null, String(tag.folderCount)));
                row.addEventListener('click', function () {
                    if (picked.has(tag.id)) { picked.delete(tag.id); } else { picked.add(tag.id); }
                    row.classList.toggle('on');
                    sync();
                });
                tbody.appendChild(row);
            });
            sync();
        }

        function sync() {
            status.textContent = picked.size === 0 ? t('{0} tags', [S.tags.length]) : t('{0} selected', [picked.size]);
            renameButton.disabled = picked.size !== 1;
            deleteButton.disabled = picked.size === 0;
            linkButton.disabled = picked.size !== 1;
        }

        newTag.addEventListener('keydown', function (event) { if (event.key === 'Enter') { add(); } });
        addButton.addEventListener('click', add);

        function add() {
            var name = newTag.value.trim();
            if (!name) { return; }
            call('freja:tag.add', name).then(function () {
                newTag.value = '';
                error.textContent = '';
                changed = true;
                return refreshTags().then(refill);
            }).catch(function (e) { error.textContent = e.message; });
        }

        renameButton.addEventListener('click', function () {
            var id = Array.from(picked)[0];
            var tag = tagById(id);
            if (!tag) { return; }
            inputDialog(t('Rename tag'), tag.name).then(function (name) {
                if (name === null || !name.trim()) { return; }
                call('freja:tag.rename', id, name.trim()).then(function () {
                    changed = true;
                    picked.clear();
                    refreshTags().then(refill);
                }).catch(function (e) { error.textContent = e.message; });
            });
        });

        deleteButton.addEventListener('click', function () {
            confirmDialog(t('Delete tags'), t('Remove {0} tags from every folder? The photos are not deleted.', [picked.size]), t('Delete'), true).then(function (ok) {
                if (!ok) { return; }
                call('freja:tag.delete', { ids: Array.from(picked) }).then(function () {
                    changed = true;
                    picked.clear();
                    refreshTags().then(refill);
                }).catch(function () { });
            });
        });

        replaceButton.addEventListener('click', function () {
            var from = '';
            inputDialog(t('Find which tag name'), '').then(function (first) {
                if (first === null || !first.trim()) { return; }
                from = first.trim();
                inputDialog(t('Replace with'), '').then(function (second) {
                    if (second === null || !second.trim()) { return; }
                    var ids = S.tags.filter(function (tag) { return tag.name.toLowerCase().indexOf(from.toLowerCase()) >= 0; }).map(function (tag) { return tag.id; });
                    call('freja:tag.bulkRename', { ids: ids, from: from, to: second.trim() }).then(function (count) {
                        changed = true;
                        error.textContent = '';
                        status.textContent = t('{0} tags renamed', [count]);
                        picked.clear();
                        refreshTags().then(refill);
                    }).catch(function (e) { error.textContent = e.message; });
                });
            });
        });

        linkButton.addEventListener('click', function () {
            var id = Array.from(picked)[0];
            var tag = tagById(id);
            if (!tag) { return; }
            var map = linkedMap();
            var linked = new Set(map.get(id) || []);
            var list = el('div');
            list.style.cssText = 'max-height:300px;overflow:auto';
            var others = S.tags.filter(function (candidate) { return candidate.id !== id; });
            if (others.length === 0) { list.appendChild(el('div', 'muted', t('No other tags yet'))); }
            others.forEach(function (candidate) {
                var row = el('div', 'row');
                var label = el('label');
                var box = el('input');
                box.type = 'checkbox';
                box.checked = linked.has(candidate.id);
                label.appendChild(box);
                var dot = el('span', 'tag-dot');
                dot.style.cssText = 'display:inline-block;width:10px;height:10px;border-radius:99px;background:' + tagColor(candidate.name);
                label.appendChild(dot);
                label.appendChild(el('span', null, candidate.name));
                row.appendChild(label);
                row.appendChild(el('span', 'grow'));
                list.appendChild(row);
                box.addEventListener('change', function () {
                    if (box.checked) { linked.add(candidate.id); } else { linked.delete(candidate.id); }
                    call('freja:tag.setLinks', { id: id, targets: Array.from(linked) }).then(function () {
                        changed = true;
                        refreshTags();
                    }).catch(function () { });
                });
            });
            modal({ title: t('Edit tag links') + ' — #' + tag.name, node: list, buttons: [{ label: t('Close'), value: true, primary: true }] })
                .then(function () { refill(); });
        });

        search.addEventListener('input', refill);
        refill();
        modal({ title: t('Manage tag names and links'), node: body, wide: false, sticky: false, buttons: [{ label: t('Close'), value: true, primary: true }] })
            .then(function () { if (changed) { tagsChanged(); } });
    }

    function openTagFilter(anchorRect) {
        var node = el('div');
        node.style.width = '320px';
        node.appendChild(el('h3', null, t('Filter by tags')));
        var search = el('input');
        search.type = 'search';
        search.placeholder = t('Search tags');
        node.appendChild(search);
        var list = el('div', 'list');
        node.appendChild(list);
        var foot = el('div', 'foot');
        var clearButton = el('button', null, t('Clear selection'));
        foot.appendChild(clearButton);
        node.appendChild(foot);

        function refill() {
            clear(list);
            var query = search.value.trim().toLowerCase();
            var shown = 0;
            S.tags.forEach(function (tag) {
                if (query && tag.name.toLowerCase().indexOf(query) < 0) { return; }
                shown++;
                var row = el('div', 'row');
                var label = el('label');
                var box = el('input');
                box.type = 'checkbox';
                box.checked = S.tagIds.indexOf(tag.id) >= 0;
                label.appendChild(box);
                var dot = el('span', 'tag-dot');
                dot.style.cssText = 'display:inline-block;width:10px;height:10px;border-radius:99px;background:' + tagColor(tag.name);
                label.appendChild(dot);
                label.appendChild(el('span', null, tag.name));
                row.appendChild(label);
                row.appendChild(el('span', 'count', tag.folderCount + ' ' + t('folders')));
                list.appendChild(row);
                box.addEventListener('change', function () {
                    var index = S.tagIds.indexOf(tag.id);
                    if (box.checked && index < 0) { S.tagIds.push(tag.id); }
                    if (!box.checked && index >= 0) { S.tagIds.splice(index, 1); }
                    updateTagButton();
                    browse();
                });
            });
            if (shown === 0) { list.appendChild(el('div', 'empty-note', t('No tags match'))); }
            clearButton.disabled = S.tagIds.length === 0;
        }

        clearButton.addEventListener('click', function () {
            S.tagIds = [];
            updateTagButton();
            browse();
            closeFloating();
        });

        search.addEventListener('input', refill);
        refill();
        showPopover(anchorRect, node);
        search.focus();
    }

    function openTagPicker(folderId, x, y) {
        var folder = findFolder(folderId) || S.folders.find(function (f) { return f.id === folderId; });
        if (!folder) { return; }
        var node = el('div');
        node.style.width = '320px';
        node.appendChild(el('h3', null, t('Edit tags for {0}', [folder.name])));
        var search = el('input');
        search.type = 'search';
        search.placeholder = t('Search or create labels…');
        node.appendChild(search);
        var list = el('div', 'list');
        node.appendChild(list);
        var hint = el('div', 'muted');
        hint.textContent = t('Tags live on folders; every photo inherits the tags of its folder.');
        node.appendChild(hint);

        var selected = new Set(folder.tags);

        function applyNow() {
            call('freja:tag.setFolderTags', { folderId: folderId, names: Array.from(selected) }).then(function () {
                tagsChanged();
            }).catch(function () { });
        }

        function refill() {
            clear(list);
            var query = search.value.trim().toLowerCase();
            var map = linkedMap();
            var byId = new Map();
            S.tags.forEach(function (tag) {
                byId.set(tag.id, tag);
                if (query && tag.name.toLowerCase().indexOf(query) < 0) { return; }
                var row = el('div', 'row');
                var label = el('label');
                var box = el('input');
                box.type = 'checkbox';
                box.checked = selected.has(tag.name);
                label.appendChild(box);
                var dot = el('span', 'tag-dot');
                dot.style.cssText = 'display:inline-block;width:10px;height:10px;border-radius:99px;background:' + tagColor(tag.name);
                label.appendChild(dot);
                label.appendChild(el('span', null, tag.name));
                row.appendChild(label);
                var linked = (map.get(tag.id) || []).map(function (id) { var other = byId.get(id); return other ? other.name : ''; }).filter(Boolean);
                if (linked.length) { row.appendChild(el('span', 'linked', '→ ' + linked.join(', '))); }
                var x2 = el('span', 'x', '×');
                x2.title = t('Delete this tag');
                x2.addEventListener('click', function () {
                    confirmDialog(t('Delete tag'), t('Delete tag {0} from every folder? The photos are not deleted.', [tag.name]), t('Delete'), true).then(function (ok) {
                        if (!ok) { return; }
                        call('freja:tag.delete', { ids: [tag.id] }).then(function () {
                            selected.delete(tag.name);
                            refreshTags().then(refill);
                            tagsChanged();
                        }).catch(function () { });
                    });
                });
                row.appendChild(x2);
                list.appendChild(row);
                box.addEventListener('change', function () {
                    if (box.checked) { selected.add(tag.name); } else { selected.delete(tag.name); }
                    applyNow();
                });
            });
            var exact = S.tags.some(function (tag) { return tag.name.toLowerCase() === query; });
            if (query && !exact) {
                var create = el('div', 'row create', '＋  ' + search.value.trim() + '  ·  ' + t('Create new label'));
                create.addEventListener('click', createTag);
                list.appendChild(create);
            }
            if (S.tags.length === 0) { list.appendChild(el('div', 'empty-note', t('No tags yet'))); }
        }

        function createTag() {
            var name = search.value.trim();
            if (!name) { return; }
            call('freja:tag.add', name).then(function () {
                search.value = '';
                selected.add(name);
                return refreshTags().then(function () { applyNow(); refill(); });
            }).catch(function () { });
        }

        search.addEventListener('input', refill);
        search.addEventListener('keydown', function (event) { if (event.key === 'Enter') { createTag(); } });
        refill();
        var rect = { left: x, top: y, bottom: y, right: x + 1 };
        showPopover(rect, node);
        search.focus();
    }

    function openAbout() {
        var stats = S.stats || { photos: 0, favorites: 0, recent: 0, folders: 0 };
        var lines = [
            'Freja ' + (apocrypha.app.version || ''),
            t('A fast, local-first photo library for the desktop.'),
            '',
            t('Photos: {0}', [stats.photos]),
            t('Favorites: {0}', [stats.favorites]),
            t('Recent: {0}', [stats.recent]),
            t('Folders: {0}', [stats.folders]),
            t('Library: {0}', [S.library]),
            '',
            t('Browser edition (Apocrypha {0})', [apocrypha.app.frameworkVersion || '']),
            t('Java {0}', [apocrypha.app.java || '']),
            apocrypha.app.platform + ' · ' + apocrypha.app.arch + ' · ' + apocrypha.app.webview
        ].join('\n');
        modal({ title: t('About Freja'), node: textNode(lines), buttons: [{ label: t('OK'), value: true, primary: true }] });
    }

    /* ==================================================== toolbar glue ==== */

    $('tb-add').addEventListener('click', openFolderManager);
    $('welcome-add').addEventListener('click', openFolderManager);
    $('add-folder').addEventListener('click', openFolderManager);
    $('manage-tags').addEventListener('click', openTagManager);
    $('tb-rescan').addEventListener('click', rescanAll);
    $('tb-slideshow').addEventListener('click', startSlideshow);
    $('tb-tags').addEventListener('click', function (event) {
        event.stopPropagation();
        openTagFilter(event.currentTarget.getBoundingClientRect());
    });

    function rescanAll() {
        call('freja:scan.all').then(function () { setStatus(t('Rescan started')); }).catch(function () { });
    }

    function startSlideshow() {
        if (S.photos.length === 0) {
            setStatus(t('No photos for a slideshow'));
            return;
        }
        var start = S.anchor || S.photos[0];
        openViewer(start);
        if (!V.timer) { toggleSlideshow(); }
    }

    var searchDebounced = debounce(function () { browse(); }, 280);

    $('search').addEventListener('input', function () {
        S.search = $('search').value;
        $('search').parentElement.classList.toggle('dirty', S.search.length > 0);
        searchDebounced();
    });

    $('search-clear').addEventListener('click', function () {
        $('search').value = '';
        S.search = '';
        $('search').parentElement.classList.remove('dirty');
        browse();
    });

    $('sort').addEventListener('change', function () {
        var order = $('sort').value;
        if (S.mode === 'FOLDER' && S.folderId) {
            call('freja:folder.sort', S.folderId, order).catch(function () { });
        } else {
            S.sort = order;
            setSetting('sort', order);
        }
        S.sort = order;
        browse();
    });

    var zoomSaved = debounce(saveSettings, 250);

    function applyThumb(px) {
        S.thumb = px;
        $('grid').style.setProperty('--thumb', px + 'px');
        $('zoom').value = px;
        $('set-thumb').value = px;
        $('set-thumb-value').textContent = px + ' px';
        $('zoom').title = t('Thumbnail size') + ': ' + px + ' px';
        scheduleVirtualUpdate(true);
    }

    $('zoom').addEventListener('input', function () {
        applyThumb(parseInt($('zoom').value, 10));
        setSetting('thumbSize', String(S.thumb));
    });

    $('set-thumb').addEventListener('input', function () {
        applyThumb(parseInt($('set-thumb').value, 10));
        setSetting('thumbSize', String(S.thumb));
    });

    $('tb-theme').addEventListener('click', toggleTheme);

    function toggleTheme() {
        S.theme = S.theme === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = S.theme;
        $('tb-theme').textContent = S.theme === 'dark' ? '☀' : '☾';
        setSetting('theme', S.theme);
        syncSettingsPage();
    }

    function applyInspector() {
        $('inspector').hidden = !S.inspector;
        $('split-inspector').hidden = !S.inspector;
    }

    function toggleInspector() {
        S.inspector = !S.inspector;
        setSetting('inspectorVisible', String(S.inspector));
        applyInspector();
    }

    $('manage-tags').addEventListener('click', function (event) { event.stopPropagation(); openTagManager(); });

    /* ===================================================== pages / splitters ==== */

    function showPage(page) {
        S.page = page;
        $('page-viewer').classList.toggle('active', page === 'viewer');
        $('page-settings').classList.toggle('active', page === 'settings');
        $('page-photos').classList.toggle('selected', page === 'viewer');
        $('page-settings-btn').classList.toggle('selected', page === 'settings');
        if (page === 'settings') { syncSettingsPage(); }
        else { scheduleVirtualUpdate(true); }
    }

    $('page-photos').addEventListener('click', function () { showPage('viewer'); });
    $('page-settings-btn').addEventListener('click', function () { showPage('settings'); });

    function splitter(handle, target, min, max, widthKey, from) {
        handle.addEventListener('mousedown', function (event) {
            event.preventDefault();
            var start = event.clientX;
            var startWidth = target.getBoundingClientRect().width;
            function move(e) {
                var delta = (from === 'right' ? start - e.clientX : e.clientX - start);
                var next = Math.max(min, Math.min(max, startWidth + delta));
                target.style.width = next + 'px';
            }
            function up() {
                window.removeEventListener('mousemove', move);
                window.removeEventListener('mouseup', up);
                setSetting(widthKey, Math.round(target.getBoundingClientRect().width));
            }
            window.addEventListener('mousemove', move);
            window.addEventListener('mouseup', up);
        });
    }

    splitter($('split-sidebar'), $('sidebar'), 180, 460, 'sidebarWidth', 'left');
    splitter($('split-inspector'), $('inspector'), 240, 520, 'inspectorWidth', 'right');

    /** Restores one persisted divider position, clamped to the same range as the drag handler. */
    function restoreSplitter(target, key, min, max) {
        var raw = S.settings[key];
        if (raw === undefined || raw === null || raw === '') { return; }
        var width = parseInt(raw, 10);
        if (!isFinite(width)) { return; }
        target.style.width = Math.max(min, Math.min(max, width)) + 'px';
    }

    function applySplitters() {
        restoreSplitter($('sidebar'), 'sidebarWidth', 180, 460);
        restoreSplitter($('inspector'), 'inspectorWidth', 240, 520);
    }

    /* settings page bindings */

    $('set-theme').addEventListener('change', function () {
        S.theme = $('set-theme').value;
        document.documentElement.dataset.theme = S.theme;
        $('tb-theme').textContent = S.theme === 'dark' ? '☀' : '☾';
        setSetting('theme', S.theme);
    });

    $('set-scan-start').addEventListener('change', function () {
        setSetting('scanOnStartup', String($('set-scan-start').checked));
    });

    $('set-sweep').addEventListener('change', function () {
        setSetting('sweep', String($('set-sweep').checked));
    });

    $('set-manage-folders').addEventListener('click', openFolderManager);
    $('set-about').addEventListener('click', openAbout);
    $('set-restart').addEventListener('click', function () { apocrypha.app.restart(); });

    (function buildLanguageSelect() {
        var select = $('set-language');
        Object.keys(LANG_NAMES).forEach(function (code) {
            var option = el('option', null, LANG_NAMES[code]);
            option.value = code;
            select.appendChild(option);
        });
        select.addEventListener('change', function () {
            LANG = select.value;
            setSetting('language', LANG);
            saveSettings();
            applyChrome();
            browse().then(function () { setStatus(t('Language changed')); });
        });
    })();

    function syncSettingsPage() {
        $('set-theme').value = S.theme;
        $('set-scan-start').checked = S.settings.scanOnStartup === 'true';
        $('set-sweep').checked = S.settings.sweep === 'true';
        $('set-thumb').value = S.thumb;
        $('set-thumb-value').textContent = S.thumb + ' px';
        $('set-language').value = LANG;
        $('settings-library-path').textContent = t('Library: {0}', [S.library || '—']);
    }

    /* ====================================================== keyboard ==== */

    document.addEventListener('keydown', function (event) {
        if (V.open) {
            if (event.key === 'Escape') { closeViewer(); event.preventDefault(); }
            else if (event.key === 'ArrowLeft') { viewerMove(-1); }
            else if (event.key === 'ArrowRight') { viewerMove(1); }
            else if (event.key === ' ') { toggleSlideshow(); event.preventDefault(); }
            else if (event.key === '+' || event.key === '=') { zoomBy(1.18); }
            else if (event.key === '-' || event.key === '_') { zoomBy(1 / 1.18); }
            else if (event.key === '0' || event.key === 'f' || event.key === 'F') { setFit(); }
            else if (event.key === 'F11') { toggleFullscreen(); event.preventDefault(); }
            return;
        }
        var typing = event.target && (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA');
        if (event.key === 'Escape' && !typing) {
            closeFloating();
            return;
        }
        if (event.key === 'Escape' && typing) { event.target.blur(); return; }
        if (typing) { return; }
        if (event.ctrlKey || event.metaKey) {
            switch (event.key) {
            case 'o': case 'O': openFolderManager(); event.preventDefault(); return;
            case ',': showPage('settings'); event.preventDefault(); return;
            case 'q': case 'Q': apocrypha.app.quit(); event.preventDefault(); return;
            case 'f': case 'F': $('search').focus(); event.preventDefault(); return;
            case 't': case 'T': openTagManager(); event.preventDefault(); return;
            case 'i': case 'I': toggleInspector(); event.preventDefault(); return;
            case '1': selectMode('ALL', null); event.preventDefault(); return;
            case '2': selectMode('FAVORITES', null); event.preventDefault(); return;
            case '3': selectMode('RECENT', null); event.preventDefault(); return;
            default:
                if (event.shiftKey && (event.key === 'D' || event.key === 'd')) { toggleTheme(); event.preventDefault(); }
                return;
            }
        }
        if (event.key === 'F5') { rescanAll(); event.preventDefault(); }
        if (event.key === 'F11') { startSlideshow(); event.preventDefault(); }
        if (event.key === 'Delete' && S.selected.size > 0) {
            removeFromLibrary(selectedPhotos().map(function (p) { return p.id; }), selectedPhotos());
        }
    });

    /* =========================================================== boot ==== */

    applyChrome();
    setStatus(t('Connecting to the library…'));
    updateTagButton();
    applyThumb(S.thumb);
    applyInspector();
    $('brand-version').textContent = apocrypha.app.version || '';
    $('viewer-help').textContent = t('← → or scroll to move   ·   drag to pan   ·   +/- to zoom   ·   0 or F to fit   ·   Space for slideshow   ·   F11 for fullscreen   ·   Esc to close');

    document.addEventListener('contextmenu', function (event) {
        // allow the native menu on inputs so text fields keep cut / copy / paste
        if (event.target && (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA')) { return; }
        event.preventDefault();
    });

    window.addEventListener('error', function (event) {
        console.error('[freja]', event.message);
    });
})();
