// Port of data/labels/LabelSheet.kt and LabelPalette.kt: the printable zone cards.
//
// The ten printed zones come first and never move, because the card taped to a shelf stays
// the card for that shelf. Anything they do not cover becomes an extra card after them.

import { CATEGORIES, STORAGE_AREAS, formatQuantity } from './model.js';

export const CARDS_PER_PAGE = 10;

/** The ten fixed cards, in reading order, with their printed heading key and colours. */
export const PRINTED_ZONES = [
  { category: 'DAIRY_EGGS', area: 'FRIDGE', heading: 'zone_dairy_eggs', fill: '#D9D2BE', chip: '#7E7658' },
  { category: 'VEGETABLES', area: 'FRIDGE', heading: 'zone_vegetables', fill: '#C8D0C1', chip: '#657059' },
  { category: 'FRUIT', area: 'FRIDGE', heading: 'zone_fruit', fill: '#D6C4C6', chip: '#7B6062' },
  // Slot four was seasoning on the first printing; tofu took it and kept the colour.
  { category: 'TOFU_SOY', area: 'FRIDGE', heading: 'zone_tofu_soy', fill: '#C3CDD3', chip: '#5D6C74' },
  { category: 'PREPARED', area: 'FRIDGE', heading: 'zone_prepared', fill: '#CEC6D0', chip: '#6E6674' },
  { category: 'PREPARED', area: 'FREEZER', heading: 'zone_prepared', fill: '#CEC6D0', chip: '#6E6674' },
  { category: 'MEAT', area: 'FREEZER', heading: 'zone_meat', fill: '#D2BEC1', chip: '#78595D' },
  { category: 'SEAFOOD', area: 'FREEZER', heading: 'zone_seafood', fill: '#C1CDD2', chip: '#5B6B72' },
  { category: 'SNACKS', area: 'FREEZER', heading: 'zone_snacks', fill: '#CDC4CF', chip: '#6C6472' },
  { category: 'FROZEN_STAPLES', area: 'FREEZER', heading: 'zone_frozen_staples', fill: '#D6CFB9', chip: '#7C7355' },
];

/** Extra cards are neutral on purpose: colour is how the ten fixed zones are told apart. */
export const EXTRA = { fill: '#CFCAC2', chip: '#6E675C' };

const key = (category, area) => `${category}/${area}`;

/**
 * Finished rows are history, not contents: a label answers "what is in there now".
 * @returns {{zones: object[], printed: object[], extra: object[], itemCount: number,
 *   pageCount: number}}
 */
export function labelSheet(items) {
  const byZone = new Map();
  for (const it of items) {
    if (it.finishedOn != null) continue;
    const k = key(it.category, it.storageArea);
    if (!byZone.has(k)) byZone.set(k, []);
    byZone.get(k).push(it);
  }
  const printedKeys = new Set(PRINTED_ZONES.map((z) => key(z.category, z.area)));
  const printed = PRINTED_ZONES.map((z) => ({
    category: z.category, storageArea: z.area, heading: z.heading, fill: z.fill, chip: z.chip,
    items: byZone.get(key(z.category, z.area)) ?? [], printed: true,
  }));
  // Grouped by where it lives first, so the extras for one place get taped up together.
  const extra = [...byZone.keys()]
    .filter((k) => !printedKeys.has(k))
    .map((k) => k.split('/'))
    .sort((a, b) => STORAGE_AREAS.indexOf(a[1]) - STORAGE_AREAS.indexOf(b[1]) ||
      CATEGORIES.indexOf(a[0]) - CATEGORIES.indexOf(b[0]))
    .map(([category, area]) => ({
      category, storageArea: area, heading: `cat_${category}`, fill: EXTRA.fill,
      chip: EXTRA.chip, items: byZone.get(key(category, area)), printed: false,
    }));
  const zones = [...printed, ...extra];
  return {
    zones,
    printed,
    extra,
    itemCount: zones.reduce((n, z) => n + z.items.length, 0),
    pageCount: 1 + Math.ceil(extra.length / CARDS_PER_PAGE),
  };
}

/** A count of one is left off: the name already says there is one. */
export function labelLine(item) {
  return item.quantity > 1 ? `${item.name} ×${formatQuantity(item.quantity)}` : item.name;
}
