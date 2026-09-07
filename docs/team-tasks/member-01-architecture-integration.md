# 成员1：总体架构、流程编排与集成负责人

## 任务定位

负责把五名成员的模块组合成可运行的完整游戏，维护公共模型、页面流程、依赖装配和版本集成。该成员不重复实现 UI、网络协议、麻将规则或数据库，而是确定它们之间的边界并完成应用层编排。

## 负责范围

- 整理最终需求和用例：创建好友房、邀请码加入、等待准备、房主开局、牌局、单局结算、下一轮、总结算、好友积分排行、断线重连。
- 将当前单模块工程调整为清晰的分层结构：`api`、`application`、`domain`、`infrastructure`、`ui`。
- 维护 `MahjongTypes`、模块错误码、页面参数和跨模块只读 DTO。
- 实现正式版 `NavigationService`、`ViewFactory` 和应用启动装配，替换页面 Demo 使用的静态导航。
- 编写房间到牌局的应用流程协调器，例如 `RoomApplicationService`、`GameFlowCoordinator`。
- 确定客户端、服务端和规则引擎的调用顺序、超时策略及异常回退页面。
- 合并各成员分支，处理接口冲突并保证主分支持续可编译运行。

## 核心流程实现

```text
启动程序
  → 加载本机玩家资料和好友积分榜
  → 创建房间 / 输入房间码加入
  → 建立连接并保存 reconnectToken
  → 进入等待页并订阅 RoomSnapshot
  → 全员准备，房主调用 start
  → 收到 GameId 后进入牌局页
  → 根据 GameSnapshot 和 GameEvent 更新页面
  → 收到 Settlement 后进入结算页
  → 未完成全部轮次：返回牌局
  → 已完成全部轮次：写入好友累计积分并返回首页
```

## 主要代码归属

```text
src/main/java/com/campus/mahjong/
├─ MainApplication.java
├─ api/common/
├─ application/
│  ├─ AppContext.java
│  ├─ RoomApplicationService.java
│  ├─ GameFlowCoordinator.java
│  └─ SessionContext.java
└─ ui/navigation/
   ├─ JavaFxNavigationService.java
   └─ FxmlViewFactory.java
```

## 接口协作

- 调用成员3提供的 `FriendRoomService` 实现，获取房间快照、连接状态和 `GameId`。
- 调用成员4提供的 `GameSessionService`/规则引擎实现，推进牌局和获取结算。
- 调用成员5提供的 `FriendScoreService` 与玩家资料存储实现。
- 向成员2提供稳定的页面 ViewModel、导航方法和统一异常对象，UI 不直接访问 Socket 或数据库。

## 阶段安排

1. 第1周：确认需求、包结构、公共 DTO、错误码和 Git 协作规范。
2. 第2周：完成应用上下文、依赖装配和正式导航框架。
3. 第3周：串联创建/加入房间与等待页流程。
4. 第4周：串联牌局、结算、下一轮和房间结束流程。
5. 第5周：接入断线重连、积分写入及全局错误处理。
6. 第6周：负责全量集成、缺陷分派、演示版本和答辩流程。

## 交付物

- 可运行的客户端启动入口和依赖装配代码。
- 完整页面导航与会话生命周期实现。
- 跨模块 DTO、错误码和接口版本说明。
- 集成分支、版本变更记录和完整演示脚本。
- 一张最终组件图和一张完整业务时序图。

## 验收标准

- 主流程可以从首页连续运行到总结算并返回首页。
- Controller 不直接创建网络连接、操作数据库或实例化规则实现。
- 退出房间、网络异常、服务端拒绝操作时均有明确回退路径。
- 页面切换不会重复订阅事件或遗留后台线程。
- `mvn clean test` 和 `mvn javafx:run` 均能通过。

## 协作约束

- 公共接口变更须先通知其余四名成员并更新接口说明。
- 不直接修改其他成员模块的内部实现；通过接口或合并请求协调。
- 每日合并小步提交，提交信息使用 `模块: 修改内容` 格式。
