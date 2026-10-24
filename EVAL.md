# KillMyApps × Shizuku 一体 App 评估报告

> 日期：2026-10-24（Asia/Shanghai）  
> 范围：把 Shizuku 能力合进 KillMyApps **同一个 APK**（不是第三个无关产品）  
> 仓库只读分析：`/workspace/killmyapps-shizuku/KillMyApps`（GPL-3.0）、`/workspace/killmyapps-shizuku/Shizuku`（Apache-2.0）  
> 约束：不 merge 上游 main；改动保持可审计

---

## 0. 现状（重要）

**KillMyApps v1.6 已经是标准的 Shizuku 客户端，不是「还没接 Shizuku」。**

证据（源码）：

| 项 | 现状 |
|---|---|
| 依赖 | `dev.rikka.shizuku:api:13.1.0` + `provider:13.1.0`（`gradle/libs.versions.toml`） |
| Manifest | 已声明 `rikka.shizuku.ShizukuProvider`，authority `${applicationId}.shizuku` |
| 杀进程 | `ShizukuUtils`（`Shizuku.newProcess` 跑 `am force-stop` 等）、`ShizukuPlusUtils` + `ShizukuUserService`（`bindUserService`） |
| 引擎选项 | Root / Shizuku / Shizuku+（设置页 Radio） |
| 包名 | `dedeadend.killmyapps`，versionCode 160 |

用户本机已装 v1.6，**未装 Shizuku** → 杀应用功能在无 Root 时必然不可用。  
用户真正想要的是：**少装一个 App，打开一个 APK 就能完成「起提权服务 + 列表杀应用」**。

官方 Shizuku-API 文档明确要求：*guide the user to install Shizuku or Sui first*。客户端库本身**不包含** manager/server。

---

## 1. 可行路径对比

### A) 同一 APK 内嵌完整 Shizuku manager/server

**做法概要**：把 RikkaApps/Shizuku 的 `manager` + `server` + `starter`（含 JNI：`starter.cpp`、`adb_pairing.cpp`、SELinux/cgroup）整进 KillMyApps 工程，改包名/入口，做成「带内置 Shizuku 控制台的杀应用 App」。

| 维度 | 评估 |
|---|---|
| 实现难度 | **极高**。Shizuku 是多模块工程（manager/server/starter/shell/common + native + ADB pairing）；大量硬编码包名 `moe.shizuku.privileged.api`、权限与 Provider；跟 AGP/Kotlin/Refine/HiddenAPI 栈绑定。合进现有 Java + AGP 9.2 的 KillMyApps 等于维护一份 fork 的 Shizuku。 |
| 重启后是否仍需无线调试/ADB | **是（无 Root）**。嵌入不改变模型：server 必须以 shell(UID 2000) 或 root 身份经 ADB/`app_process` 拉起；重启后 adbd 特权进程消失，需再次配对/启动。Android 11+ 可机内无线调试，但步骤仍在。 |
| 与「单独装 Shizuku」利弊 | **弊大于利**：APK 体积暴涨；与已装官方 Shizuku 包名/服务冲突风险；安全面（把跨 App 提权 broker 塞进杀进程工具）过大；升级跟不上上游；调试面爆炸。利：表面上「一个图标」。 |
| 是否真满足「一个 APK」 | **表面满足**。运行时仍依赖「开发者选项 + 无线调试」；不是「装完即永久提权」。 |

**结论**：工程上能硬做，但**不推荐**作为产品路径。

---

### B) 依赖 Shizuku API + 应用内引导启动（仍可能要用户先起 Shizuku / ADB）

**做法概要**：保持现有 `api`/`provider` 客户端；加强引导 UI（检测未安装 → 跳下载；已装未运行 → 跳转 Shizuku / 展示无线调试步骤；已运行未授权 → `requestPermission`）；首页状态条实时显示 Binder 是否活着。

| 维度 | 评估 |
|---|---|
| 实现难度 | **低**。业务杀逻辑已齐；缺的是状态机 UX 与深链引导。改动集中在设置/首页与少量工具类。 |
| 重启后是否仍需无线调试/ADB | **是**。由外部 Shizuku 承担；KillMyApps 只检测 `Shizuku.pingBinder()`。 |
| 与「单独装 Shizuku」利弊 | **利**：与官方生态一致、体积小、可复用已装 Shizuku 给别的 App、维护成本低。**弊**：仍是两个 APK，不完全满足「合进同一个 App」的字面目标。 |
| 是否真满足「一个 APK」 | **否**。能力在，交付形态仍是「KillMyApps + Shizuku」。 |

**结论**：最快可用、风险最低；若用户能接受「多装一个官方 Shizuku」，应优先做引导体验而不是嵌 server。

---

### C) 中间方案：自包含特权栈（单租户，不做成通用 Shizuku）

**做法概要**：不嵌完整 Shizuku manager，而在 KillMyApps 内实现「迷你提权宿主」：

1. 应用内 **Wireless ADB 配对/连接**（Android 11+，本机连 `adbd`）；
2. 经 ADB shell 用 `app_process` 拉起 **仅服务本 App** 的特权进程（跑现有 `ShizukuUserService` 同类逻辑，或直接 `am force-stop`）；
3. App 通过自有 Binder/socket 连上该进程执行杀列表；
4. （可选）若检测到系统已装官方 Shizuku 且 Binder 存活，则回退走现有 `ShizukuUtils` 路径。

业界参照：Portage 的 self-contained ADB bridge ADR、Castla「internalize Shizuku privileged layer」、开源库 **Priv Kit**（Root/ADB/手动 shell 启特权进程）。

| 维度 | 评估 |
|---|---|
| 实现难度 | **中高**。比 A 小一个数量级（不需要跨 App 授权中心、管理 UI、多客户端协议），但仍要：ADB TLS 配对、本机 adbd 连接、starter/`app_process`、Binder 握手、死后重连、各 OEM 无线调试差异。可考虑接入 Priv Kit 降低自研量。 |
| 重启后是否仍需无线调试/ADB | **是（无 Root）**。物理限制不变；可把「输入配对码 → 一键启动服务」做成应用内向导，降低摩擦，但无法省掉重启后的再启动。 |
| 与「单独装 Shizuku」利弊 | **利**：真正一个 APK；UX 可针对「杀应用」线性化；版本与 APK 绑定。**弊**：自研/维护 ADB 栈；不自动惠及其他 App；OEM 坑要自己扛；安全上需做「仅本 App 可绑」的鉴权。 |
| 是否真满足「一个 APK」 | **是**（安装形态）。运行前提仍是无线调试/ADB（无 Root 时）。 |

**结论**：若「一个 APK」是硬需求，这是**现实可选路径**；比硬嵌完整 Shizuku 更干净。

---

### 路径对照一览

| | A 完整嵌入 Shizuku | B API + 引导 | C 自包含特权栈 |
|---|---|---|---|
| 安装几个 APK | 1 | 2（KMA + Shizuku） | 1 |
| 无 Root 重启后要 ADB/无线调试 | 要 | 要 | 要 |
| 实现难度 | 极高 | 低 | 中高 |
| 与现有 KMA 代码契合 | 差（大移植） | 极好（已是客户端） | 中（保留杀逻辑，换启动层） |
| 维护/安全面 | 差 | 好 | 中 |
| 推荐度 | ❌ | ✅ 短期 | ✅ 中期（要「一个 APK」时） |

---

## 2. 许可证（工程可执行注意点）

- **KillMyApps = GPL-3.0**；**Shizuku / Shizuku-API = Apache-2.0**。
- Apache-2.0 **可以**合进 GPL-3.0 工程：合体后的分发物按 **GPL-3.0** 义务对外；同时必须保留 Apache 作品的 **版权与 NOTICE/许可证副本**（归因）。
- **Maven 依赖** `dev.rikka.shizuku:api` / `provider`（路径 B）：分发 APK 时附上依赖许可证清单即可；源码仓库 README/NOTICE 写明使用了 Shizuku API。
- **源码级拷贝** Shizuku manager/server/jni（路径 A）或抄其 starter/配对实现（路径 C 若复制而非重写）：
  - 拷贝文件头版权保留；
  - 根目录 `NOTICE` 列明来源与 Apache-2.0；
  - **整体对外仍以 GPL-3.0** 提供对应源码（GPL 传染合体）；
  - 不要删上游版权声明，不要把「仅 Apache」说成整包协议。
- **再分发 APK 时**：提供获取对应源码的方式（Git 仓库地址/附源码包）；若改了 GPL 宿主或合入的 GPL 代码，源码必须是改过之后的版本。
- **不要**把合体产物说成「Apache 许可 App」；也不要假装可以闭源分发（GPL 宿主不允许）。
- 包名/商标：不要冒充官方「Shizuku」上架名；UI 文案写「基于 Shizuku 协议 / 兼容 Shizuku」或「内置提权服务」更稳。

（以上为工程合规检查清单，不是法律意见。）

---

## 3. 推荐方案

**推荐：短期走 B（强化引导）；若用户坚持「同一个 APK、不再装 Shizuku」，中期走 C（自包含特权栈）。明确不推荐 A（硬嵌完整 Shizuku）。**

理由：

1. **v1.6 客户端已就绪**，B 的缺口是 UX，不是能力；MI PAD 4 PLUS 已通无线 ADB，装官方 Shizuku 后当天就能验「列表杀应用」。
2. **A 的成本/冲突/安全面不成比例**，且**解决不了**「重启仍要 ADB」这一用户痛点——嵌了也白嵌核心摩擦。
3. **C 才对准「一个 APK」**：只做单租户提权，体积与复杂度可控；可与 B 并存（检测到外部 Shizuku 则复用）。

不推荐硬嵌完整 Shizuku 的充分理由：官方设计就是「独立 manager + 多 App 共享 server」；嵌入等于 fork 整条产品线；包名/权限/升级/与官方并存全部是坑；无 Root 重启模型不变。

---

## 4. UI 信息架构草案（一体 App）

面向 **B 强化版** 或 **C 完成后的一体壳**；底栏可保持现有「首页 / 排除 / 设置」，顶部增加全局状态。

### 4.1 全局状态条（首页顶）

```
┌──────────────────────────────────────────┐
│ ❄ KillMyApps                             │
├──────────────────────────────────────────┤
│ ● 提权服务：未运行          [启动向导]   │  ← 红/黄/绿
│   引擎：Shizuku+ · 深度强杀              │
└──────────────────────────────────────────┘
```

状态枚举：

- 绿：Binder 活 + 已授权 → 可杀  
- 黄：已安装/可连但未授权 → 一键 `requestPermission`  
- 红：未安装 / 未运行 / Binder 死 → 进「启动向导」

### 4.2 启动向导（独立全屏或 BottomSheet）

```
┌──────────────────────────────────────────┐
│ 启动提权服务                         [×] │
│                                          │
│ ① 打开「开发者选项 → 无线调试」   [去设置]│
│ ② 输入配对码 / 一键配对           [配对] │  ← C 方案才有机内配对
│ ③ 启动服务                        [启动] │
│ ④ 授权 KillMyApps                 [授权] │
│                                          │
│ ── 或 ──                                 │
│ 已安装官方 Shizuku？          [打开 Shizuku]│ ← B / C 回退
│ 下载 Shizuku                  [浏览器]   │
└──────────────────────────────────────────┘
```

B 方案：②③ 主要是跳转官方 Shizuku + 文案说明「每次重启后要在 Shizuku 里点启动」。  
C 方案：②③ 在本 App 内完成配对与 `app_process` 拉起。

### 4.3 可杀应用列表（现有首页增强）

```
┌──────────────────────────────────────────┐
│ [状态条见上]                             │
│ 运行中 · 用户应用            共 12 个    │
│ ┌──────────────────────────────────────┐ │
│ │ ☑ App A          com.foo     [杀死] │ │
│ │ ☑ App B          com.bar     [杀死] │ │
│ │ ☐ 系统关键…（已隐藏）               │ │
│ └──────────────────────────────────────┘ │
│                                          │
│              [ 一键杀死所选 ]             │
└──────────────────────────────────────────┘
```

无提权时：主按钮禁用，点按弹出启动向导（替代现在偏静态的 InfoDialog）。

### 4.4 设置

```
引擎： ( ) Root  (•) Shizuku  ( ) Shizuku+
强杀级别：柔和 / 深度 / Killer
……（现有排除、自动杀、主题等不变）
── 提权 ──
服务状态：运行中 / 已死
[重新启动服务]   [忘记配对]   [仅用外部 Shizuku]
```

---

## 5. 落地计划

### 5.1 若下一步改 fork：最小改动集

**阶段 0 — 验证基线（不改代码，半天内）**

1. 在 MI PAD 4 PLUS 上安装官方 Shizuku；
2. 无线调试启动 Shizuku；
3. 打开已装 KillMyApps v1.6，选 Shizuku/Shizuku+，授权；
4. 确认「列表杀应用」Pass。  
→ 证明：**业务路径已通，缺的是分发形态/引导，不是杀逻辑。**

**阶段 1 — Fork 做 B（推荐立刻做的最小集）**

Fork `dedeadend/KillMyApps` 到自有命名空间（**不要 PR/merge 上游 main**）：

| 改动 | 文件（约） | 说明 |
|---|---|---|
| 状态检测 | 新 `PrivilegeStatus` + 改 `HomeFragment` | `pingBinder` / 权限 / 是否安装 `moe.shizuku.manager` |
| 状态条 UI | `fragment_home.xml` | 见 §4.1 |
| 启动向导 | 新 Fragment/Dialog | 跳转设置、Shizuku 包、下载页 |
| 文案 | `strings.xml` / `dialog_info` | 写清「重启后需重新启动 Shizuku」 |
| 包名（可选） | `applicationId` | 若要与已装 v1.6 并存，用新 id；否则覆盖安装注意签名 |

预计工作量：约 1–2 人日出可装 debug 包。

**阶段 2 — 若要坚持一个 APK：在同一 fork 上做 C**

| 改动 | 说明 |
|---|---|
| 引入 ADB 配对/连接模块 | 自研或评估 Priv Kit |
| 迷你 server 入口 | 复用/改编 `ShizukuUserService` 杀命令，经 `app_process` 跑在 shell uid |
| 替换/旁路 `Shizuku.pingBinder` 启动链 | 自有 Binder；保留外部 Shizuku 回退 |
| 向导接真配对 | MI PAD 4 PLUS 无线调试端到端 |

预计工作量：数人日到两周（含 OEM 坑）；**明显小于完整嵌入 A**。

### 5.2 无 Root 的 MI PAD 4 PLUS（无线 ADB）能否验证？

| 目标 | B | C |
|---|---|---|
| 启动服务 | 经官方 Shizuku + 无线调试 | 经 App 内配对 + 无线调试 |
| 列表杀应用 | ✅ 现有代码即可 | ✅ 需新 server 通后再接现有杀命令 |
| 本环境条件 | 平板无线 ADB 已通（txb 上验证过） | 同上，还需实现配对客户端 |

**可以验证**，前提是阶段 0/1 先装上 Shizuku（B），或阶段 2 完成机内启动（C）。  
云端 box 无真机；验证应在用户机器 `txb` + 平板无线调试上进行（与现有 adb 流程一致）。

---

## 6. 总结表

| 问题 | 答案 |
|---|---|
| 现在缺什么？ | 缺「提权服务在本机跑起来」；杀逻辑与 Shizuku API 已在 v1.6 |
| 一个 APK 能否免去每次重启 ADB？ | **不能**（无 Root）；只能把步骤收进 App |
| 推荐？ | **B 短期 +（可选）C 中期；不做 A** |
| 许可证？ | Apache 合入 GPL 宿主可行；合体按 GPL-3.0 分发 + 保留 Apache NOTICE |
| 立刻 fork？ | **是（为 B）**；为 A 则否 |

---

## 附录：本地克隆路径

```
/workspace/killmyapps-shizuku/
├── EVAL.md                 ← 本报告
├── KillMyApps/             ← github.com/dedeadend/KillMyApps（depth 1）
└── Shizuku/                ← github.com/RikkaApps/Shizuku（depth 1）
```
