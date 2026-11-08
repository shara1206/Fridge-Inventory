// Wording follows the Android app's values/ and values-zh/ strings.xml.

const en = {
  app_name: 'Fridge',
  area_FRIDGE: 'Fridge', area_FREEZER: 'Freezer', area_PANTRY: 'Pantry',
  cat_VEGETABLES: 'Vegetables', cat_FRUIT: 'Fruit', cat_MEAT: 'Meat', cat_SEAFOOD: 'Seafood',
  cat_TOFU_SOY: 'Tofu & soy', cat_DAIRY_EGGS: 'Dairy & eggs',
  cat_FROZEN_STAPLES: 'Frozen staples', cat_GRAINS_NOODLES: 'Grains & noodles',
  cat_SEASONING: 'Seasoning', cat_SNACKS: 'Snacks', cat_BEVERAGE: 'Drinks',
  cat_PREPARED: 'Ready to eat', cat_OTHER: 'Other',
  reason_USED: 'Eaten', reason_DISCARDED: 'Thrown out',
  filter_all: 'All',
  save: 'Save', back: 'Back', delete: 'Delete', cancel: 'Cancel', undo: 'Undo',
  put_back: 'Put back', settings: 'Settings', history: 'History',
  search: 'Search', add_manual: 'Add item', scan: 'Add from receipt',
  empty: 'Nothing logged yet. Add an item, or paste a receipt.',
  no_match: 'Nothing matches.',
  mark_used: 'Mark as eaten', mark_discarded: 'Threw it out',
  qty_increase: 'Bought another', qty_decrease: 'Ate one',
  summary_items: '{0} items', summary_expired: '{0} expired', summary_due: '{0} due soon',
  logged_use: 'Ate 1 {0}', all_gone: '{0}: all gone', binned: '{0}: thrown out',
  expiry_over: '{0}d over', expiry_today: 'today', expiry_days: '{0}d',
  add_item: 'Add item', edit_item: 'Edit item',
  name: 'Name', guess: 'Guess category & storage from name',
  where: 'Where', category: 'Category', store: 'Store', quantity: 'Count',
  price: 'Price', notes: 'Notes', bought: 'Bought', expires: 'Expires',
  expiry_optional: 'Optional. Leave it empty and nothing will nag you about this one.',
  expiry_2w: '2 weeks', expiry_1m: '1 month', expiry_3m: '3 months', clear: 'Clear',
  use_some: 'Used some of it',
  merged_into: 'Added to the {0} you already had',
  delete_title: 'Delete this item?',
  delete_body: '{0} will be removed entirely, and will not appear in the waste report. ' +
    'To record that you threw it out, use the bin on the list instead.',
  scan_title: 'Add from receipt',
  scan_how: 'On iPhone: open the receipt photo (or point the Camera at the receipt), touch ' +
    'and hold the text to select it, choose Select All, then Copy, and paste it here.',
  scan_paste: 'Paste receipt text',
  scan_read: 'Read receipt',
  scan_header: '{0} · {1} · {2} of {3} selected',
  scan_date_unknown: 'date not found',
  scan_no_lines: 'No item lines found. Check that the prices were copied too.',
  scan_select_all: 'Select all', scan_select_none: 'Select none',
  scan_add: 'Add {0} items', scan_added: 'Added {0} items', scan_again: 'Start over',
  no_price: 'no price',
  history_title: 'History',
  history_empty: 'Nothing has left the kitchen yet.',
  history_hint: 'Every −, ✓ and bin on the list is logged here.',
  history_empty_filtered: 'Nothing like that has been logged.',
  this_week: 'This week', last_week: 'Last week',
  waste_window: 'Last 30 days', waste_eaten: '{0} eaten', waste_thrown: '{0} thrown out',
  waste_percent: '{0}% wasted', waste_none: 'not enough data yet',
  waste_money: '{0} thrown away', waste_by_category: 'Most wasted categories',
  waste_by_store: 'By store',
  settings_title: 'Settings', language: 'Language', lang_auto: 'Automatic',
  backup: 'Backup',
  backup_desc: 'Save everything to a JSON file, or read one back in. The same file works ' +
    'with the Android app. Data lives only in this browser, so export now and then.',
  export: 'Export', import: 'Import',
  exported: 'Exported {0} items', imported: 'Imported {0} items',
  import_failed: 'Could not read that file. {0}',
  import_title: 'Import how?',
  import_body: 'Merge adds the file to what you already have. Replace deletes everything ' +
    'here first — use that when moving from another phone.',
  merge: 'Merge', replace: 'Replace all',
  receipt_text: 'Receipt text',
  receipt_text_desc: 'Save the text of every receipt you added to a file, to send along ' +
    'when one was read badly.',
  export_receipt_text: 'Export receipt text', receipts_exported: 'Exported {0} receipts',
  backup_nag: 'Last backup: {0}. Data lives only in this browser.',
  backup_never: 'Not backed up yet. Data lives only in this browser.',
  backup_now: 'Back up',
  storage_persistent: 'This browser will keep the data even when space runs low.',
  storage_best_effort: 'Add this page to your Home Screen so iOS keeps its data.',
  privacy: 'Nothing leaves this phone. The app works offline and sends no data anywhere.',
  storage_error: 'This browser will not let the app save data (private browsing?). ' +
    'Changes will be lost when the page closes.',
  labels: 'Zone labels',
  labels_desc: 'One card per zone, ten to an A4 page. Print it (or save it as a PDF from ' +
    'the print screen), cut the cards apart and tape them inside the door.',
  labels_count: '{0} items · {1} pages',
  labels_empty_card: 'Empty. Print it and write it in.',
  labels_extra: 'Extra cards',
  labels_print: 'Print / Save as PDF',
  zone_dairy_eggs: 'Dairy · Eggs', zone_vegetables: 'Vegetables', zone_fruit: 'Fruit',
  zone_tofu_soy: 'Tofu & soy', zone_prepared: 'Ready · Prepared', zone_meat: 'Fresh meat',
  zone_seafood: 'Seafood · Fish', zone_snacks: 'Ice cream · Dessert',
  zone_frozen_staples: 'Frozen staples',
};

const zh = {
  app_name: '冰箱',
  area_FRIDGE: '冷藏', area_FREEZER: '冷冻', area_PANTRY: '常温',
  cat_VEGETABLES: '蔬菜', cat_FRUIT: '水果', cat_MEAT: '生鲜肉类', cat_SEAFOOD: '海鲜',
  cat_TOFU_SOY: '豆制品', cat_DAIRY_EGGS: '乳品蛋类', cat_FROZEN_STAPLES: '速冻主食',
  cat_GRAINS_NOODLES: '米面粮油', cat_SEASONING: '调味料', cat_SNACKS: '零食',
  cat_BEVERAGE: '饮料', cat_PREPARED: '熟食即食', cat_OTHER: '其他',
  reason_USED: '吃掉了', reason_DISCARDED: '扔掉了',
  filter_all: '全部',
  save: '保存', back: '返回', delete: '删除', cancel: '取消', undo: '撤销',
  put_back: '放回去', settings: '设置', history: '历史',
  search: '搜索', add_manual: '手动添加', scan: '从小票添加',
  empty: '还没有记录。手动加一条，或者粘贴小票文字。',
  no_match: '没有符合的。',
  mark_used: '吃掉了', mark_discarded: '扔掉了',
  qty_increase: '又买了一份', qty_decrease: '吃掉了一份',
  summary_items: '{0} 件', summary_expired: '{0} 已过期', summary_due: '{0} 快到期',
  logged_use: '已记录：吃掉 1 个{0}', all_gone: '{0}：吃完了', binned: '{0}：已扔掉',
  expiry_over: '过期 {0} 天', expiry_today: '今天', expiry_days: '{0} 天',
  add_item: '添加', edit_item: '编辑',
  name: '名称', guess: '根据名称猜分类和存放位置',
  where: '放在哪', category: '分类', store: '店铺', quantity: '数量',
  price: '价格', notes: '备注', bought: '购买', expires: '到期',
  expiry_optional: '选填。留空就不会为这件东西提醒你。',
  expiry_2w: '2 周', expiry_1m: '1 个月', expiry_3m: '3 个月', clear: '清空',
  use_some: '用掉一部分',
  merged_into: '已合并到{0}里原有的那一条',
  delete_title: '删除这一条？',
  delete_body: '{0} 会被彻底移除，也不会计入浪费统计。如果是扔掉了，请用列表上的垃圾桶。',
  scan_title: '从小票添加',
  scan_how: 'iPhone 上：打开小票照片（或者用相机对着小票），长按文字选中，点"全选"再点"拷贝"，' +
    '然后粘贴到下面。',
  scan_paste: '粘贴小票文字',
  scan_read: '识别',
  scan_header: '{0} · {1} · 选中 {2} / {3}',
  scan_date_unknown: '未识别到日期',
  scan_no_lines: '没找到商品行。检查一下价格那一列有没有一起复制进来。',
  scan_select_all: '全选', scan_select_none: '全不选',
  scan_add: '加入 {0} 件', scan_added: '已加入 {0} 件', scan_again: '重新开始',
  no_price: '无价格',
  history_title: '历史',
  history_empty: '还没有东西离开厨房。',
  history_hint: '列表上每次按 −、✓ 或垃圾桶都会记录在这里。',
  history_empty_filtered: '还没有这类记录。',
  this_week: '本周', last_week: '上周',
  waste_window: '最近 30 天', waste_eaten: '吃掉 {0}', waste_thrown: '扔掉 {0}',
  waste_percent: '浪费 {0}%', waste_none: '数据还不够',
  waste_money: '扔掉了 {0}', waste_by_category: '最常扔掉的分类', waste_by_store: '按店铺',
  settings_title: '设置', language: '语言', lang_auto: '跟随系统',
  backup: '备份',
  backup_desc: '把全部数据导出成 JSON 文件，或者从文件导回来。和 Android 版用的是同一种文件。' +
    '数据只存在这个浏览器里，记得时常导出。',
  export: '导出', import: '导入',
  exported: '已导出 {0} 条', imported: '已导入 {0} 条',
  import_failed: '这个文件读不出来。{0}',
  import_title: '怎么导入？',
  import_body: '合并：把文件加到现有数据上。覆盖：先清空这里的全部记录，从别的手机搬过来时用这个。',
  merge: '合并', replace: '覆盖全部',
  receipt_text: '小票文字',
  receipt_text_desc: '把加过的每张小票的文字存成文件。哪张识别得不好，就把文件发出来。',
  export_receipt_text: '导出小票文字', receipts_exported: '已导出 {0} 张小票',
  backup_nag: '上次备份：{0}。数据只存在这个浏览器里。',
  backup_never: '还没备份过。数据只存在这个浏览器里。',
  backup_now: '去备份',
  storage_persistent: '空间不足时浏览器也会保留这些数据。',
  storage_best_effort: '把这个页面"添加到主屏幕"，iOS 才会长期保留数据。',
  privacy: '数据不会离开这台手机。App 离线可用，不向任何地方发送数据。',
  storage_error: '这个浏览器不允许保存数据（无痕模式？）。关掉页面后改动会丢失。',
  labels: '分区标签',
  labels_desc: '每个分区一张卡，A4 一页十张。打印出来（或在打印界面存为 PDF），剪开贴在冰箱门里。',
  labels_count: '{0} 条 · {1} 页',
  labels_empty_card: '这格是空的，打印出来手写。',
  labels_extra: '额外卡片',
  labels_print: '打印 / 存为 PDF',
  zone_dairy_eggs: '乳制品 · 蛋', zone_vegetables: '蔬菜', zone_fruit: '水果',
  zone_tofu_soy: '豆制品', zone_prepared: '即食 · 加工', zone_meat: '生鲜肉类',
  zone_seafood: '海鲜 · 水产', zone_snacks: '冰淇淋 · 甜点', zone_frozen_staples: '速冻主食',
};

let current = en;
let currentLang = 'en';

/** 'auto' follows the phone's language. */
export function setLanguage(choice) {
  const lang = choice === 'zh' || choice === 'en'
    ? choice
    : ((navigator.language || 'en').toLowerCase().startsWith('zh') ? 'zh' : 'en');
  current = lang === 'zh' ? zh : en;
  currentLang = lang;
  document.documentElement.lang = lang === 'zh' ? 'zh-Hans' : 'en';
}

export function lang() {
  return currentLang;
}

export function t(key, ...args) {
  const text = current[key] ?? en[key] ?? key;
  return text.replace(/\{(\d)\}/g, (_, i) => String(args[Number(i)] ?? ''));
}

export const KEYS = { en, zh };
