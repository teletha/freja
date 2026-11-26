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
 * Entry point of the Svelte part of the browser UI.
 *
 * The vanilla renderer in app.js stays in charge of the catalog, the grid and the IPC channels.
 * It publishes a small snapshot and a set of actions on window.frejaSvelteBridge; the Svelte
 * components consume that bridge so both halves share one source of truth.
 */
import { mount } from 'svelte';
import Settings from './Settings.svelte';

const target = document.getElementById('svelte-settings');

if (target && window.frejaSvelteBridge) {
    mount(Settings, { target, props: { bridge: window.frejaSvelteBridge } });
} else {
    console.warn('[freja] Svelte mount point or bridge is missing');
}
