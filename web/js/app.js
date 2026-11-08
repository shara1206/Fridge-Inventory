import * as repo from './repo.js';
import { encode, decode } from './backup.js';
import { parseReceipt } from './parsers.js';
import { guess } from './shelf-life.js';
import { ReceiptTextExport } from './receipt-text.js';
import { CARDS_PER_PAGE, labelLine, labelSheet } from './labels.js';
import {
  CATEGORIES, DUE_SOON_DAYS, STORAGE_AREAS, STORES, addDays, addMonths,
  daysLeft, formatMoney, formatQuantity, today,
} from './model.js';
import { t, setLanguage, lang } from './i18n.js';
import {
  loadState, saveState, loadPrefs, savePrefs, requestPersistence,
} from './storage.js';

// ---- state -------------------------------------------------------------------

let state = repo.emptyState();
let prefs = { lang: 'auto' };
let storageOk = true;
let persistent = false;

const ui = {
  query: '',
  area: null,
  category: null,
  store: null,
  historyFilter: 'ALL',
  scan: null, // { text, receipt, lines: [{ line, name, keep, storageArea }] }
  form: null, // the item being edited
  snack: null,
};

const app = document.getElementById('app');

/**
 * Applies a change to a copy of the kitchen and saves the copy in one write, so storage
 * never holds half a change. If the browser refuses to store anything (private browsing),
 * the app keeps working in memory and shows a warning rather than dropping the change.
 */
async function commit(change) {
  const draft = structuredClone(state);
  const result = change(draft);
  try {
    await saveState(draft);
  } catch (e) {
    storageOk = false;
    console.error(e);
  }
  state = draft;
  render();
  return result;
}

// ---- small helpers -------------------------------------------------------------

const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

const ICONS = {
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  trash: '<path d="M4 7h16M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13M10 11v5.5M14 11v5.5"/>',
  minus: '<path d="M6 12h12"/>',
  plus: '<path d="M12 6v12M6 12h12"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  history: '<path d="M4 12a8 8 0 1 0 2.4-5.7M4 4.5V8h3.5M12 8v4.5l3 2"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8"/>',
  receipt: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3"/>',
  undo: '<path d="M9 7L4 12l5 5M4 12h10a6 6 0 0 1 0 12"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
  tag: '<path d="M3.5 12.5V4.5a1 1 0 0 1 1-1h8l8 8-9 9z"/><circle cx="8" cy="8" r="1.5"/>',
  print: '<path d="M7 9V3.5h10V9M7 17H4.5V10h15v7H17M7 14h10v6.5H7z"/>',
};

const icon = (name) => `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name]}</svg>`;

function iconButton(name, action, label, extra = '') {
  return `<button class="icon-btn" type="button" data-action="${action}" ${extra}
    aria-label="${esc(label)}" title="${esc(label)}">${icon(name)}</button>`;
}

function chip(label, selected, action, value = '') {
  return `<button type="button" class="chip${selected ? ' selected' : ''}" data-action="${action}"
    data-value="${esc(value)}" aria-pressed="${selected}">${esc(label)}</button>`;
}

function formatDate(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(lang() === 'zh' ? 'zh-CN' : 'en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  });
}

function go(hash) {
  if (location.hash === hash) render();
  else location.hash = hash;
}

function showSnack(message, undoEventId = null) {
  ui.snack = { message, undoEventId, key: Date.now() };
  renderSnack();
  const key = ui.snack.key;
  setTimeout(() => {
    if (ui.snack?.key === key) {
      ui.snack = null;
      renderSnack();
    }
  }, 5000);
}

function renderSnack() {
  const host = document.getElementById('snack');
  if (!ui.snack) {
    host.innerHTML = '';
    return;
  }
  host.innerHTML = `<div class="snack" role="status">
    <span>${esc(ui.snack.message)}</span>
    ${ui.snack.undoEventId != null
    ? `<button type="button" data-action="snack-undo">${esc(t('undo'))}</button>` : ''}
  </div>`;
}

// ---- inventory ---------------------------------------------------------------

function expiryChip(item) {
  const left = daysLeft(item);
  if (left == null || left > DUE_SOON_DAYS) return '';
  if (left < 0) return `<span class="badge expired">${esc(t('expiry_over', -left))}</span>`;
  const text = left === 0 ? t('expiry_today') : t('expiry_days', left);
  return `<span class="badge soon">${esc(text)}</span>`;
}

function backupBanner() {
  const reminder = repo.backupReminder(state);
  if (!reminder) return '';
  const last = reminder.lastBackupOn;
  const text = last ? t('backup_nag', formatDate(last)) : t('backup_never');
  return `<div class="banner"><span>${esc(text)}</span>
    <button type="button" class="text-btn" data-action="nav" data-value="#/settings">
      ${esc(t('backup_now'))}</button></div>`;
}

function renderInventory() {
  const all = repo.activeItems(state);
  const now = today();
  const q = ui.query.trim().toLowerCase();
  const items = all.filter((i) =>
    (!ui.area || i.storageArea === ui.area) &&
    (!ui.category || i.category === ui.category) &&
    (!ui.store || i.store === ui.store) &&
    (!q || i.name.toLowerCase().includes(q)));
  const expired = all.filter((i) => (daysLeft(i, now) ?? Infinity) < 0).length;
  const soon = all.filter((i) => {
    const d = daysLeft(i, now);
    return d != null && d >= 0 && d <= DUE_SOON_DAYS;
  }).length;
  const summary = [t('summary_items', items.length)];
  if (expired) summary.push(t('summary_expired', expired));
  if (soon) summary.push(t('summary_due', soon));
  const presentCategories = CATEGORIES.filter((c) => all.some((i) => i.category === c));
  // Same rule as Android: the store row only appears once there are two stores to pick from.
  const presentStores = Object.keys(STORES).filter((s) => all.some((i) => i.store === s));

  const rows = items.map((i) => `
    <li class="row">
      <button type="button" class="row-main" data-action="open" data-value="${i.id}">
        <span class="row-name">${esc(i.name)}</span>
        <span class="row-meta">${esc([t(`cat_${i.category}`),
          i.store !== 'OTHER' ? STORES[i.store] : null].filter(Boolean).join(' · '))}</span>
      </button>
      ${expiryChip(i)}
      <div class="row-finish">
        ${iconButton('check', 'finish-used', t('mark_used'), `data-value="${i.id}"`)}
        ${iconButton('trash', 'finish-discarded', t('mark_discarded'), `data-value="${i.id}"`)}
      </div>
      <div class="stepper">
        ${iconButton('minus', 'use-one', t('qty_decrease'), `data-value="${i.id}"`)}
        <span class="qty">${esc(formatQuantity(i.quantity))}</span>
        ${iconButton('plus', 'buy-another', t('qty_increase'), `data-value="${i.id}"`)}
      </div>
    </li>`).join('');

  return `
    <header class="bar">
      <div class="bar-title">
        <h1>${esc(t('app_name'))}</h1>
        <p class="sub">${esc(summary.join(' · '))}</p>
      </div>
      ${iconButton('tag', 'nav', t('labels'), 'data-value="#/labels"')}
      ${iconButton('history', 'nav', t('history'), 'data-value="#/history"')}
      ${iconButton('settings', 'nav', t('settings'), 'data-value="#/settings"')}
    </header>
    <main class="page">
      ${storageOk ? '' : `<div class="banner warn">${esc(t('storage_error'))}</div>`}
      ${backupBanner()}
      <label class="search">${icon('search')}
        <input type="search" id="search" placeholder="${esc(t('search'))}"
          value="${esc(ui.query)}" autocomplete="off" enterkeyhint="search">
      </label>
      <div class="chips" role="group">
        ${chip(t('filter_all'), !ui.area, 'area', '')}
        ${STORAGE_AREAS.map((a) => chip(t(`area_${a}`), ui.area === a, 'area', a)).join('')}
      </div>
      ${presentCategories.length > 1 ? `<div class="chips scroll" role="group">
        ${chip(t('filter_all'), !ui.category, 'category', '')}
        ${presentCategories.map((c) => chip(t(`cat_${c}`), ui.category === c, 'category', c))
    .join('')}
      </div>` : ''}
      ${presentStores.length > 1 ? `<div class="chips scroll" role="group">
        ${chip(t('filter_all'), !ui.store, 'store', '')}
        ${presentStores.map((s) => chip(STORES[s], ui.store === s, 'store', s)).join('')}
      </div>` : ''}
      ${items.length ? `<ul class="list">${rows}</ul>`
    : `<p class="empty">${esc(all.length ? t('no_match') : t('empty'))}</p>`}
    </main>
    <nav class="actions">
      <button type="button" class="btn secondary" data-action="nav" data-value="#/scan">
        ${icon('receipt')}${esc(t('scan'))}</button>
      <button type="button" class="btn primary" data-action="nav" data-value="#/edit/0">
        ${icon('plus')}${esc(t('add_manual'))}</button>
    </nav>`;
}

// ---- edit --------------------------------------------------------------------

function formFor(id) {
  const existing = id ? repo.item(state, id) : null;
  if (existing) {
    return {
      ...existing,
      quantityText: formatQuantity(existing.quantity),
      priceText: existing.priceCents != null ? formatMoney(existing.priceCents) : '',
    };
  }
  return { ...repo.draftFor(''), quantityText: '1', priceText: '' };
}

function formErrors(f) {
  const qty = Number(f.quantityText);
  const price = f.priceText.trim() === '' ? 0 : Number(f.priceText);
  return {
    quantity: f.quantityText.trim() !== '' && !(qty > 0),
    price: !(price >= 0),
    canSave: f.name.trim() !== '' && qty > 0 && price >= 0,
  };
}

function select(field, options, value, labelOf) {
  return `<select data-field="${field}">${options.map((o) =>
    `<option value="${esc(o)}"${o === value ? ' selected' : ''}>${esc(labelOf(o))}</option>`)
    .join('')}</select>`;
}

function renderEdit() {
  const f = ui.form;
  const err = formErrors(f);
  const isNew = !f.id;
  return `
    <header class="bar">
      ${iconButton('back', 'nav', t('back'), 'data-value="#/"')}
      <div class="bar-title"><h1>${esc(isNew ? t('add_item') : t('edit_item'))}</h1></div>
    </header>
    <main class="page form">
      <label class="field"><span>${esc(t('name'))}</span>
        <input data-field="name" value="${esc(f.name)}" autocomplete="off"
          ${isNew ? 'autofocus' : ''}></label>
      <button type="button" class="text-btn" data-action="guess">${esc(t('guess'))}</button>

      <fieldset class="field"><legend>${esc(t('where'))}</legend>
        <div class="chips">${STORAGE_AREAS.map((a) =>
    chip(t(`area_${a}`), f.storageArea === a, 'form-area', a)).join('')}</div>
      </fieldset>

      <div class="two">
        <label class="field"><span>${esc(t('category'))}</span>
          ${select('category', CATEGORIES, f.category, (c) => t(`cat_${c}`))}</label>
        <label class="field"><span>${esc(t('store'))}</span>
          ${select('store', Object.keys(STORES), f.store, (s) => STORES[s])}</label>
      </div>

      <div class="two">
        <div class="field"><span>${esc(t('quantity'))}</span>
          <div class="stepper wide">
            ${iconButton('minus', 'form-step', '−1', 'data-value="-1"')}
            <input data-field="quantityText" inputmode="decimal" value="${esc(f.quantityText)}"
              class="${err.quantity ? 'invalid' : ''}" aria-label="${esc(t('quantity'))}">
            ${iconButton('plus', 'form-step', '+1', 'data-value="1"')}
          </div></div>
        <label class="field"><span>${esc(t('price'))}</span>
          <input data-field="priceText" inputmode="decimal" placeholder="0.00"
            value="${esc(f.priceText)}" class="${err.price ? 'invalid' : ''}"></label>
      </div>

      ${isNew ? '' : `<fieldset class="field"><legend>${esc(t('use_some'))}</legend>
        <div class="chips">${['0.25', '0.5', '1'].map((a) =>
    chip(`− ${a}`, false, 'use-some', a)).join('')}</div></fieldset>`}

      <div class="two">
        <label class="field"><span>${esc(t('bought'))}</span>
          <input type="date" data-field="purchasedOn" value="${esc(f.purchasedOn)}"></label>
        <label class="field"><span>${esc(t('expires'))}</span>
          <input type="date" data-field="expiresOn" value="${esc(f.expiresOn ?? '')}"></label>
      </div>
      <div class="chips">
        ${chip(t('expiry_2w'), false, 'expiry', 'w2')}
        ${chip(t('expiry_1m'), false, 'expiry', 'm1')}
        ${chip(t('expiry_3m'), false, 'expiry', 'm3')}
        ${f.expiresOn ? chip(t('clear'), false, 'expiry', 'clear') : ''}
      </div>
      <p class="hint">${esc(t('expiry_optional'))}</p>

      <label class="field"><span>${esc(t('notes'))}</span>
        <textarea data-field="notes" rows="2">${esc(f.notes)}</textarea></label>
    </main>
    <nav class="actions">
      ${isNew ? '' : `<button type="button" class="btn danger" data-action="delete">
        ${icon('trash')}${esc(t('delete'))}</button>`}
      <button type="button" class="btn primary" data-action="save"
        ${err.canSave ? '' : 'disabled'}>${esc(t('save'))}</button>
    </nav>`;
}

async function saveForm() {
  const f = ui.form;
  if (!formErrors(f).canSave) return;
  const price = f.priceText.trim() === '' ? null : Math.round(Number(f.priceText) * 100);
  const { quantityText, priceText, ...rest } = f;
  const out = await commit((s) => repo.save(s, {
    ...rest,
    name: f.name.trim(),
    quantity: Number(quantityText),
    priceCents: price,
    purchasedOn: f.purchasedOn || today(),
    expiresOn: f.expiresOn || null,
  }));
  if (out.kind === 'merged') showSnack(t('merged_into', t(`area_${f.storageArea}`)));
  go('#/');
}

// ---- scan --------------------------------------------------------------------

function renderScan() {
  const s = ui.scan ?? { text: '' };
  const header = `
    <header class="bar">
      ${iconButton('back', 'nav', t('back'), 'data-value="#/"')}
      <div class="bar-title"><h1>${esc(t('scan_title'))}</h1></div>
    </header>`;
  if (!s.receipt) {
    return `${header}
      <main class="page form">
        <p class="hint">${esc(t('scan_how'))}</p>
        <label class="field"><span>${esc(t('scan_paste'))}</span>
          <textarea id="receipt-text" rows="12" spellcheck="false"
            autocapitalize="off">${esc(s.text)}</textarea></label>
      </main>
      <nav class="actions">
        <button type="button" class="btn primary" data-action="scan-read"
          ${s.text.trim() ? '' : 'disabled'}>${esc(t('scan_read'))}</button>
      </nav>`;
  }
  const r = s.receipt;
  const keep = s.lines.filter((l) => l.keep).length;
  const rows = s.lines.map((l, index) => `
    <li class="review${l.keep ? '' : ' off'}">
      <input type="checkbox" data-action="scan-toggle" data-value="${index}"
        ${l.keep ? 'checked' : ''} aria-label="${esc(l.name)}">
      <div class="review-body">
        <input data-scan-name="${index}" value="${esc(l.name)}" aria-label="${esc(t('name'))}">
        <div class="review-meta">
          <span>${l.line.quantity !== 1 ? `× ${esc(formatQuantity(l.line.quantity))} · ` : ''}${
  l.line.priceCents != null ? esc(formatMoney(l.line.priceCents)) : esc(t('no_price'))}</span>
          <select data-scan-area="${index}" aria-label="${esc(t('where'))}">${STORAGE_AREAS.map((a) =>
  `<option value="${a}"${a === l.storageArea ? ' selected' : ''}>${esc(t(`area_${a}`))}</option>`)
    .join('')}</select>
        </div>
      </div>
    </li>`).join('');
  return `${header}
    <main class="page">
      <p class="sub">${esc(t('scan_header', STORES[r.store],
    r.purchasedOn ? formatDate(r.purchasedOn) : t('scan_date_unknown'), keep, s.lines.length))}</p>
      ${s.lines.length === 0 ? `<p class="banner warn">${esc(t('scan_no_lines'))}</p>` : `
      <div class="chips">
        <button type="button" class="text-btn" data-action="scan-all">${esc(t('scan_select_all'))}</button>
        <button type="button" class="text-btn" data-action="scan-none">${esc(t('scan_select_none'))}</button>
      </div>
      <ul class="list">${rows}</ul>`}
    </main>
    <nav class="actions">
      <button type="button" class="btn secondary" data-action="scan-reset">${esc(t('scan_again'))}</button>
      <button type="button" class="btn primary" data-action="scan-commit"
        ${keep ? '' : 'disabled'}>${esc(t('scan_add', keep))}</button>
    </nav>`;
}

// ---- history -----------------------------------------------------------------

function renderHistory() {
  const report = repo.wasteReport(state);
  const log = repo.history(state);
  const weeks = repo.groupByWeek(log.filter((e) => repo.acceptsFilter(ui.historyFilter, e)));
  const thisWeek = addDays(today(), -((new Date().getDay() + 6) % 7));
  const weekTitle = (w) => {
    if (w.start === thisWeek) return t('this_week');
    if (w.start === addDays(thisWeek, -7)) return t('last_week');
    return `${formatDate(w.start)} – ${formatDate(w.end)}`;
  };
  const q = formatQuantity;
  const summary = `
    <section class="card summary">
      <h2>${esc(t('waste_window'))}</h2>
      <p>${esc(t('waste_eaten', q(report.usedQuantity)))} · ${
  esc(t('waste_thrown', q(report.discardedQuantity)))}</p>
      <p class="big${report.wastePercent == null ? ' muted' : ' warn-text'}">${
  esc(report.wastePercent == null ? t('waste_none') : t('waste_percent', report.wastePercent))}</p>
      ${report.discardedCents > 0
    ? `<p>${esc(t('waste_money', formatMoney(report.discardedCents)))}</p>` : ''}
      ${report.discardedByCategory.length ? `<h3>${esc(t('waste_by_category'))}</h3>
        <p class="small">${report.discardedByCategory.slice(0, 4)
    .map(([c, n]) => `${esc(t(`cat_${c}`))} × ${esc(q(n))}`).join(' · ')}</p>` : ''}
      ${report.discardedByStore.length ? `<h3>${esc(t('waste_by_store'))}</h3>
        <p class="small">${report.discardedByStore.slice(0, 4)
    .map(([s, n]) => `${esc(STORES[s])} × ${esc(q(n))}`).join(' · ')}</p>` : ''}
    </section>`;
  const body = weeks.length === 0
    ? `<p class="empty">${esc(log.length ? t('history_empty_filtered') : t('history_empty'))}<br>
        <span class="small">${esc(t('history_hint'))}</span></p>`
    : weeks.map((w) => `
      <section class="week">
        <h2>${esc(weekTitle(w))}</h2>
        <p class="small muted">${esc([
    w.eatenQuantity > 0 ? t('waste_eaten', q(w.eatenQuantity)) : null,
    w.thrownQuantity > 0 ? t('waste_thrown', q(w.thrownQuantity)) : null,
  ].filter(Boolean).join(' · '))}</p>
        <ul class="list">${w.events.map((e) => `
          <li class="row history-row">
            <div class="row-main static">
              <span class="row-name">${esc(e.name)} <span class="muted">× ${esc(q(e.quantity))}</span></span>
              <span class="row-meta">${esc([formatDate(e.occurredOn),
    e.reason ? t(`reason_${e.reason}`) : null, t(`cat_${e.category}`)].filter(Boolean).join(' · '))}</span>
            </div>
            ${iconButton('undo', 'history-undo', t('put_back'), `data-value="${e.id}"`)}
          </li>`).join('')}</ul>
      </section>`).join('');
  return `
    <header class="bar">
      ${iconButton('back', 'nav', t('back'), 'data-value="#/"')}
      <div class="bar-title"><h1>${esc(t('history_title'))}</h1></div>
    </header>
    <main class="page">
      ${summary}
      <div class="chips" role="group">
        ${chip(t('filter_all'), ui.historyFilter === 'ALL', 'history-filter', 'ALL')}
        ${chip(t('reason_USED'), ui.historyFilter === 'EATEN', 'history-filter', 'EATEN')}
        ${chip(t('reason_DISCARDED'), ui.historyFilter === 'THROWN_OUT', 'history-filter', 'THROWN_OUT')}
      </div>
      ${body}
    </main>`;
}

// ---- labels ------------------------------------------------------------------

function cardHtml(zone, forPrint) {
  if (!zone) {
    // Padding on the last printed page: a spare ruled card, not white space.
    return `<div class="label-card blank" style="--fill:#CFCAC2">
      <div class="label-panel">${'<i class="rule"></i>'.repeat(4)}</div></div>`;
  }
  const lines = zone.items.map(labelLine);
  const body = lines.length
    ? `<ul class="label-lines${lines.length > 5 ? ' two' : ''}">${lines.map((l) =>
      `<li>${esc(l)}</li>`).join('')}</ul>`
    : (forPrint ? '<i class="rule"></i>'.repeat(4)
      : `<p class="label-empty">${esc(t('labels_empty_card'))}</p>`);
  return `<div class="label-card" style="--fill:${zone.fill};--chip:${zone.chip}">
    <span class="label-chip">${esc(t(`area_${zone.storageArea}`))}</span>
    <h3 class="label-heading">${esc(t(zone.heading))}</h3>
    <div class="label-panel">${body}</div>
  </div>`;
}

/** A4 pages, ten cards each, in the same positions as the Android PDF. */
function printSheetHtml(sheet) {
  const slots = [...sheet.zones];
  while (slots.length % CARDS_PER_PAGE !== 0) slots.push(null);
  const pages = [];
  for (let i = 0; i < slots.length; i += CARDS_PER_PAGE) {
    pages.push(`<section class="print-page">${slots.slice(i, i + CARDS_PER_PAGE)
      .map((z) => cardHtml(z, true)).join('')}</section>`);
  }
  return pages.join('');
}

function renderLabels() {
  const sheet = labelSheet(state.items);
  document.getElementById('print-root').innerHTML = printSheetHtml(sheet);
  return `
    <header class="bar">
      ${iconButton('back', 'nav', t('back'), 'data-value="#/"')}
      <div class="bar-title"><h1>${esc(t('labels'))}</h1>
        <p class="sub">${esc(t('labels_count', sheet.itemCount, sheet.pageCount))}</p></div>
    </header>
    <main class="page">
      <p class="hint">${esc(t('labels_desc'))}</p>
      <div class="label-grid">${sheet.printed.map((z) => cardHtml(z, false)).join('')}</div>
      ${sheet.extra.length ? `<h2 class="section-title">${esc(t('labels_extra'))}</h2>
        <div class="label-grid">${sheet.extra.map((z) => cardHtml(z, false)).join('')}</div>` : ''}
    </main>
    <nav class="actions">
      <button type="button" class="btn primary" data-action="print">
        ${icon('print')}${esc(t('labels_print'))}</button>
    </nav>`;
}

// ---- settings ----------------------------------------------------------------

function renderSettings() {
  return `
    <header class="bar">
      ${iconButton('back', 'nav', t('back'), 'data-value="#/"')}
      <div class="bar-title"><h1>${esc(t('settings_title'))}</h1></div>
    </header>
    <main class="page form">
      <label class="field"><span>${esc(t('language'))}</span>
        <select id="lang">
          <option value="auto"${prefs.lang === 'auto' ? ' selected' : ''}>${esc(t('lang_auto'))}</option>
          <option value="zh"${prefs.lang === 'zh' ? ' selected' : ''}>中文</option>
          <option value="en"${prefs.lang === 'en' ? ' selected' : ''}>English</option>
        </select></label>

      <section class="card">
        <h2>${esc(t('backup'))}</h2>
        <p class="small">${esc(t('backup_desc'))}</p>
        <p class="small muted">${esc(state.lastBackupOn
    ? t('backup_nag', formatDate(state.lastBackupOn)) : t('backup_never'))}</p>
        <div class="row-buttons">
          <button type="button" class="btn secondary" data-action="export">${esc(t('export'))}</button>
          <label class="btn secondary">${esc(t('import'))}
            <input type="file" id="import-file" accept="application/json,.json,text/plain" hidden></label>
        </div>
      </section>

      <section class="card">
        <h2>${esc(t('receipt_text'))}</h2>
        <p class="small">${esc(t('receipt_text_desc'))}</p>
        <div class="row-buttons">
          <button type="button" class="btn secondary" data-action="export-receipts"
            ${state.purchases.length ? '' : 'disabled'}>${esc(t('export_receipt_text'))}</button>
        </div>
      </section>

      <p class="small muted">${esc(persistent ? t('storage_persistent') : t('storage_best_effort'))}</p>
      <p class="small muted">${esc(t('privacy'))}</p>
    </main>
    <dialog id="import-dialog">
      <form method="dialog">
        <h2>${esc(t('import_title'))}</h2>
        <p class="small">${esc(t('import_body'))}</p>
        <div class="row-buttons end">
          <button value="cancel" class="text-btn">${esc(t('cancel'))}</button>
          <button value="merge" class="btn secondary">${esc(t('merge'))}</button>
          <button value="replace" class="btn danger">${esc(t('replace'))}</button>
        </div>
      </form>
    </dialog>`;
}

/** Saves through the share sheet where iOS offers it (Save to Files), else a download. */
async function saveFile(name, text, type) {
  const file = new File([text], name, { type });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file] });
      return true;
    } catch (e) {
      if (e.name === 'AbortError') return false;
    }
  }
  const url = URL.createObjectURL(file);
  const a = Object.assign(document.createElement('a'), { href: url, download: name });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
  return true;
}

async function exportBackup() {
  const text = encode(state.items, state.events);
  const saved = await saveFile(`fridge-inventory-${today()}.json`, text, 'application/json');
  if (!saved) return;
  await commit((s) => { s.lastBackupOn = today(); });
  showSnack(t('exported', state.items.length));
}

async function importBackup(fileInput) {
  const file = fileInput.files?.[0];
  fileInput.value = '';
  if (!file) return;
  let decoded;
  try {
    decoded = decode(await file.text());
  } catch (e) {
    showSnack(t('import_failed', e.message));
    return;
  }
  const dialog = document.getElementById('import-dialog');
  dialog.returnValue = '';
  dialog.showModal();
  dialog.addEventListener('close', async () => {
    const mode = dialog.returnValue;
    if (mode !== 'merge' && mode !== 'replace') return;
    await commit((s) => {
      if (mode === 'replace') repo.replaceAll(s, decoded.items, decoded.events);
      else repo.mergeImport(s, decoded.items, decoded.events);
    });
    showSnack(t('imported', decoded.items.length));
  }, { once: true });
}

// ---- routing and events ------------------------------------------------------

function route() {
  const hash = location.hash || '#/';
  const edit = hash.match(/^#\/edit\/(\d+)$/);
  if (edit) {
    const id = Number(edit[1]);
    if (!ui.form || ui.form.id !== id || ui.formRoute !== hash) {
      ui.form = formFor(id);
      ui.formRoute = hash;
    }
    return renderEdit();
  }
  ui.formRoute = null;
  if (hash === '#/history') return renderHistory();
  if (hash === '#/settings') return renderSettings();
  if (hash === '#/scan') return renderScan();
  if (hash === '#/labels') return renderLabels();
  return renderInventory();
}

function render() {
  const active = document.activeElement;
  const focusId = active?.id;
  const caret = focusId && 'selectionStart' in active ? active.selectionStart : null;
  app.innerHTML = route();
  if (focusId) {
    const again = document.getElementById(focusId);
    if (again) {
      again.focus();
      if (caret != null) again.setSelectionRange?.(caret, caret);
    }
  }
}

const actions = {
  nav: (v) => go(v),
  area: (v) => { ui.area = v || null; render(); },
  category: (v) => { ui.category = v || null; render(); },
  store: (v) => { ui.store = v || null; render(); },
  open: (v) => go(`#/edit/${v}`),
  'use-one': async (v) => {
    const it = repo.item(state, Number(v));
    const emptied = it.quantity - 1 <= 0.0001;
    const eventId = await commit((s) => repo.useAmount(s, it.id, 1));
    showSnack(emptied ? t('all_gone', it.name) : t('logged_use', it.name), eventId);
  },
  'buy-another': (v) => commit((s) => repo.addOne(s, Number(v))),
  'finish-used': async (v) => {
    const it = repo.item(state, Number(v));
    const eventId = await commit((s) => repo.markFinished(s, it.id, 'USED'));
    if (eventId != null) showSnack(t('all_gone', it.name), eventId);
  },
  'finish-discarded': async (v) => {
    const it = repo.item(state, Number(v));
    const eventId = await commit((s) => repo.markFinished(s, it.id, 'DISCARDED'));
    if (eventId != null) showSnack(t('binned', it.name), eventId);
  },
  'snack-undo': async () => {
    const id = ui.snack?.undoEventId;
    ui.snack = null;
    renderSnack();
    if (id != null) await commit((s) => repo.undoConsumption(s, id));
  },
  'history-filter': (v) => { ui.historyFilter = v; render(); },
  'history-undo': (v) => commit((s) => repo.undoConsumption(s, Number(v))),
  guess: () => {
    if (!ui.form.name.trim()) return;
    const g = guess(ui.form.name);
    Object.assign(ui.form, { category: g.category, storageArea: g.storageArea });
    render();
  },
  'form-area': (v) => { ui.form.storageArea = v; render(); },
  // A correction to a wrong number: edits the form only, writes no history. 1 is the floor.
  'form-step': (v) => {
    const current = Number(ui.form.quantityText) || 1;
    ui.form.quantityText = formatQuantity(Math.max(1, current + Number(v)));
    render();
  },
  // "I just used half" is a fact about now, so for a saved item it writes straight through.
  'use-some': async (v) => {
    const amount = Number(v);
    const id = ui.form.id;
    const before = repo.item(state, id);
    if (!before) return;
    const remaining = before.quantity - amount;
    const eventId = await commit((s) => repo.useAmount(s, id, amount));
    if (remaining > 0.0001) {
      ui.form.quantityText = formatQuantity(remaining);
      ui.form.quantity = remaining;
      render();
      showSnack(t('logged_use', before.name), eventId);
    } else {
      showSnack(t('all_gone', before.name), eventId);
      go('#/');
    }
  },
  expiry: (v) => {
    const base = ui.form.purchasedOn || today();
    ui.form.expiresOn = {
      w2: addDays(base, 14), m1: addMonths(base, 1), m3: addMonths(base, 3), clear: null,
    }[v];
    render();
  },
  save: saveForm,
  delete: async () => {
    const f = ui.form;
    if (!confirm(`${t('delete_title')}\n\n${t('delete_body', f.name)}`)) return;
    await commit((s) => repo.deleteItem(s, f.id));
    go('#/');
  },
  'scan-read': () => {
    const text = ui.scan?.text ?? '';
    const receipt = parseReceipt(text);
    ui.scan = {
      text,
      receipt,
      // Low-confidence lines start unchecked so a bad parse cannot quietly pollute the list.
      lines: receipt.lines.map((line) => ({
        line, name: line.name, keep: line.confidence >= 0.7,
        storageArea: guess(line.name).storageArea,
      })),
    };
    render();
  },
  'scan-toggle': (v) => { const l = ui.scan.lines[Number(v)]; l.keep = !l.keep; render(); },
  'scan-all': () => { ui.scan.lines.forEach((l) => { l.keep = true; }); render(); },
  'scan-none': () => { ui.scan.lines.forEach((l) => { l.keep = false; }); render(); },
  'scan-reset': () => { ui.scan = null; render(); },
  'scan-commit': async () => {
    const kept = ui.scan.lines.filter((l) => l.keep && l.name.trim());
    await commit((s) => repo.commitReceipt(s, ui.scan.receipt, kept.map((l) => ({
      line: l.line, name: l.name.trim(), storageArea: l.storageArea,
    }))));
    ui.scan = null;
    showSnack(t('scan_added', kept.length));
    go('#/');
  },
  export: exportBackup,
  print: () => window.print(),
  'export-receipts': async () => {
    const text = ReceiptTextExport.format([...state.purchases].reverse());
    if (await saveFile(`fridge-receipts-${today()}.txt`, text, 'text/plain')) {
      showSnack(t('receipts_exported', state.purchases.length));
    }
  },
};

document.addEventListener('click', (event) => {
  const target = event.target.closest('[data-action]');
  if (!target || target.disabled) return;
  const action = actions[target.dataset.action];
  if (!action) return;
  if (target.type === 'checkbox') event.preventDefault();
  action(target.dataset.value ?? '');
});

document.addEventListener('input', (event) => {
  const el = event.target;
  if (el.id === 'search') {
    ui.query = el.value;
    render();
  } else if (el.id === 'receipt-text') {
    ui.scan = { text: el.value };
    document.querySelector('[data-action="scan-read"]').disabled = !el.value.trim();
  } else if (el.dataset.field && ui.form) {
    ui.form[el.dataset.field] = el.value;
    // Only the save button and the error outlines depend on typing; avoid re-rendering so
    // the keyboard and caret stay put.
    const err = formErrors(ui.form);
    document.querySelector('[data-action="save"]').disabled = !err.canSave;
    if (el.dataset.field === 'quantityText') el.classList.toggle('invalid', err.quantity);
    if (el.dataset.field === 'priceText') el.classList.toggle('invalid', err.price);
  } else if (el.dataset.scanName != null) {
    ui.scan.lines[Number(el.dataset.scanName)].name = el.value;
  }
});

document.addEventListener('change', async (event) => {
  const el = event.target;
  if (el.id === 'lang') {
    prefs.lang = el.value;
    setLanguage(prefs.lang);
    await savePrefs(prefs).catch(() => {});
    render();
  } else if (el.id === 'import-file') {
    importBackup(el);
  } else if (el.dataset.scanArea != null) {
    ui.scan.lines[Number(el.dataset.scanArea)].storageArea = el.value;
  } else if (el.dataset.field && ui.form && el.tagName === 'SELECT') {
    ui.form[el.dataset.field] = el.value;
  } else if (el.dataset.field && ui.form && el.type === 'date') {
    ui.form[el.dataset.field] = el.value || null;
    render();
  }
});

window.addEventListener('hashchange', () => {
  render();
  window.scrollTo(0, 0);
});

// ---- start -------------------------------------------------------------------

async function start() {
  try {
    [state, prefs] = await Promise.all([loadState(), loadPrefs()]);
  } catch (e) {
    console.error(e);
    storageOk = false;
  }
  setLanguage(prefs.lang);
  render();
  persistent = await requestPersistence();
  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
}

start();
