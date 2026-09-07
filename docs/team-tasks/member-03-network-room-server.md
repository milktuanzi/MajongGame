# 成员3：联网通信、好友房与服务端负责人

## 任务定位

负责多台电脑之间的可靠通信、好友房生命周期和服务端权威状态。客户端不能互相信任，房间和牌局操作必须由服务端校验、排序并广播。

## 功能任务

- 选择并实现 TCP 或 WebSocket 通信方案，定义统一消息信封、消息类型和协议版本。
- 实现游戏服务端启动、端口配置、局域网地址配置及优雅关闭。
- 实现6位房间码生成、唯一性校验、超时回收和房间人数限制。
- 实现创建、加入、离开、准备、取消准备、房主开始和房主转移。
- 实现房间配置同步，只有房主可以修改玩法、轮次、倍率和封顶。
- 实现心跳检测、掉线标记、重连令牌、座位保留和超时踢出。
- 将合法的玩家动作转交成员4的牌局服务，并将结果按顺序广播给所有客户端。
- 每个玩家只收到其有权查看的数据，绝不向其他玩家广播暗牌。

## 建议协议

```text
MessageEnvelope
├─ protocolVersion
├─ requestId
├─ roomId
├─ playerId
├─ messageType
├─ expectedRevision
├─ sentAt
└─ payload
```

主要消息类型：

- `CREATE_ROOM_REQUEST/RESPONSE`
- `JOIN_ROOM_REQUEST/RESPONSE`
- `ROOM_SNAPSHOT`
- `PLAYER_READY`
- `START_GAME`
- `GAME_SNAPSHOT`
- `PLAYER_ACTION`
- `ACTION_RESULT`
- `ROUND_SETTLED`
- `HEARTBEAT`
- `RECONNECT_REQUEST/RESPONSE`
- `ERROR_RESPONSE`

## 主要代码归属

```text
src/main/java/com/campus/mahjong/infrastructure/network/
├─ client/
│  ├─ GameNetworkClient.java
│  ├─ ReconnectManager.java
│  └─ MessageDispatcher.java
├─ server/
│  ├─ GameServer.java
│  ├─ ClientSession.java
│  ├─ RoomManager.java
│  └─ RoomActor.java
└─ protocol/
   ├─ MessageEnvelope.java
   ├─ MessageType.java
   └─ codec/
```

## 接口实现责任

- 实现 `FriendRoomService` 的所有方法。
- 为 `observe` 提供可关闭的订阅对象，防止页面退出后继续接收事件。
- 为 `create` 和 `joinByInviteCode` 返回 `FriendRoomAccess`。
- 将网络事件转换为 `GameEventBus` 事件，供成员1和成员2消费。
- 调用成员4实现的 `GameSessionService`，但不在网络层实现麻将规则。

## 并发与安全要求

- 同一房间内所有命令串行执行，避免两名玩家同时操作造成状态竞争。
- 每次房间/牌局变化递增 `revision`，拒绝旧版本操作。
- 校验 `playerId`、房间成员身份、当前行动者和操作权限。
- 房间码不能作为永久身份凭证；重连使用独立随机令牌。
- 客户端输入长度、消息大小和发送频率必须有限制。
- 日志不得记录完整手牌、密码或重连令牌。

## 阶段安排

1. 第1周：确定协议、消息模型、端口配置和本机双客户端测试方案。
2. 第2周：实现服务端、客户端连接、编码解码和请求响应关联。
3. 第3周：完成好友房创建、加入、准备、离开和状态广播。
4. 第4周：接入牌局命令与事件广播，实现玩家视角数据过滤。
5. 第5周：实现心跳、断线、重连、重复消息与过期操作处理。
6. 第6周：进行4台电脑联机压力测试、延迟测试和异常恢复测试。

## 交付物

- 可独立启动的游戏服务端和网络客户端。
- 联网协议文档、消息示例和错误码表。
- `FriendRoomService` 正式实现。
- 本机多进程及多电脑联机测试脚本/说明。
- 网络日志、连接统计和故障定位说明。

## 验收标准

- 四台电脑可使用同一房间码进入同一房间并看到一致座位状态。
- 同一玩家不能重复占座，房间满员后拒绝新玩家。
- 断网后恢复连接能够回到原座位并获取最新快照。
- 非房主不能修改配置或开始游戏。
- 并发提交、重复请求和旧 `revision` 不会造成状态分叉。
- 任何客户端都不能从协议数据中获取其他玩家暗牌。

## 联调依赖

- 第2周前与成员1冻结公共 DTO 和错误处理方式。
- 第3周向成员2提供可运行的模拟服务端。
- 第4周与成员4联调牌局命令和玩家视角快照。
- 第5周向成员5提供网络异常测试入口和测试数据。
