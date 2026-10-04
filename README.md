# AdKiller —— 广告奖励放大器 (Xposed 模块)

通杀四大广告联盟，放大 eCPM 提高看广告奖励。

## 支持

| 联盟 | 类名前缀 |
|---|---|
| 穿山甲 Pangle | `com.bytedance.sdk.openadsdk.` / `com.byazt.` |
| 优量汇 GDT | `com.qq.e.` |
| 快手 KS | `com.kwad.` |
| 百度 | `com.baidu.mobads.` |
| Sigmob | `com.sigmob.` |
| GroMore 聚合 | `com.bytedance.msdk.` |
| 自研框架(吉人天相) | `com.zygote.` |

## 原理

1. **动态类加载拦截**：Hook `ClassLoader.loadClass`，广告 SDK 类加载时自动扫描 `getECPM/getEcpm` 方法，Hook 返回值放大。
2. **吉人天相专项**：`RenderAdData.ecpm` 字段/构造/setEcpm 三保险；`SdkLoaderGromore.getMaxECpm` 双保险。
3. eCPM 在 App 做 RSA 签名**之前**被放大 → App 自己签名 → 服务端验签通过。

## 使用

1. 安装 APK，打开一次“AdKiller”激活模块
2. LSPosed 里启用模块 + 勾选目标 App（吉人天相/墨染流年/慧竞）
3. 打开模块界面调好倍率（建议先 x10）→ 保存
4. 强制停止并重启目标 App
5. 看广告测试奖励变化

## 编译

```bash
gradle assembleDebug
```

GitHub Actions：push 后自动编译，Artifacts 下载 APK。

## 免责

仅供学习研究。
