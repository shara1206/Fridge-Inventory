// Port of receipt/ReceiptTextExport.kt: the same header per receipt, so a text file from
// either phone reads the same when it is sent in to fix a parser.

import { STORES } from './model.js';

export const ReceiptTextExport = {
  format(purchases) {
    return purchases.map((p) => this.receipt(p.rawText, p.store, p.purchasedOn, p.id))
      .join('\n\n');
  },

  receipt(rawText, store, purchasedOn, id = null) {
    const header = [
      id != null ? `#${id}` : null,
      `parser: ${STORES[store] ?? STORES.OTHER}`,
      purchasedOn ?? 'date: unknown',
    ].filter(Boolean).join(' · ');
    const body = rawText.trimEnd() || '(OCR found no text)';
    return `=== ${header} ===\n${body}\n=== end ===`;
  },
};
