# LAN Board Game

一台 Android 手机即可主持的离线局域网桌游平台。房主安装 APK 后，应用会在手机上启动 HTTP + WebSocket 服务；其他玩家只需连接同一 Wi‑Fi 或房主热点，扫码后使用 Chrome 加入。

当前版本：`0.6.0`（七款游戏独立桌面、即时测试机器人与新应用图标）

## 已实现

- Android Kotlin 宿主、WebView、Foreground Service；
- Android 内嵌 NanoHTTPD + NanoWSD，无 Node 运行时、无云服务器；
- 合并 Android 网络属性与系统网卡信息，自动识别无互联网 Wi-Fi、热点、有线网络的 RFC1918 IPv4；
- 首页“部署上线”流程，部署设置会保存到房主手机；
- 网络变化后自动刷新地址；没有局域网地址时，房主仍可在仅本机模式创建并游玩；
- 部署页支持手动刷新反馈和关闭局域网部署；关闭后服务器仅监听 `127.0.0.1`；
- 默认端口被占用时自动改用可用端口，并显示连接/重连错误；
- WebView 等待本地服务器就绪后再打开，失败时自动恢复并提供重试；
- 通用 Room / Player / Session / Reconnect 平台层；
- 房间创建、加入、准备、房主开始、踢出玩家、再来一局；
- React + TypeScript + Vite 移动端客户端，使用项目内原生 CSS，避免引入未使用的 UI 框架；
- 使用 Android/WebView 系统中文字体，不打包额外中文字体资源；
- 阿瓦隆 5–10 人完整核心状态机与按玩家裁剪的私密视图；
- 阿瓦隆房主可设置目标人数、梅林/刺客以外的可选特殊角色；
- 阿瓦隆投票与任务历史、累计投票次数、游戏规则简介；
- 等待房间内可修改本人昵称，准备后由服务器锁定；
- 游戏结束或房主放弃本局后返回大厅，所有玩家重新准备；
- 房间和游戏页提供返回按钮；房主返回会解散房间并通知所有玩家，普通玩家返回只退出本人；
- 断线或退出后使用原房间号和本人昵称可恢复原玩家身份、座位及局内私密状态；
- 德州扑克 2–8 人、可调初始筹码和盲注、自动下一手；
- 掼蛋 4 人、两副牌、级牌与红桃逢人配、完整常用牌型及特殊牌力、接风、排名、进贡/还贡、升级和 A 级过关；
- TimeBomb 4–8 人、隐藏阵营、线路剪除与四轮胜负；
- Crossfire 5–10 人、全员向左传一次身份、以庄家为起点每隔 3 个座位执行相邻三人混洗、私密混洗记录、讨论倒计时与同时选目标；
- Good Cop Bad Cop 4–8 人、身份牌调查、装备、武装与射击；
- 骰子模拟器支持 2–10 人、房主统一配置 1–10 颗骰子、全量重掷、全员结算和下一局同步；不包含喊点、质疑或胜负逻辑；
- 房主可在服务器未部署上线时于等待大厅批量添加/移除测试机器人；真人完成动作后机器人会立即随机执行合法动作，并复用正常游戏状态机；
- 七款游戏按玩法分别使用议事环、搭档牌桌、拆弹工作台、情报座次环、玩家档案板、私人骰盘和扑克环桌；高信息密度游戏支持横屏双栏布局；
- 德州 7 选 5 牌型、下注、最小加注、All-in、主池、多边池、平分池；
- Kotlin/JUnit 自动测试和 TypeScript 生产构建。

## 使用流程

1. 在房主 Android 手机上安装并打开 APK。
2. 在首页选择当前 Wi-Fi/热点对应的局域网 IP，并设置端口（默认 `8787`）；地址列表会随网络变化自动刷新。
3. 点击“部署上线”，确认页面显示的局域网访问地址。若暂时没有局域网地址，可以先用仅本机模式创建房间，连接局域网后再分享二维码。
4. 输入本人昵称，选择游戏并创建房间；也可以填写房间号加入已有房间。房间号右侧的“加入”按钮用于直接加入。
5. 让其他手机连接同一普通 Wi‑Fi，或连接房主手机开启的热点。
6. 其他玩家扫描房主页面中的二维码。
7. 阿瓦隆房主设置人数和角色；其他游戏按页面提示准备，房主开始游戏。需要单人验证时，房主可在等待大厅添加测试机器人。

整个流程无需电脑、公网服务器或互联网。房主也在 APK 内的同一 React 客户端中参与。

## 工程结构

```text
lan-board-game/
├─ android/app/                 Android 宿主应用
│  ├─ src/main/kotlin/          Activity、Foreground Service、本地服务器
│  └─ src/main/assets/web/      已构建的离线 Web 客户端
├─ server-core/                 纯 Kotlin 游戏与平台核心
│  ├─ platform/                 Room、Player、Session、RoomManager
│  ├─ games/avalon/             阿瓦隆状态机与 PlayerView
│  ├─ games/guandan/            掼蛋状态机与按玩家裁剪的手牌视图
│  ├─ games/timebomb/           TimeBomb 状态机与私密线路视图
│  ├─ games/crossfire/          Crossfire 情报、目标与结算状态机
│  ├─ games/goodcopbadcop/      Good Cop Bad Cop 身份与行动状态机
│  ├─ games/liarsdice/          骰子模拟器私密骰子、重掷与结算状态机
│  ├─ games/poker/              牌、下注、牌型、主池/边池
│  └─ src/test/                 自动测试
├─ web/                         React + TypeScript + Vite 客户端
│  └─ src/
│     ├─ pages/                 首页、加入页、房间大厅
│     ├─ games/avalon/          阿瓦隆 UI
│     ├─ games/guandan/         掼蛋 UI
│     ├─ games/poker/           德州 UI 与牌面
│     ├─ games/timebomb/        TimeBomb UI
│     ├─ games/crossfire/       Crossfire UI
│     ├─ games/goodcopbadcop/   Good Cop Bad Cop UI
│     ├─ games/liarsdice/       骰子模拟器 UI
│     └─ useGameSocket.ts       WebSocket 与断线重连
├─ scripts/sync-web.mjs         将 Web 构建复制进 APK assets
├─ docs/ARCHITECTURE.md
└─ docs/PROTOCOL.md
```

## 开发资料

- [当前开发交接文档](DEVELOPMENT_HANDOFF.md)
- [架构说明](docs/ARCHITECTURE.md)
- [通信协议](docs/PROTOCOL.md)
- [新增游戏开发规范](docs/GAME_EXTENSION_GUIDE.md)
- [仓库内容与排除项](docs/REPOSITORY_CONTENTS.md)
- [v0.3.2 历史交接文档](docs/archive/LAN-Board-Game-v0.3.2-DEVELOPMENT-HANDOFF.md)

## 架构原则

- Android Native 只负责生命周期、网络地址、二维码、WebView 和服务保活；
- `server-core` 是唯一可信规则源，浏览器只提交动作；
- 每款游戏独立实现 `GameInstance`，通过 RoomManager 和 LocalGameServer 复用房间/网络层；
- 服务器按 `playerId` 生成独立视图，不把完整身份或其他玩家手牌发给浏览器；
- 通用 Socket Envelope 使用 `type + requestId + payload`，后续游戏只需新增事件和独立引擎；
- 房间状态暂存在内存中，关闭/强制结束房主应用会结束当前房间。

## 路由器 Wi-Fi 注意事项

- 房主应选择标记为“当前 Wi-Fi”的地址，通常形如 `192.168.x.x`；不要选择 VPN、移动网络或 Wi-Fi Direct 地址。
- 所有玩家必须连接同一个普通 Wi-Fi 和同一子网；路由器的访客网络通常禁止设备互访。
- 如果地址正确但仍无法打开，请在路由器中关闭 **AP 隔离 / 客户端隔离 / 无线隔离**。
- 自定义端口建议使用 `1024–65535` 中未被占用的端口；不确定时保留默认 `8787`。

更完整的模块和数据流见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 本地开发

### 前端

要求 Node.js 20 或更高版本。

```bash
cd web
npm install
npm run dev -- --host 0.0.0.0
```

生产构建：

```bash
cd ..
npm run sync:web
```

`sync:web` 会执行 Vite 构建，并把 `web/dist` 同步到 `android/app/src/main/assets/web`。

### Kotlin 核心测试

```bash
./gradlew :server-core:test
```

也可以把核心模块作为独立 Gradle 工程运行：

```bash
cd server-core
../gradlew test
```

## 构建 APK

推荐 Android Studio：

1. 安装 Android Studio、JDK 21（同时保留额外安装的 JDK 17）和 Android SDK 35；项目源码的 Java/Kotlin 字节码目标仍为 17；
2. 打开项目根目录 `lan-board-game`；
3. 等待 Gradle Sync；
4. 先在项目根目录执行 `npm install --prefix web` 和 `npm run sync:web`；
5. Android Studio 中选择 **Build > Generate Signed Bundle / APK > APK**，使用项目维护者保管的 Release keystore。

命令行 Release 构建（交付验证）：

```bash
./gradlew :android:app:assembleRelease
```

输出位置：

```text
android/app/build/outputs/apk/release/app-release-unsigned.apk
```

如果没有配置项目自己的 Release keystore，Android Gradle Plugin 会生成 unsigned Release APK；正式分发前必须配置固定签名，不要把 keystore 或密码提交到代码仓库。

### 当前开发与交付约定

- CSS 使用 Android/WebView 可用的系统中文字体栈，不打包完整 Noto Sans SC 字体分片；生产构建关闭 SourceMap 并启用 R8/资源压缩；
- 交付只生成 Release APK，Debug 构建仅用于开发者本地排错；
- Release APK 必须使用固定的 Release keystore 签名，签名文件、密码和私钥不得提交到源码或发送给 AI；在获得现有 keystore 或明确创建新签名密钥前，不宣称 APK 为正式可升级发布包；
- 每次正式交付必须运行 Web/Kotlin 测试、Android Lint、`assembleRelease`，并检查 APK 包名、版本号、签名和 zipalign。

## Wi‑Fi 局域网测试

1. 房主与玩家连接同一路由器；
2. 房主创建房间，确认页面显示 `http://局域网IP:8787/room/房间号`；
3. 玩家扫码或直接在 Chrome 输入地址；
4. 创建阿瓦隆房间至少需要 5 人，TimeBomb 4 人，Crossfire 5 人，Good Cop Bad Cop 4 人，骰子模拟器/德州至少需要 2 人；
5. 测试浏览器刷新、切换 App、锁屏后返回，原玩家应按 Session 恢复。

部分公共 Wi‑Fi 开启了 AP/Client Isolation，会阻止设备互访。这种情况下改用房主热点。

## 手机热点测试

1. 房主先开启 Android 热点；
2. 玩家连接热点；
3. 房主打开 APK 并创建房间；
4. 玩家扫码加入；
5. 即使所有手机均关闭移动数据，游戏仍应继续。

某些 Android 厂商会在开启热点、锁屏或省电模式下限制后台网络。游戏期间建议把本应用加入“不受电池优化限制”的列表，并保持房主手机有足够电量。

## 自动测试覆盖

当前自动测试覆盖：

- 阿瓦隆人数与阵营分配；
- Merlin、Percival、坏人、Oberon/Mordred 可见性边界；
- 队伍投票、任务结果、好人不可提交失败、刺杀与胜负；
- 10 类德州牌型与 7 选 5；
- Wheel Straight、踢脚比较、完全 Tie；
- Heads-up 行动顺序和街道推进；
- 最小 Raise；
- 短码 All-in 不错误地重新开放加注；
- 主池、多个 Side Pot、Folded Money；
- Split Pot 和奇数筹码按庄家后顺序分配；
- Showdown 前不公开其他玩家 Hole Cards。
- TimeBomb 线路池、身份确认、剪线与胜负；
- Crossfire 5–10 人标准混洗组、非 1 号庄家整体旋转、身份连续流动、私密情报、讨论结束、同时目标和结算；
- Good Cop Bad Cop 私密身份牌、调查、装备与射击回合；
- 骰子模拟器房主统一骰数配置、私密骰子、全量重掷、全员结算、结果统计和全员确认下一局；
- 测试机器人玩家的房间容量、准备状态和等待大厅移除；
- 无默认网络、移动网络与 Wi-Fi 并存、无互联网 Wi-Fi、VPN/Wi-Fi Direct 过滤；
- 没有局域网地址时的仅本机地址选择；
- 空昵称/Session 与同一 Session 重复创建房间的拒绝逻辑。

## 常见故障

### 二维码能扫，但页面打不开

- 确认玩家已连接房主热点或同一 Wi‑Fi；
- 关闭玩家手机临时启用的 VPN/代理；
- 确认地址不是 `127.0.0.1`；玩家应使用房主显示的局域网地址；
- 公共/校园 Wi‑Fi 可能隔离客户端，改用手机热点；
- 如果房主同时连接 VPN、Wi‑Fi 和热点，系统可能返回多个地址，优先尝试热点或 Wi‑Fi 网段地址。

### 房主锁屏后其他人掉线

- 允许应用发送前台服务通知；
- 关闭该应用的电池优化；
- 厂商“后台冻结/自动清理”中将应用设为允许；
- 房主不要强制结束应用。

### 浏览器刷新后出现新玩家

- 不要使用无痕模式或清除站点数据；
- Chrome 需要保留 `localStorage` 中的 Session；
- 回到原二维码/原房间 URL，客户端会自动重连。

### 输入昵称后点击“进入房间”没有反应

- `0.1.1` 及更高版本已修复普通局域网 HTTP 页面无法调用 `crypto.randomUUID()` 的兼容问题；
- 请确认房主安装的是 `0.2.0` 或更高版本，旧 APK 不会自动更新；
- 如果按钮呈灰色，说明 WebSocket 尚未连接，先检查玩家页面右下角连接状态以及 VPN/代理设置。

### Android Studio 无法构建

- Gradle 使用 JDK 21（本机同时保留 JDK 17；Gradle 工程的 Java/Kotlin 字节码目标仍为 JVM 17）；
- 安装 Android SDK Platform 35 和 Build Tools 35.0.0；
- 执行 `npm run sync:web`，确认 `android/app/src/main/assets/web/index.html` 存在；
- 检查 Gradle 下载是否被代理、防火墙或证书拦截。

## 已知边界

- 当前房间和牌局保存在房主内存中；应用进程被系统彻底杀死后无法恢复；
- `0.5.0` 已支持按原昵称恢复被动断线或主动退出的离线座位；若同一房间中昵称被占用则不能恢复；
- 断线玩家不会被自动投票或自动出牌；服务端通过应用层心跳检测连接，客户端连续 5 秒无法恢复才提示；
- 使用局域网明文 HTTP/WebSocket，适用于可信的家庭/朋友局域网，不应暴露到公网；
- Release 构建启用 R8 与资源压缩；正式分发仍应替换为项目自己的 Release 签名并进行多机真机回归测试；
- 测试机器人仅用于服务器未部署时的等待大厅流程验证；服务器部署上线后服务端拒绝机器人操作并自动隐藏入口，正式构建仍可通过 `VITE_ENABLE_TEST_BOTS=false` 关闭；不提供策略 AI。

## 扩展新游戏

新增狼人杀、UNO、斗地主时：

1. 在 `server-core/games/<game>` 新建独立规则引擎并实现 `GameInstance`；
2. 在 RoomManager 注册新的 `GameType` 与工厂；
3. 在协议层增加该游戏事件；
4. 始终由服务端验证动作并生成按玩家裁剪的视图；
5. 在 `web/src/games/<game>` 增加独立 UI；
6. 为规则、隐私与重连补充测试。

不需要修改阿瓦隆或德州内部实现。
