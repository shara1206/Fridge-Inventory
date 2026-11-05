// Port of receipt/parser/*.kt. Same regexes, same order of steps, same tests
// (web/tests/parsers.test.js mirrors ParserTest.kt), so a receipt parses the same on both
// phones. A fix to one side should land on the other.
//
// On iPhone the text comes from Live Text (copy from the photo, paste here) rather than from
// ML Kit, so line breaks can differ; parsing is line-based and tolerant of that.

const TRAILING_PRICE = /[$]?\s*(-?\d{1,3}(?:,\d{3})*\.\d{2})\s*[A-Za-z]{0,2}\s*$/;
const LEADING_CODE = /^[\d#*]{4,}\s*/;
const TRAILING_CODE = /\s+[\d]{4,}$/;
const FILLER_DOTS = /\.{2,}/g;
const MULTISPACE = /\s{2,}/g;
const WEIGHT_AT = /(\d+\.\d{1,3})\s*(?:LB|lb|KG|kg)\s*@/;
const COUNT_AT = /^\s*(\d{1,2})\s*(?:@|[xX])\s*[$]?\d/;
const TOTAL_LINE = /\bTOTAL\b|\bBALANCE\s+DUE\b/;
const DATE_SLASH = /\b(\d{1,2})\/(\d{1,2})\/(\d{2,4})\b/;
const DATE_ISO = /\b\d{4}-\d{2}-\d{2}\b/;

const COMMON_NOISE = [
  /\bSUB\s?TOTAL\b/, /\bTOTAL\b/, /\bTAX\b/,
  /\bCASH\b/, /\bCHANGE\b/, /\bTENDER/,
  /\bVISA\b|\bMASTERCARD\b|\bAMEX\b|\bDEBIT\b|\bCREDIT\b|\bEBT\b/,
  /\bAUTH\b|\bAPPROVED\b|\bCHIP\b|\bAID\b|\bREF\s?#/,
  /\bTHANK\s?YOU\b|\bTHANKS\b/,
  /\bSAVINGS?\b|\bCOUPON\b|\bDISCOUNT\b|\bREWARD/,
  /\bMEMBER\b|\bPOINTS?\b|\bLOYALTY\b/,
  /\bCASHIER\b|\bSTORE\b|\bREG(ISTER)?\s?#|\bTRANS(ACTION)?\b|\bINVOICE\b/,
  /\bRETURN\b|\bREFUND\b|\bPOLICY\b|\bRECEIPT\b|\bSURVEY\b/,
  /\bITEMS?\s+(SOLD|COUNT)\b|\b# ?OF ?ITEMS\b/,
  /\bBAG\s?FEE\b|\bBOTTLE\s?DEPOSIT\b|\bCRV\b/,
  // masked card digits
  /^\**\d{4}$/,
  // phone numbers
  /\b\d{3}[-.]\d{3}[-.]\d{4}\b/,
  /\bWWW\.|\.COM\b|\bHTTP/,
];

/** Receipt shorthand seen across all four chains. */
const COMMON_ABBREVIATIONS = {
  ORG: 'Organic', ORGN: 'Organic', ORGANC: 'Organic',
  CHKN: 'Chicken', CHK: 'Chicken', BF: 'Beef', PRK: 'Pork',
  GRND: 'Ground', GRD: 'Ground', BNLS: 'Boneless', BNLSS: 'Boneless',
  SKNLS: 'Skinless', THGH: 'Thigh', BRST: 'Breast',
  SPNCH: 'Spinach', SPNH: 'Spinach', LTUCE: 'Lettuce', LTC: 'Lettuce',
  TOM: 'Tomato', TMTO: 'Tomato', CUC: 'Cucumber', BRCLI: 'Broccoli',
  BROC: 'Broccoli', CRRT: 'Carrot', MSHRM: 'Mushroom', MUSH: 'Mushroom',
  SCLLN: 'Scallion', GRN: 'Green', ONN: 'Onion', GARLC: 'Garlic',
  PTTO: 'Potato', SWT: 'Sweet', AVCDO: 'Avocado', BNNA: 'Banana',
  STRWBRY: 'Strawberry', BLBRY: 'Blueberry', RSPBRY: 'Raspberry',
  MLK: 'Milk', WHL: 'Whole', YOG: 'Yogurt', YGRT: 'Yogurt',
  CHZ: 'Cheese', CHS: 'Cheese', BTTR: 'Butter', CRM: 'Cream',
  EGGS: 'Eggs', LRG: 'Large', XL: 'Extra Large',
  SHRMP: 'Shrimp', SLMN: 'Salmon', TLPIA: 'Tilapia',
  FRZ: 'Frozen', FRZN: 'Frozen', DMPLNG: 'Dumpling',
  NDL: 'Noodle', NDLS: 'Noodles', RC: 'Rice',
  SCE: 'Sauce', SAU: 'Sauce', VNGR: 'Vinegar', OL: 'Oil',
  SNCK: 'Snack', BVRG: 'Beverage', JC: 'Juice', WTR: 'Water',
  LB: 'lb', OZ: 'oz', PK: 'Pack', PKG: 'Package', CT: 'Count',
  BNCH: 'Bunch', BAG: 'Bag', BX: 'Box',
};

const LETTER = /\p{L}/u;

export function toCents(text) {
  const value = Number(text.replace(/,/g, ''));
  return Number.isFinite(value) ? Math.round(value * 100) : null;
}

export function titleCase(text) {
  return text.split(' ').map((word) => {
    if (word === '') return word;
    if ([...word].length <= 2 && /^\p{Lu}+$/u.test(word)) return word; // XL
    if (/\p{Ll}/u.test(word)) return word; // already cased
    const lower = word.toLowerCase();
    return lower.charAt(0).toUpperCase() + lower.slice(1);
  }).join(' ');
}

function trimChars(text, chars) {
  let start = 0;
  let end = text.length;
  while (start < end && chars.includes(text[start])) start++;
  while (end > start && chars.includes(text[end - 1])) end--;
  return text.slice(start, end);
}

function trimEndChars(text, chars) {
  let end = text.length;
  while (end > 0 && chars.includes(text[end - 1])) end--;
  return text.slice(0, end);
}

function isoOrNull(year, month, day) {
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day) return null;
  return date.toISOString().slice(0, 10);
}

/**
 * The "NAME .... 4.99 T" receipt shape every store uses with small variations. A store
 * passes its own noise patterns, abbreviations and code stripping.
 */
export class LineBasedParser {
  constructor({ store, matches, extraNoise = [], extraAbbreviations = {}, stripCodes }) {
    this.store = store;
    this.matches = matches;
    this.extraNoise = extraNoise;
    this.abbreviations = { ...COMMON_ABBREVIATIONS, ...extraAbbreviations };
    if (stripCodes) this.stripCodes = stripCodes;
  }

  parse(rawText) {
    const rawLines = rawText.split(/\r\n|\n|\r/).map((l) => l.trim()).filter((l) => l !== '');
    const items = [];
    let pendingQuantity = null;

    for (const raw of rawLines) {
      // "2 @ 1.99" / "0.86 lb @ 5.99/lb" applies to the line before or after it.
      const multiplier = multiplierQuantity(raw);
      if (multiplier !== null) {
        if (items.length > 0 && looksLikeBareMultiplier(raw)) {
          items[items.length - 1] = { ...items[items.length - 1], quantity: multiplier };
        } else {
          pendingQuantity = multiplier;
        }
        continue;
      }

      if (this.isNoise(raw)) continue;

      const price = trailingPriceCents(raw);
      if (price === null) continue;
      const name = this.cleanName(raw.replace(TRAILING_PRICE, ''));
      if ([...name].length < 2) continue;
      if (!LETTER.test(name)) continue;

      items.push({
        name,
        quantity: pendingQuantity ?? 1,
        priceCents: price,
        rawLine: raw,
        confidence: confidenceFor(name, price),
      });
      pendingQuantity = null;
    }

    return {
      store: this.store,
      purchasedOn: findDate(rawText),
      totalCents: findTotalCents(rawLines),
      lines: items,
      rawText,
    };
  }

  isNoise(line) {
    const upper = line.toUpperCase();
    return COMMON_NOISE.some((r) => r.test(upper)) || this.extraNoise.some((r) => r.test(upper));
  }

  /** Strips leading UPC / PLU / department codes. */
  stripCodes(name) {
    return name.replace(LEADING_CODE, '').replace(TRAILING_CODE, '');
  }

  cleanName(rawName) {
    let name = this.stripCodes(rawName.trim())
      .replace(FILLER_DOTS, ' ')
      .replace(MULTISPACE, ' ')
      .trim();
    name = trimEndChars(name, '*-.,#').trim();
    name = name.split(' ').map((word) => {
      const key = trimChars(word.toUpperCase(), '.,*');
      return this.abbreviations[key] ?? word;
    }).join(' ');
    return titleCase(name);
  }
}

function confidenceFor(name, priceCents) {
  let score = 0.5;
  if (name.includes(' ')) score += 0.2; // multi-word names are usually real
  if (name.length >= 4 && name.length <= 40) score += 0.2;
  if (priceCents >= 25 && priceCents <= 10_000) score += 0.1; // 0.25 – 100.00
  return Math.min(Math.round(score * 10) / 10, 1);
}

function trailingPriceCents(line) {
  const match = TRAILING_PRICE.exec(line);
  return match ? toCents(match[1]) : null;
}

function multiplierQuantity(line) {
  const weight = WEIGHT_AT.exec(line);
  if (weight) return Number(weight[1]);
  const count = COUNT_AT.exec(line);
  if (count) return Number(count[1]);
  return null;
}

function looksLikeBareMultiplier(line) {
  return ![...line].some((c) => LETTER.test(c) && !'LBSKGEA@X'.includes(c.toUpperCase()));
}

function findTotalCents(lines) {
  const totals = lines
    .filter((line) => {
      const u = line.toUpperCase();
      return TOTAL_LINE.test(u) && !u.includes('SUBTOTAL') && !u.includes('SUB TOTAL');
    })
    .map(trailingPriceCents)
    .filter((c) => c !== null);
  return totals.length ? Math.max(...totals) : null;
}

function findDate(rawText) {
  const slash = DATE_SLASH.exec(rawText);
  if (slash) {
    let year = Number(slash[3]);
    if (year < 100) year += 2000;
    return isoOrNull(year, Number(slash[1]), Number(slash[2]));
  }
  const iso = DATE_ISO.exec(rawText);
  if (iso) {
    const [y, m, d] = iso[0].split('-').map(Number);
    return isoOrNull(y, m, d);
  }
  return null;
}

// ---- stores ------------------------------------------------------------------

const hmart = new LineBasedParser({
  store: 'HMART',
  matches: (text) => {
    const u = text.toUpperCase();
    return u.includes('H MART') || u.includes('HMART') || u.includes('H-MART') ||
      u.includes('HANAHREUM');
  },
  extraNoise: [
    /\bDEPT\b|\bDEPARTMENT\b/,
    /\bSMART\s?POINT|\bK-?POINT/,
    /\bCUSTOMER\s?COPY\b|\bMERCHANT\s?COPY\b/,
    /\bQTY\b\s*:?\s*\d+\s*$/,
    /\bFOOD\s?STAMP\b/,
  ],
  extraAbbreviations: {
    KIM: 'Kimchi', KMCHI: 'Kimchi', GOCHU: 'Gochujang',
    TFU: 'Tofu', SOYBN: 'Soybean', SESME: 'Sesame',
    MDLE: 'Noodle', RAMYN: 'Ramyun', MANDU: 'Mandu',
    PERILA: 'Perilla', PRLLA: 'Perilla', CHIVE: 'Chive',
    NAPA: 'Napa', DAIKN: 'Daikon', ENOKI: 'Enoki',
    BLGGI: 'Bulgogi', BULGO: 'Bulgogi', GALBI: 'Galbi',
  },
});

const tt = new LineBasedParser({
  store: 'TT',
  matches: (text) => {
    const u = text.toUpperCase();
    return u.includes('T&T') || u.includes('T & T') || u.includes('TNT SUPERMARKET') ||
      text.includes('大統華');
  },
  extraNoise: [
    /\bGST\b|\bPST\b|\bHST\b|\bQST\b/,
    /\bAIR\s?MILES\b|\bT&T\s?REWARD/,
    /\bCAD\b|\bDEPOSIT\b|\bENVIRO\b|\bECO\s?FEE\b/,
    /\bDEBIT\s?CARD\b|\bINTERAC\b/,
  ],
  extraAbbreviations: {
    CHOY: 'Choy', BOKCHY: 'Bok Choy', GAILAN: 'Gai Lan',
    YUCHOI: 'Yu Choy', TOFU: 'Tofu', DOUFU: 'Tofu',
    WNTN: 'Wonton', DMPL: 'Dumpling', BBQ: 'BBQ',
    SHTKE: 'Shiitake', SHIITK: 'Shiitake', LTS: 'Lotus',
    PORKBLY: 'Pork Belly', FSH: 'Fish', STMD: 'Steamed',
  },
  // Item codes at T&T can be 5–13 digits and sometimes trail the Chinese name.
  stripCodes(name) {
    return LineBasedParser.prototype.stripCodes.call(this, name)
      .replace(/\s+\d{5,}\s*/g, ' ').trim();
  },
});

const traderJoes = new LineBasedParser({
  store: 'TRADER_JOES',
  matches: (text) => text.toUpperCase().includes('TRADER JOE'),
  extraNoise: [
    /\bITEMS?\b\s*\d*\s*$/,
    /\bOPEN\s?DAILY\b|\bBALANCE\b/,
    /\bTJ'?S\b\s*$/,
  ],
  extraAbbreviations: {
    EVOO: 'Olive Oil', MED: 'Medium', SM: 'Small', SLD: 'Salad', SNP: 'Snap',
    RSTD: 'Roasted', UNSWT: 'Unsweetened', GF: 'Gluten Free',
  },
});

const wholeFoods = new LineBasedParser({
  store: 'WHOLE_FOODS',
  matches: (text) => {
    const u = text.toUpperCase();
    return u.includes('WHOLE FOODS') || u.includes('WFM') ||
      (u.includes('AMAZON') && u.includes('MARKET'));
  },
  extraNoise: [
    /\bPRIME\b|\bAMZN\b|\bAMAZON\b/,
    /\bSNAP\b|\bWIC\b/,
    /\bYOU\s?SAVED\b|\bREG\s?PRICE\b|\bSALE\s?PRICE\b/,
    /\bNET\s?WT\b|\bPRICE\s?PER\b|\bPER\s?LB\b/,
  ],
  extraAbbreviations: {
    365: '365', WF: 'Whole Foods', GG: 'Grass Fed',
    PSTR: 'Pasture', RSPNSBL: 'Responsibly', SSTNBL: 'Sustainable',
    AIRCHL: 'Air Chilled', NTRL: 'Natural', VEG: 'Vegetable',
  },
});

const generic = new LineBasedParser({ store: 'OTHER', matches: () => true });

/** Order matters: the generic parser matches everything, so it stays last. */
export const PARSERS = [hmart, tt, traderJoes, wholeFoods, generic];

export function parseReceipt(rawText) {
  const parser = PARSERS.find((p) => p.matches(rawText)) ?? generic;
  return parser.parse(rawText);
}
