# LAN Board Game v0.6.0 开发交接文档

> 交接用途：将当前 Android 局域网桌游项目移交给其他 AI 或开发者继续开发。
> 文档日期：2026-10-01
> 当前可构建版本：`0.6.0`，Android `versionCode = 9`
> Android 包名：`com.lanboardgame.host`

## 1. 项目目标

一台 Android 手机作为房主设备和本地服务器。其他玩家连接同一 Wi-Fi 或房主热点，通过浏览器访问房主手机的局域网地址并加入游戏。

核心约束：

- 不依赖电脑部署；
- 不依赖云服务器；
- 没有互联网时也必须可以创建房间和游戏；
- 只有房主需要安装 APK；
- 玩家优先使用 Android Chrome 加入；
- 游戏规则、秘密身份和手牌必须由房主手机中的服务端控制；
- 架构需支持继续添加狼人杀、UNO、斗地主等游戏。

当前包含：

1. 阿瓦隆（5–10 人）；
2. 掼蛋（4 人）；
3. TimeBomb（4–8 人）；
4. Crossfire（5–10 人）；
5. Good Cop Bad Cop（4–8 人）；
6. 骰子模拟器（2–10 人）；
7. 德州扑克（2–8 人）。

## 2. 当前版本状态

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| Android WebView 宿主 | 已完成 | Kotlin，打开本机 React 客户端 |
| Foreground Service | 已完成 | 保持 HTTP/WebSocket 服务运行 |
| 启动等待与自动重试 | 已完成 | 避免服务未启动时出现 `ERR_CONNECTION_REFUSED` |
| HTTP/WebSocket | 已完成 | NanoHTTPD + NanoWSD，监听 `0.0.0.0` |
| IP/端口部署页 | 已完成 | 首页选择局域网 IPv4、端口并点击“部署上线” |
| 房间/玩家/Session | 已完成 | 创建、加入、准备、改名、踢人、Session 重连和按昵称恢复 |
| 返回/解散房间 | 已完成 | 房主返回解散全房间，其他玩家返回只退出本人 |
| 部署开关 | 已完成 | 上线绑定 `0.0.0.0`，关闭后仅绑定 `127.0.0.1` |
| 阿瓦隆引擎与 UI | 已完成 | 身份、可见性、投票、任务、刺杀、历史记录 |
| 掼蛋引擎与 UI | 已完成 | 四人两副牌、级牌/逢人配、完整牌型与特殊牌力、接风、排名、进贡/还贡/抗贡、升级和 A 级状态 |
| TimeBomb 引擎与 UI | 已完成 | 隐藏阵营、私密线路、剪线、轮次和胜负 |
| Crossfire 引擎与 UI | 已完成 | 初始身份、全员向左传一次、庄家起每隔 3 座位的环形相邻三人混洗、私密混洗记录、讨论倒计时、同时选目标和结算 |
| Good Cop Bad Cop 引擎与 UI | 已完成 | 身份牌、调查、装备、武装、射击和淘汰 |
| 骰子模拟器引擎与 UI | 已完成 | 纯多人骰子模拟器：房主统一配置 1–10 颗骰子、全量重掷、全员结算、结果统计和全员确认下一局 |
| 德州引擎与 UI | 已完成 | 下注、All-in、主池、多个边池、平分池 |
| 测试机器人 | 已完成 | 仅服务器未部署时显示和使用；房主可批量添加/移除；真人动作后零延迟随机执行合法动作，复用真人状态机 |
| 独立游戏桌面 | 已完成 | 七款游戏按机制分别布局；掼蛋、Crossfire、Good Cop Bad Cop、德州支持横屏双栏桌面 |
| 无互联网运行 | 已完成代码修复，待真机回归 | 无 LAN IP 时可仅本机创建；无互联网 Wi-Fi/热点仍需多厂商验收，见第 11 节 |
| Release 构建 | 已完成 | 启用 R8、资源压缩和系统字体；项目 Release keystore 仍需由维护者配置 |
| 进程死亡恢复 | 未完成 | 游戏状态当前只保存在内存中 |

重要说明：v0.6.0 在 v0.5.0 基础上完成七款游戏的独立桌面布局、横屏适配、测试机器人即时随机行动、新应用图标和包体清理。按昵称恢复离线座位、5 秒断线提示延迟和 WebSocket 应用层心跳保持不变。代码级验证完成后，仍需在原问题设备及 Android 多厂商设备上完成 A–D 场景真机回归。

## 3. 技术栈

### Android

- Kotlin 2.1.20
- Android Gradle Plugin 8.9.1
- JDK 17（可同时保留 JDK 21；Android Studio 的 Gradle JDK 选择额外安装的 JDK 17）
- compileSdk / targetSdk 35
- minSdk 26
- Android WebView
- Foreground Service
- NanoHTTPD / NanoWSD 2.3.1
- ZXing Core 3.5.3
- kotlinx.serialization 1.8.0

### Web

- React 19
- TypeScript 5.7
- Vite 6
- Vitest 3
- v0.6.0 使用项目内原生 CSS 与 Android/WebView 系统中文字体，运行时不访问互联网，也不打包 Noto Sans SC 字体或未使用的 Tailwind 工具链

### 数据

- 房间和游戏状态保存在房主进程内存中；
- Session 使用浏览器 `localStorage`；
- 当前不使用 Room、SQLite 或外部数据库。

## 4. 运行架构

```text
Android APK
├─ MainActivity / WebView
├─ GameServerService (Foreground Service)
├─ LocalGameServer
│  ├─ HTTP 静态资源
│  ├─ WebSocket 协议路由
│  └─ QR Code
├─ RoomManager / Session
├─ AvalonGame
├─ GuandanGame
├─ TimeBombGame
├─ CrossfireGame
├─ GoodCopBadCopGame
├─ LiarDiceGame
└─ PokerGame

房主 WebView ── http://127.0.0.1:<port>
玩家 Chrome  ── http://<房主局域网IPv4>:<port>
```

服务器绑定 `0.0.0.0`。部署页选择的 IP 只用于对外显示、二维码和访问地址，不是服务器 Socket 的绑定地址。

## 5. 工程目录

```text
lan-board-game/
├─ android/app/
│  ├─ src/main/kotlin/com/lanboardgame/host/
│  │  ├─ MainActivity.kt
│  │  └─ server/
│  │     ├─ GameServerService.kt
│  │     ├─ HostServerConfig.kt
│  │     ├─ LocalGameServer.kt
│  │     └─ NetworkManager.kt
│  └─ src/main/assets/web/       # 打包进 APK 的前端产物
├─ server-core/src/main/kotlin/com/lanboardgame/
│  ├─ platform/                  # Room、Player、Session、RoomManager
│  ├─ protocol/                  # Socket Message 和事件名
│  └─ games/
│     ├─ avalon/
│     ├─ guandan/
│     ├─ timebomb/
│     ├─ crossfire/
│     ├─ goodcopbadcop/
│     ├─ liarsdice/
│     └─ poker/
├─ server-core/src/test/         # Kotlin/JUnit 测试
├─ web/src/
│  ├─ pages/                     # Home、Lobby
│  ├─ games/avalon/
│  ├─ games/guandan/
│  ├─ games/timebomb/
│  ├─ games/crossfire/
│  ├─ games/goodcopbadcop/
│  ├─ games/liarsdice/
│  ├─ games/poker/
│  ├─ useGameSocket.ts
│  ├─ sessionId.ts
│  └─ hostDeployment.ts
├─ scripts/sync-web.mjs
├─ docs/ARCHITECTURE.md
├─ docs/PROTOCOL.md
└─ README.md
```

## 6. 关键源码入口

| 文件 | 作用 |
| --- | --- |
| `android/app/src/main/kotlin/.../MainActivity.kt` | 启动前台服务、轮询本机端口、WebView 加载和失败恢复 |
| `android/app/src/main/kotlin/.../server/GameServerService.kt` | 服务生命周期、端口切换、部署状态和通知 |
| `android/app/src/main/kotlin/.../server/NetworkManager.kt` | 枚举并排序局域网 IPv4 |
| `android/app/src/main/kotlin/.../server/LocalGameServer.kt` | HTTP、WebSocket、事件路由、静态资源和二维码 |
| `server-core/.../platform/RoomManager.kt` | 房间、Session、玩家和游戏实例管理 |
| `server-core/.../games/avalon/AvalonGame.kt` | 阿瓦隆权威状态机和玩家视图 |
| `server-core/.../games/guandan/GuandanGame.kt` | 掼蛋权威状态机、牌型验证和私密手牌视图 |
| `server-core/.../games/timebomb/TimeBombGame.kt` | TimeBomb 阵营、线路和轮次状态机 |
| `server-core/.../games/crossfire/CrossfireGame.kt` | Crossfire initialRole/seenRoles/currentRole/finalRole、庄家与 seatOrder、标准 shuffleGroups/shuffleHistory、目标和结算状态机 |
| `server-core/.../games/goodcopbadcop/GoodCopBadCopGame.kt` | Good Cop Bad Cop 身份牌与行动状态机 |
| `server-core/.../games/liarsdice/LiarDiceGame.kt` | 骰子模拟器私密骰子、全量重掷、结算和结果统计 |
| `server-core/.../games/poker/PokerGame.kt` | 德州权威状态机和下注流程 |
| `android/app/src/main/kotlin/.../server/TestBotController.kt` | 测试机器人调度，复用各游戏动作接口 |
| `server-core/.../games/poker/HandEvaluator.kt` | 7 选 5 手牌判断 |
| `server-core/.../games/poker/Pots.kt` | 主池、边池、多人 All-in 和平分池 |
| `web/src/useGameSocket.ts` | WebSocket、应用层心跳、指数退避和 Session/昵称恢复 |
| `web/src/pages/Home.tsx` | 本人昵称、房间号、游戏选择、规则说明和部署页 |

## 7. 通信协议

统一结构：

```json
{
  "type": "room:join",
  "requestId": "optional-id",
  "payload": {}
}
```

主要客户端事件：

- `room:create`
- `room:join`
- `room:leave`
- `player:ready`
- `player:rename`
- `player:kick`
- `game:start`
- `game:restart`
- `avalon:configure`
- `avalon:select_team`
- `avalon:vote`
- `avalon:mission`
- `avalon:assassinate`
- `poker:action`
- `guandan:play` / `guandan:pass` / `guandan:continue` / `guandan:tribute` / `guandan:return_tribute`
- `timebomb:confirm_role` / `timebomb:cut`
- `crossfire:confirm_initial` / `crossfire:confirm_intel` / `crossfire:confirm_shuffle` / `crossfire:start_targeting` / `crossfire:target`
- `goodcop:investigate` / `goodcop:equip` / `goodcop:arm` / `goodcop:aim` / `goodcop:shoot`
- `liarsdice:configure` / `liarsdice:reroll` / `liarsdice:settle` / `liarsdice:next_round`
- `room:bots`

主要服务器事件：

- `session:attached`
- `room:state`
- `room:left`
- `room:closed`
- `avalon:config`
- `game:state`
- `game:error`
- `session:kicked`

完整字段见 `docs/PROTOCOL.md`。

## 8. 防作弊和隐私边界

- 浏览器只提交动作，不能直接修改权威状态；
- 服务端根据 WebSocket 绑定的 Session 确定操作玩家，不信任客户端传来的 `actorId`；
- 阿瓦隆通过 `viewFor(playerId)` 返回每名玩家有权看到的身份信息；
- 完整角色映射不得发送给浏览器；
- 德州牌堆和全部底牌保存在服务端；
- Showdown 前，玩家只能收到自己的 Hole Cards；
- 所有动作进入游戏状态机前必须验证。

## 9. 构建方法

### 环境

- Android Studio（支持 AGP 8.9.1）
- JDK 21（保留 JDK 17 可供 Android Studio 选择；源码字节码目标为 17）
- Android SDK Platform 35
- Android Build Tools 35.0.0
- Node.js 20+
- npm

### 构建前端并同步到 APK

```bash
npm install --prefix web
npm run sync:web
```

`sync:web` 会执行 TypeScript/Vite 生产构建，并将 `web/dist` 复制到：

```text
android/app/src/main/assets/web/
```

### 运行测试

```bash
npm run web:test
./gradlew :server-core:test
./gradlew :android:app:testReleaseUnitTest
./gradlew :android:app:lintRelease
```

### 构建 Release APK（正式交付）

```bash
./gradlew :android:app:assembleRelease
```

正式 APK 必须由固定的 Release keystore 签名；当前工程尚未配置该密钥，因此在配置签名之前，Gradle 产物不能视为正式可升级发布包。Release keystore、密码和私钥不得提交到代码仓库，也不得发送给 AI。后续交付不得提供 Debug APK。

### Android Studio

1. 使用 Android Studio 打开项目根目录；
2. 确认 Gradle 使用 JDK 21（本机同时保留 JDK 17；项目目标字节码仍为 17）；
3. 安装 SDK 35；
4. 执行 `npm run sync:web`；
5. 选择 `Build > Generate Signed Bundle / APK > APK`，并使用项目维护者保管的 Release keystore。

## 10. 当前自动测试

Kotlin 测试：

- `AvalonGameTest.kt`
- `GuandanGameTest.kt`
- `TimeBombGameTest.kt`
- `CrossfireGameTest.kt`
- `GoodCopBadCopGameTest.kt`
- `LiarDiceGameTest.kt`
- `HandEvaluatorTest.kt`
- `PokerGameTest.kt`
- `PotCalculatorTest.kt`
- `RoomManagerTest.kt`

Android JVM 测试：

- `NetworkManagerTest.kt`
- `GameServerServiceTest.kt`

Web 测试：

- `sessionId.test.ts`
- `hostDeployment.test.ts`

覆盖重点：

- 阿瓦隆角色分配、角色可见性、投票、任务、刺杀和胜负；
- 掼蛋牌型、级牌/红桃逢人配、特殊牌力、接风、四名次排名、进贡/还贡/抗贡、首出、升级、A级过关和跨局私密手牌恢复；
- TimeBomb 线路池、身份确认、剪线与胜负；
- Crossfire 5–10 人标准混洗组、非首位庄家旋转、连续 currentRole 混洗、个人历史与最终身份、讨论结束、同时目标和结算；
- Good Cop Bad Cop 私密身份牌、调查、装备与射击回合；
- 骰子模拟器房主统一骰数配置、私密骰子、全量重掷、全员结算、结果统计和全员确认下一局；
- 测试机器人玩家的房间容量、准备状态和等待大厅移除；
- 德州牌型、行动顺序、最小加注、All-in、主池、多个边池、Tie 和 Split Pot；
- Session ID 在普通局域网 HTTP 页面中的兼容性；
- 房主端口范围、Loopback 管理页判断和仅本机地址选择；
- 无 active network、移动数据与 Wi-Fi 并存、无互联网 Wi-Fi、VPN/Wi-Fi Direct 过滤；
- 空昵称/Session 和同一 Session 重复创建房间的拒绝逻辑。
- 未部署仅绑定 Loopback、部署后监听 LAN 接口；
- 房主返回解散房间并释放全部 Session、普通玩家退出不影响房间。
- 等待/进行中房间按原昵称恢复座位，WebSocket 心跳和 5 秒断线提示延迟。

## 11. 最高优先级 Bug 修补状态

### 现象

房主真机反馈：

- 开启移动数据时可以创建房间；
- 关闭移动数据后无法创建房间；
- 即使手机已经连接路由器 Wi-Fi，仍可能无法部署或创建；
- 热点场景曾经可用，但路由器 Wi-Fi 场景不稳定。

目标行为：

- 移动数据关闭；
- Wi-Fi 没有互联网；
- 只有局域网或只有房主热点；
- 以上场景都必须能够启动服务、创建房间和游戏。

### v0.3.2 已实施修补

- `LocalGameServer` 部署上线时监听 `0.0.0.0`，关闭部署或网络失效后仅监听 `127.0.0.1`；
- `MainActivity` 房主页面使用 Loopback，不应依赖公网；
- `NetworkManager` 合并 `ConnectivityManager.allNetworks + LinkProperties` 与 `NetworkInterface`，不要求互联网或 `NET_CAPABILITY_VALIDATED`；
- 候选地址仅接受 RFC1918 IPv4，并排除 Loopback、Link-local、VPN、移动网络和 Wi-Fi Direct；
- `GameServerService` 使用 `NetworkCallback` 重新发现地址，端口被占用时自动选择可用端口，并输出结构化日志；
- `Home.tsx` 只以本机 WebSocket 是否连接决定能否创建房间，不再要求 `hostStatus.deployed`；无 LAN IP 时进入仅本机模式；
- 房主部署状态与大厅分享地址每 3 秒及窗口重新聚焦时刷新，LAN IP 出现后自动生成可分享地址；
- WebSocket 解析、关闭、发送失败会显示明确错误；同一 Session 的多个 Socket 只在最后一个断开时将玩家标记离线；
- 房间快照与游戏视图广播已按同步快照读取，减少并发修改风险。

### 仍必须完成的真机诊断

在以下四种场景分别记录：

| 场景 | 移动数据 | Wi-Fi/热点 | 互联网 |
| --- | --- | --- | --- |
| A | 开 | 路由器 Wi-Fi | 有 |
| B | 关 | 路由器 Wi-Fi | 有 |
| C | 关 | 路由器 Wi-Fi | 无 |
| D | 关 | 房主热点 | 无 |

v0.3.2 已保留并扩展相关结构化日志。真机回归时应采集：

- `ConnectivityManager.activeNetwork`；
- `ConnectivityManager.allNetworks`；
- 每个网络的 `NetworkCapabilities` 和 `LinkProperties`；
- `NetworkInterface` 名称、状态和 IPv4；
- `/api/host/config` 的完整结果；
- LocalGameServer 启动成功或异常；
- WebSocket 的 `onopen`、`onclose` 和错误原因；
- `hostStatus.deployed`、`connected`、`selectedAddress`。

### 修补覆盖情况

以下代码级修补均已完成：

1. 局域网地址发现不依赖默认网络是否有互联网，也不要求 `NET_CAPABILITY_VALIDATED`。
2. 合并以下两个来源：
   - `ConnectivityManager.allNetworks + LinkProperties`；
   - `NetworkInterface.getNetworkInterfaces()`。
3. Wi-Fi 网络请求只要求 `TRANSPORT_WIFI`，不要添加 `NET_CAPABILITY_INTERNET`。
4. 将 Wi-Fi/热点接口的 RFC1918 IPv4 作为可部署地址，即使该网络不能访问公网。
5. 明确排除 Loopback、Link-local、VPN、移动数据和 Wi-Fi Direct 地址。
6. 当网络切换时通过 `NetworkCallback` 刷新部署页，不依赖用户重启 App。
7. 创建房间按钮的错误原因必须显示出来，不能只保持灰色。
8. 增加“仅本机模式”：即使未检测到局域网地址，房主仍可在 `127.0.0.1` 创建房间；只是在生成二维码前提示尚无对外地址。
9. 尚待完成：对 Android 厂商的“自动切换移动网络/避开无互联网 Wi-Fi”功能进行真机验证。

不要在没有真机结果前把该问题简单归因于 AP 隔离。AP 隔离会阻止其他玩家访问房主，但不能充分解释房主本机无法点击创建房间。

### 已增加与仍缺少的测试

- 已自动测试：无 active network 但存在 `wlan0/192.168.x.x`；
- 已自动测试：active network 是移动网络，但同时存在 Wi-Fi 局域网；
- 已自动测试：Wi-Fi 没有 `VALIDATED`/`INTERNET` capability；
- 已自动测试：VPN + Wi-Fi + 移动网络同时存在并正确过滤；
- 已自动测试：没有 LAN IP 时前端选择仅本机模式；
- 仍需仪器化/真机测试：网络从移动数据切换到无互联网 Wi-Fi 后部署页自动刷新；
- 仍需仪器化/真机测试：端口重启后 WebView 自动连接新 Loopback 端口。

## 12. 已修复的重要历史 Bug

### 玩家点击“进入房间”无反应

原因：普通 HTTP 局域网页面不一定能调用 `crypto.randomUUID()`。旧代码在点击后抛出异常。

修复：`web/src/sessionId.ts` 优先使用 `crypto.getRandomValues()`，并提供兼容回退；创建和加入操作增加异常捕获与可见错误。

### APK 启动出现 `ERR_CONNECTION_REFUSED`

原因：WebView 在前台服务真正监听端口前就加载 `127.0.0.1`。

修复：`MainActivity` 先显示启动页并轮询本机端口；服务器就绪后再加载，失败时自动重试。

### 路由器 Wi-Fi 显示错误 IP

修复：`NetworkManager` 对 Wi-Fi、热点、有线、VPN、移动网络和 Wi-Fi Direct 接口进行评分，部署页允许房主手动选择 IP 和端口。

## 13. 安装与签名注意事项

当前构建产物为未签名的 Release APK；如果维护者已有旧 Debug 安装包，不能用不同签名直接覆盖升级。

如果安装时提示冲突：

1. 确认卸载包名 `com.lanboardgame.host` 的旧版本；
2. 检查系统是否存在工作资料/双开空间中的同包名应用；
3. 允许文件管理器或浏览器“安装未知应用”；
4. 不要直接用不同 Debug keystore 覆盖安装同包名版本；
5. 正式交付应创建固定 Release keystore，并安全保存。

不要把 Release keystore、密码或私钥提交给 AI、Git 或源码压缩包。

## 14. 真机验收清单

- [ ] 首次打开不显示 `ERR_CONNECTION_REFUSED`；
- [ ] 关闭移动数据后房主仍可进入首页；
- [ ] 无互联网 Wi-Fi 下能显示正确 `192.168.x.x` 地址；
- [ ] 点击部署后能创建阿瓦隆、掼蛋、TimeBomb、Crossfire、Good Cop Bad Cop、骰子模拟器和德州房间；
- [ ] 同 Wi-Fi 玩家能通过 URL/二维码打开；
- [ ] 热点无移动数据时玩家能加入；
- [ ] Chrome 刷新后恢复原玩家；
- [ ] 准备前可改名，准备后不可改；
- [ ] 重开后所有玩家重新准备；
- [ ] 阿瓦隆私密身份无泄漏；
- [ ] 德州其他玩家底牌在 Showdown 前无泄漏；
- [ ] 多人 All-in 和多个边池结算正确；
- [ ] 房主锁屏/切换 App 后服务仍运行；
- [ ] Android 8、12、14、16 至少各测试一台或模拟器。

## 15. 后续开发优先级

### 用户确认的后续开发与交付约定

- 已移除 `@fontsource-variable/noto-sans-sc` 及其导入，改用 Android/WebView 系统中文字体栈；仍需在 Xm 和至少一台实体 Android 设备上检查中文字体回退、字重、裁切和布局；
- 已移除完整 Noto Sans SC 字体分片、未使用的 Tailwind 工具链与 AndroidX Core 依赖，并关闭 SourceMap/发布依赖元数据；v0.6.0 Release 未签名 APK 为 354,732 bytes（约 0.338 MiB，包含 29,313 bytes 新图标）；
- 后续只构建和交付 Release APK，Debug APK 不进入交付目录或交接包；
- 固定 Release keystore 后再生成可升级的正式签名 APK，签名材料只由项目维护者保管；
- 每次正式交付前必须通过 Web/Kotlin 测试、Android Lint、`assembleRelease`、签名验证和 zipalign 检查。

1. 在原问题设备及不同 Android 厂商上真机验证 A–D 网络场景；
2. 增加网络切换、端口回退和 WebView 重连的仪器化测试；
3. 固定 Release 签名并解决升级安装链路；
4. 增加断线玩家自动操作策略；
5. 增加进程死亡后的状态快照恢复；
6. 完善德州规则边界和多机长局压力测试；
7. 完善四款新游戏的真机长局压力测试和规则边界；
8. 再考虑 UI 动画和更多游戏。

开发优先级必须保持：规则正确 > 网络稳定 > 防作弊 > 断线恢复 > 操作体验 > 动画。

## 16. 给下一位 AI 的建议提示词

```text
请接手当前 LAN Board Game v0.6.0 Android 项目。不要推翻现有架构，也不要重写已有游戏引擎。

最高优先级任务：在真机上回归 v0.6.0 的无互联网局域网、部署开关、断线恢复、七款独立游戏桌面和测试机器人流程。应用必须在完全没有互联网、只有普通 Wi-Fi 局域网或房主热点时正常运行。

请先阅读：
1. DEVELOPMENT_HANDOFF.md
2. README.md
3. docs/ARCHITECTURE.md
4. docs/PROTOCOL.md
5. NetworkManager.kt
6. GameServerService.kt
7. LocalGameServer.kt
8. MainActivity.kt
9. web/src/pages/Home.tsx
10. web/src/useGameSocket.ts

要求：
- 使用已有诊断日志和可复现测试定位剩余厂商差异，不要猜测根因；
- 网络发现不得依赖互联网、VALIDATED 或默认移动网络；
- 保留 IP/端口设置和“部署上线”；
- 没有 LAN IP 时允许房主进入仅本机模式，并显示明确提示；
- 仅部署上线时绑定 `0.0.0.0`，关闭部署后必须只绑定 `127.0.0.1`；
- 不泄漏阿瓦隆完整角色和其他玩家德州底牌；
- 运行全部 Kotlin 和前端测试；
- 构建 APK 后检查版本、签名、zipalign 和 ZIP 完整性；
- 输出修改文件、根因、测试结果、APK 和完整源码包。
```

## 17. 交付文件说明

- `android/app/build/outputs/apk/release/app-release-unsigned.apk`：Release 构建产物；配置项目维护者的 Release keystore 后才可作为正式签名交付包；
- 完整源码、Gradle Wrapper、内嵌前端资源和本交接文档：按当前工作区打包交付；
- 本文档：可单独发送给其他 AI 的交接说明。
