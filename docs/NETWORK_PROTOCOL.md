# ArmyChess 联机协议（v1）

状态：已实现并通过基础联机验收  
传输：REST 创建会话；WebSocket 传递实时 JSON 消息  
地址：`POST /api/rooms`、`POST /api/rooms/{roomCode}/join`、`/ws/game?roomCode=…&token=…`

## 1. 不可破坏的隐私约束

服务端保存完整棋盘，但每个连接只能收到与其座位对应的裁剪快照。对手未公开棋子必须满足：

- `visibleType = null`；
- `revealed = false`；
- `id` 为匿名编号，不能包含 `MARSHAL`、`BOMB` 等类型；
- 公共事件禁止出现 `attackerType`、`defenderType`、`winnerPiece`、`loserPiece` 或任何等价字段；
- 客户端战况只可呈现“某方完成移动”“棋盘上发生交锋”以及公开的出局原因；夺旗事件可以说明“某方军旗被擒获”，但不得说明进攻棋子；
- 聊天、日志、通知和结算同样不得建立“谁击败了谁”的棋子身份对照。

该约束由前后端测试共同保护；修改公共 DTO 时必须先更新隐私测试并进行人工网络面板复核。

## 2. 创建与加入

请求体：

```json
{ "nickname": "松风" }
```

成功响应：

```json
{
  "roomCode": "482691",
  "playerId": 0,
  "nickname": "松风",
  "resumeToken": "仅该玩家持有的随机恢复凭证"
}
```

`resumeToken` 只保存在当前浏览器会话中，不出现在公开日志、分享链接或房间广播里。

## 3. 客户端消息

所有消息包含协议版本 `v: 1` 和 UUID `requestId`。服务端缓存最近的请求 ID，重复提交只确认，不再次执行动作。

### 心跳

```json
{ "v": 1, "type": "PING", "requestId": "uuid", "sentAt": 1758360000000 }
```

### 提交布局

```json
{
  "v": 1,
  "type": "SUBMIT_LAYOUT",
  "requestId": "uuid",
  "placements": [
    { "type": "FLAG", "position": { "x": 7, "y": 16 } }
  ]
}
```

服务端重新验证数量、种类、归属区域、空行营、军旗、地雷和炸弹位置，不信任前端校验。

### 行棋

```json
{
  "v": 1,
  "type": "MOVE_REQUEST",
  "requestId": "uuid",
    "pieceId": "piece-1d5f8d60-4921-4d65-b20d-7cffb5a8c863",
  "to": { "x": 8, "y": 10 },
  "expectedRevision": 8
}
```

`expectedRevision` 防止旧画面的动作覆盖新棋局；不一致时返回 `STALE_STATE`。

### 聊天、投降与再战

```json
{ "v": 1, "type": "CHAT_SEND", "requestId": "uuid", "text": "右路我来守" }
```

```json
{ "v": 1, "type": "SURRENDER_REQUEST", "requestId": "uuid" }
```

```json
{ "v": 1, "type": "REMATCH_REQUEST", "requestId": "uuid" }
```

四位玩家都提交再战请求后，服务端清空上一局私有棋盘、超时次数和结算状态，原房间回到布阵阶段。

## 4. 服务端消息

- `SESSION_READY`：恢复到指定座位；
- `ROOM_STATE`：当前玩家名单与阶段；
- `SNAPSHOT`：按接收者裁剪后的棋盘；
- `PUBLIC_EVENT`：脱敏后的公开动作；
- `CHAT_MESSAGE`：长度和频率校验后的房间聊天；
- `ACTION_ACCEPTED`：动作已接收；
- `ACTION_REJECTED`：动作未执行，包含稳定错误码；
- `PONG`：心跳响应。

`SNAPSHOT` 还包含服务端权威的 `turnDeadlineEpochMs`、四方 `alive` 状态、`timeoutCounts`、`winnerTeam` 与 `rematchVotes`。每回合 30 秒；到期由服务端自动换手并发送 `TURN_TIMED_OUT`，累计 5 次超时后发送原因是 `TIMEOUT` 的 `PLAYER_ELIMINATED`。

公开交锋事件示例：

```json
{
  "v": 1,
  "type": "PUBLIC_EVENT",
  "event": {
    "type": "CLASH_OCCURRED",
    "actor": 0,
    "position": { "x": 8, "y": 10 },
    "at": "2026-09-20T09:20:00Z"
  }
}
```

这里故意没有交战棋子、胜负棋子和被消灭棋子的身份。

一次请求可能连续产生多个 `PUBLIC_EVENT`。例如夺旗会先发送脱敏的 `CLASH_OCCURRED`，再发送：

```json
{
  "v": 1,
  "type": "PUBLIC_EVENT",
  "event": {
    "type": "PLAYER_ELIMINATED",
    "actor": 1,
    "reason": "FLAG_LOST",
    "at": "2026-09-20T09:20:00Z"
  }
}
```

客户端将 `FLAG_LOST` 表述为“某方军旗被擒获，全军覆没”，将 `NO_LEGAL_MOVE` 表述为“某方无棋可走，全军覆没”。事件只指出出局方和公开原因，不包含进攻方棋子身份。

## 5. 连接与错误边界

- 单条 WebSocket 消息上限 16 KiB；
- 昵称 1–16 字符，聊天 1–120 字符；
- 聊天最短间隔 700 ms；
- 无效版本、非法状态、越界坐标、错误棋子归属和非法路径均不会改变棋局；
- 同一房间的所有动作在服务端规则引擎中串行确认；
- 断线后使用原 `roomCode + resumeToken` 绑定原座位，新的连接会替换旧连接。
