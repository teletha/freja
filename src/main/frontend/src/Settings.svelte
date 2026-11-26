<!--
  Copyright (C) 2026 The FREJA Development Team

  Licensed under the MIT License (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

           https://opensource.org/licenses/MIT
-->
<script>
    import { onMount } from 'svelte';

    /** The bridge published by the vanilla renderer in app.js. */
    let { bridge } = $props();

    /** Snapshot of the settings owned by app.js. Replaced whenever app.js publishes a change. */
    let state = $state(bridge.state());

    onMount(() => bridge.subscribe(next => { state = next; }));

    const actions = bridge.actions;
    const t = (key, args) => actions.translate(key, args);
    const languages = $derived(Object.keys(state.languages || {}));
</script>

<div class="settings-body scroll-y">
    <h1>{t('Settings')}</h1>

    <h2>{t('Appearance')}</h2>
    <p class="settings-note">{t('Theme, colors and text live here.')}</p>
    <div class="setting-row">
        <label for="svelte-theme">{t('Theme')}</label>
        <select id="svelte-theme" value={state.theme} onchange={event => actions.setTheme(event.currentTarget.value)}>
            <option value="dark">{t('Dark')}</option>
            <option value="light">{t('Light')}</option>
        </select>
    </div>

    <h2>{t('Library')}</h2>
    <p class="settings-note">{t('Scanning and watched folders.')}</p>
    <div class="setting-row">
        <label for="svelte-scan-start">{t('Scan on startup')}</label>
        <input id="svelte-scan-start" type="checkbox" checked={state.scanOnStartup}
               onchange={event => actions.setScanOnStartup(event.currentTarget.checked)}>
    </div>
    <div class="setting-row">
        <label for="svelte-sweep">{t('Periodic sweep')}</label>
        <input id="svelte-sweep" type="checkbox" checked={state.sweep}
               onchange={event => actions.setSweep(event.currentTarget.checked)}>
    </div>
    <div class="setting-row">
        <label>{t('Watched folders')}</label>
        <button onclick={actions.manageFolders}>{t('Manage watched folders…')}</button>
    </div>

    <h2>{t('Grid')}</h2>
    <p class="settings-note">{t('Thumbnail size for the photo wall.')}</p>
    <div class="setting-row">
        <label for="svelte-thumb">{t('Thumbnail size')}</label>
        <input id="svelte-thumb" type="range" min="120" max="220" step="10" value={state.thumb}
               oninput={event => actions.setThumb(Number(event.currentTarget.value))}>
        <span class="thumb-value">{state.thumb} px</span>
    </div>

    <h2>{t('Language')}</h2>
    <p class="settings-note">{t('Menus and messages are shown in the selected language.')}</p>
    <div class="setting-row">
        <label for="svelte-language">{t('Display language')}</label>
        <select id="svelte-language" value={state.language} onchange={event => actions.setLanguage(event.currentTarget.value)}>
            {#each languages as code (code)}
                <option value={code}>{state.languages[code]}</option>
            {/each}
        </select>
    </div>

    <h2>{t('System')}</h2>
    <p class="settings-note">{t('Application information and restart.')}</p>
    <div class="setting-row">
        <label></label>
        <button onclick={actions.about}>{t('About Freja')}</button>
        <button onclick={actions.restart}>{t('Restart')}</button>
    </div>
    <div class="settings-note">{t('Library: {0}', [state.library || '—'])}</div>
</div>

<style>
    .settings-body { min-height: 100%; }
    .thumb-value { color: var(--muted); font-size: 11px; white-space: nowrap; }
</style>
