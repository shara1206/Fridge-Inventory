# iOS 版 —— 交接说明

写于 2026-08-02，Windows 上的 Cowork 会话。**代码尚未开始写**，这份文件只是把已经定下来的决策交接给 Mac 上的会话。

## 在 Mac 上怎么开始

把这个仓库同步到 Mac，在 Cowork 里打开 `Fridge` 文件夹，然后说：

> 读一下 IOS-HANDOFF.md，按里面的方案开始做 iOS 版。

## 已确认的决策

| 项目 | 决定 |
|---|---|
| 技术路线 | 原生 SwiftUI + SwiftData，独立 Xcode 工程 |
| 存放位置 | 新建 `Fridge iOS/` 目录，**不动 `Fridge Inventory/`（Android 工程）** |
| 功能范围 | 全部对齐 Android 版 |
| 分发方式 | 暂不上架。免费 Apple ID 自签装到自己设备（7 天一签）；以后要免重签再考虑 $99 账号走 TestFlight |
| 设备 | iPhone + iPad 都要能用（iPad 需要分栏适配） |

## 为什么不做 PWA

一度考虑过网页版 PWA（不需要 Mac、不会过期）。因为 Shara 有 Mac，改回原生：能用 Vision 做端上 OCR、能用真正的本地推送通知，功能可以和 Android 版一一对齐。

## 移植时要注意的对应关系

Android 工程约 6600 行 Kotlin，`Fridge Inventory/app/src/main/java/com/sharawang/fridge/` 下：

- **Room → SwiftData**：`data/local/` 里的 `FoodItem`、`Purchase`、`Enums`、`Converters`、`Migrations`
- **ML Kit 端上 OCR → Vision `VNRecognizeTextRequest`**：`receipt/ReceiptOcr.kt`。Vision 是系统框架，不用打包模型，仍然全程离线
- **WorkManager 每日提醒 → `UNUserNotificationCenter`**：`notify/` 整个目录。注意保持"默认关闭、打开时才请求权限"的行为
- **Android `PdfDocument` → Core Graphics PDF**：`data/labels/LabelPdf.kt`，A4 一页十张卡片，矢量绘制
- **`values/` + `values-zh/` → `Localizable.xcstrings`**：中英双语
- **备份 JSON 格式必须保持双向兼容**：`data/backup/InventoryBackup.kt`，format 字段是 `"fridge-inventory"`。仓库根目录的 `fridge-inventory-2026-07-31.json` 是真实样本，可以直接拿来测导入
- 小票解析器（H-Mart / T&T / Trader Joe's / Whole Foods / Generic）逻辑纯字符串处理，直译即可；Android 侧有单测，Swift 侧应当照抄一份

## 几条容易丢掉的产品行为

从 Android README 里挑出来的，移植时别简化掉：

- 只有**7 天内到期或已过期**才打标；没填日期的条目永远不提醒
- 列表上的 `−/+` 是**事件**（买了一个 / 吃了一个，会写历史、会改采购日期）；编辑页里的同样按钮是**纠错**（只改数字，不写历史）
- 到期日**可以为空且默认为空**，不猜一个假日期
- 分类筛选行只显示厨房里实际存在的分类
- 每一次移除都要能撤销（吃掉 / 丢弃两种结局，喂给浪费报表）
- 重复购买合并进已有行，不堆重复条目
- Morandi 配色：米色底 + 蓝色点缀，不使用系统动态取色
