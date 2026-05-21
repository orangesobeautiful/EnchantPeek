# EnchantPeek

[English](../../README.md)

EnchantPeek 是一個 Fabric 用戶端模組，可以在你花費青金石與等級之前，先顯示附魔台目前選項會產生的完整附魔結果。

這個模組可以在單人遊戲和多數多人伺服器上使用一般用戶端預測。伺服器也可以安裝選用的輔助外掛，讓有安裝本模組的玩家收到精確的伺服器端附魔預覽。

## 需求

- Minecraft 1.21.11
- Fabric Loader 0.18.4 或更新版本
- Fabric API
- Java 21

## 安裝

安裝 Minecraft 1.21.11 對應的 Fabric Loader 與 Fabric API，然後把 EnchantPeek jar 放進用戶端的
`mods` 資料夾。

## 選用的伺服器端外掛

Paper 輔助外掛不是必要安裝項目。它可以讓伺服器把以伺服器端計算出的附魔預覽傳送給已安裝 EnchantPeek 的 Fabric
用戶端，也可以選擇在玩家把新物品放進附魔台時刷新玩家的附魔種子。
