# WebSocket 协议

统一消息结构：

```json
{
  "type": "room:join",
  "requestId": "optional-client-id",
  "payload": {}
}
```

## 通用事件

| 方向 | 事件 | 说明 |
| --- | --- | --- |
| C → S | `system:ping` | 客户端连接健康探测 |
| S → C | `system:pong` | 服务端心跳响应 |
| C → S | `room:create` | 创建房间 |
| C → S | `room:join` | 加入、按 Session 重连，或按原昵称恢复离线玩家 |
| C → S | `room:leave` | 返回主界面；房主执行时解散房间，普通玩家执行时退出本人 |
| C → S | `player:ready` | 设置准备状态 |
| C → S | `player:rename` | 未准备时修改自己的昵称 |
| C → S | `player:kick` | 房主踢出等待房间中的玩家 |
| C → S | `room:bots` | 房主在等待大厅批量添加或移除测试机器人 |
| C → S | `game:start` | 房主开始 |
| C → S | `game:restart` | 房主结束当前局并返回准备大厅 |
| S → C | `server:hello` | WebSocket 建连后的服务器版本信息 |
| S → C | `session:attached` | 返回 room/player/session 标识 |
| S → C | `session:kicked` | 当前 Session 已被房主移出房间 |
| S → C | `room:state` | 公共房间状态 |
| S → C | `room:left` | 当前玩家已退出并返回主界面 |
| S → C | `room:closed` | 房主已解散房间，其他玩家返回主界面 |
| S → C | `game:state` | 按当前玩家裁剪的游戏状态 |
| S → C | `game:error` | 动作拒绝或协议错误 |

## 阿瓦隆事件

- `avalon:configure`：房主在等待房间设置人数与完整角色列表
- `avalon:config`：服务器广播当前阿瓦隆房间配置
- `avalon:select_team`：`{ "team": ["playerId"] }`
- `avalon:vote`：`{ "approve": true }`
- `avalon:mission`：`{ "success": true }`
- `avalon:assassinate`：`{ "targetId": "playerId" }`

服务器忽略客户端声称的 actorId，始终使用 WebSocket 已绑定的 Session 玩家。
已完成的队伍投票会在 `game:state.public.voteHistory` 中公开每名玩家的赞成/反对选择；当前尚未完成的秘密投票只公开已提交玩家 ID。

## 掼蛋事件

- `guandan:play`：`{ "cards": ["card-id"] }`，服务端验证单牌、对子、三张、三带二、五张顺子、三连对、钢板、五张同花顺、炸弹和四王炸；
- `guandan:pass`：当前玩家在已有牌面时过牌；

掼蛋 `game:state` 只向本人返回完整手牌，其他玩家只收到手牌数量和公开的上一手牌。

## 新增游戏事件

### TimeBomb

- `timebomb:confirm_role`：确认查看身份和线路组成；
- `timebomb:cut`：`{ "targetId": "playerId", "wireIndex": 0 }`，服务端验证当前持剪玩家和目标线路编号。

### Crossfire

- `crossfire:confirm_initial`：确认初始身份；
- `crossfire:confirm_intel`：确认情报传递；
- `crossfire:confirm_shuffle`：仅当前混洗组的三名玩家可确认；三人全部确认后，服务端使用其当前身份牌执行一次随机重分配并记录参与者，然后进入下一组；全部预定组完成后进入讨论；
- `crossfire:start_targeting`：房主结束讨论并开始同时选目标；
- `crossfire:target`：`{ "targetId": "playerId" }`，所有玩家提交完成后统一结算。

Crossfire 私有 `game:state` 明确区分 `initialRole`、`seenRoles`、`currentRole`、`shuffleHistory` 和 `finalRole`。服务端按真实 `seatOrder` 以 `dealerPlayerId` 为首个中心，每隔 3 个座位生成下一中心；每组固定为环形左邻、中心、右邻，覆盖全部身份牌后停止。混洗始终读取并更新 `currentRole`，所以重复参与者使用上一次混洗后的身份。混洗记录只包含本人实际参与的三人组，不包含他人混洗后的角色；`finalRole` 在全部混洗完成后才发送给本人，断线重连通过同一 `GameInstance` 恢复。

### Good Cop Bad Cop

- `goodcop:investigate`：`{ "targetId": "playerId", "cardIndex": 0 }`；
- `goodcop:equip`：获得基础防弹装备；
- `goodcop:arm`：公开一张自己的隐藏身份并拿枪；
- `goodcop:aim`：`{ "targetId": "playerId" }`；
- `goodcop:shoot`：按当前瞄准目标结算射击。

### 骰子模拟器

- `liarsdice:configure`：房主在等待大厅发送 `{ "diceCount": 5 }`，统一设置所有玩家 1–10 颗骰子；
- `liarsdice:reroll`：无参数，重掷当前玩家的全部骰子；
- `liarsdice:settle`：无参数，提交当前玩家骰子；所有玩家结算后服务端才公开各人结果和 1–6 点数统计，喊点、质疑和胜负由线下完成。
- `liarsdice:next_round`：所有玩家结算后逐人确认；全员确认后服务端生成下一局私密骰子并重新隐藏统计结果。

### 测试机器人

- `room:bots`：`{ "action": "fill" }` 补齐到当前游戏人数上限；
- `room:bots`：`{ "action": "clear" }` 移除等待大厅中的全部机器人。仅房主可调用，游戏进行中拒绝操作。

## 房主本机 HTTP 接口

- `GET /api/health`：健康检查，返回监听端口、当前所选地址、部署状态和最新局域网地址列表。
- `GET /api/host/config`：返回可用局域网网卡、IPv4、当前端口和部署状态。
- `POST /api/host/deploy?address=...&port=...`：保存对外地址并按需切换监听端口。
- `POST /api/host/undeploy`：关闭局域网部署并将监听地址切换为 `127.0.0.1`，房主本机页面继续可用。
- `GET /api/qr?text=...`：为房间分享地址生成 PNG 二维码。
- `/api/host/config`、`/api/host/deploy` 与 `/api/host/undeploy` 只接受来自 `127.0.0.1/::1` 的请求，局域网浏览器不能修改房主部署设置。

房主 WebView 始终通过 Loopback 访问服务。即使没有可用局域网 IPv4、`deployed=false`，也允许创建仅本机房间；此时不生成供其他玩家使用的地址或二维码。检测到 Wi-Fi/热点地址后，客户端会从 `/api/health` 自动刷新分享地址。

## 德州事件

`poker:action`：

```json
{
  "action": "RAISE_TO",
  "amount": 120
}
```

动作枚举：`FOLD`、`CHECK`、`CALL`、`RAISE_TO`、`ALL_IN`。每个动作都先经过 `validateAction()`。
