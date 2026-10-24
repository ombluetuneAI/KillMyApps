# Path C — 自包含特权栈（无需官方 Shizuku APK）

日期：2026-10-24（Asia/Shanghai）

## 结论

**真正零官方 Shizuku 依赖已落地并可在 MI PAD 4 PLUS 上验证。**

| 项 | 值 |
|---|---|
| APK | `/workspace/killmyapps-shizuku/app-c-debug.apk`（约 18MB） |
| applicationId | `dedeadend.killmyapps.oneshot` |
| versionName / code | `1.7.0-privkit` / `170` |
| 默认引擎 | Built-in (ADB) = killerMode `4` |
| 特权库 | [Priv Kit](https://github.com/priv-kit/priv-kit) `priv-core:0.17.8`（Apache-2.0） |
| 上游 | **未** merge／push |
| 验证时 `moe.shizuku.privileged.api` | **已卸载**（`pm path` 失败） |

## 架构

```
┌─────────────────────────────────────────────┐
│ KillMyApps oneshot APK（单包）               │
│  UI：状态条 / 配对码 / Start / Manual 复制   │
│  Killer mode 4 → PrivKitUtils                │
│       ↓ Privilege.startCommand(sh -c …)      │
│  Priv Kit runtime（进程内 Binder 客户端）     │
└──────────────────┬──────────────────────────┘
                   │ Handshake Provider
                   │ ${applicationId}.privilege.handshake
                   ▼
┌─────────────────────────────────────────────┐
│ Privileged Server（shell uid 2000）          │
│  由 libprivkitstarter.so 经 ADB/`app_process`│
│  拉起；仅服务本 App（单租户）                 │
│  执行 am force-stop / am kill 等              │
└─────────────────────────────────────────────┘
```

启动方式（应用内向导）：

1. **Wireless debugging 配对**：开发者选项 → 无线调试 → 配对码 → App 内「Pair with code」→ `Privilege.createAdbManager().pair()`
2. **Start service**：`Privilege.startAdb()`（本机连 adbd，拉起私有 server）
3. **Manual 回退**：App 写入 `Android/data/.../files/privkit-starter.txt`；PC 已 adb 时可 `adb shell $(cat …/privkit-starter.txt)`

可选回退：设置里仍保留 Root / Shizuku / Shizuku+（需官方 Shizuku 时）。

## 重启后仍需无线调试（无 Root 物理限制）

嵌入 Priv Kit **不能**省掉「重启后再起特权进程」：

- 重启后 adbd／shell 特权进程消失；
- 需再次打开「无线调试」并在本 App 点 **Start**（或 PC Manual 再跑 starter）；
- 首次或授权撤销后可能要重新配对。

文案已写在向导与设置提示中。

## 如何启动服务（操作摘要）

### A. 纯手机（推荐日常）

1. 开发者选项 → 打开「无线调试」
2. 打开 KillMyApps oneshot → 状态红「not running」→ Start／向导
3. 首次：在系统「使用配对码配对设备」页输入 6 位码 → App「Pair with code」
4. 「Start service」→ 状态变绿「Privilege: ready · Built-in」
5. 列表杀应用

### B. PC 已无线 adb（本次验证路径）

```text
adb install -r app-c-debug.apk
adb shell am start -n dedeadend.killmyapps.oneshot/.MainActivity
# 等 App 写出 starter
adb shell cat /sdcard/Android/data/dedeadend.killmyapps.oneshot/files/privkit-starter.txt
adb shell /data/app/.../lib/arm64/libprivkitstarter.so
# 冷启 App 完成 Handshake 后即可杀进程
```

Debug 杀进程广播（仅 debug APK，与 UI 同一 `Killer`/`PrivKitUtils` 路径）：

```text
adb shell am broadcast -n dedeadend.killmyapps.oneshot/.receiver.DebugKillReceiver \
  -a dedeadend.killmyapps.oneshot.ACTION_DEBUG_KILL --es pkg fun.upup.musicfree
```

## 平板验证（txb · MI PAD 4 PLUS · LeanOS）

| 步骤 | 结果 |
|---|---|
| 卸载 `moe.shizuku.privileged.api` | Success |
| 安装 oneshot `1.7.0-privkit` | Success |
| Manual starter 拉起 Priv Kit server | `starter exit with 0`；log：Handshake accepted |
| UI 状态 | **Privilege: ready / Built-in · Force-stop is available**（`qa-c/home-ready.png`） |
| 杀 `fun.upup.musicfree`（DebugKill → Killer → PrivKit） | pid 有 → `pidof` 空 | Pass |
| 验证全程无官方 Shizuku 包 | `pm path` 失败 | Pass |

截图目录：`/workspace/killmyapps-shizuku/qa-c/`

## 主要改动（本地 fork `KillMyApps/`）

- 依赖：`io.github.priv-kit:priv-core:0.17.8`、`hiddenapibypass`、coroutines；AGP 9 内置 Kotlin + KGP 2.4.20
- 新增：`util/PrivKitBridge.kt`、`util/PrivKitUtils.java`、`receiver/DebugKillReceiver.java`
- 更新：`PrivilegeStatus` / `Killer`（mode 4）、`ShizukuGuideDialog`（配对+Start）、设置 Radio「Built-in (ADB)」、`App.attachBaseContext` HiddenApiBypass、`NOTICE`
- `applicationId` → `.oneshot`（与已装 v1.6／integrated 并存）

## 剩余限制

1. **无 Root 重启后必须再起服务**（无线调试 + Start／Manual）
2. 部分 OEM 无线调试／配对差异由 Priv Kit 承担；本机为 MI PAD 4 PLUS + 无线 adb 已通
3. `startAdb()` 机内自启依赖无线调试已开且（首次）已配对；本次验证用 Manual starter（同等 shell 特权，仍零 Shizuku）
4. DebugKillReceiver 仅 debug 包；正式分发应去掉或加签名鉴权
5. 合体分发物整体 **GPL-3.0**，须保留 Priv Kit／Shizuku API 的 Apache NOTICE

## 与路径 B 对比

| | Path B (`app-debug.apk` / `.integrated`) | Path C (`app-c-debug.apk` / `.oneshot`) |
|---|---|---|
| 是否要装官方 Shizuku | **要** | **不要** |
| 提权谁提供 | 外部 Shizuku server | 本 App Priv Kit server |
| 重启后再起 | 在 Shizuku 里 Start | 在本 App 里 Start |
