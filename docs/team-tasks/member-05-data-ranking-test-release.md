# 成员5：数据持久化、好友积分、测试与发布负责人

## 任务定位

负责玩家资料、好友积分、牌局记录的可靠保存，并建立覆盖客户端、服务端和规则引擎的测试体系。该成员同时维护构建、打包和最终交付材料。

## 数据模块任务

- 选择适合课程项目的存储方案，推荐服务端 SQLite；设计初始化和版本迁移机制。
- 保存玩家、好友关系、房间、牌局、每轮结算和累计积分。
- 实现 `FriendScoreService`，支持累计积分榜、对局数、胜局、胜率和最近对手。
- 确保同一局结算重复提交时只写入一次，避免重连或重试导致重复加分。
- 提供按房间、玩家和时间查询的牌局历史。
- 设计数据备份、恢复、清理和演示数据初始化方法。

## 建议数据表

```text
player
  player_id, nickname, created_at, last_seen_at

friend_group
  group_id, name, created_at

friend_group_member
  group_id, player_id, joined_at

game_room
  room_id, invite_code, mode, settings_json, status, created_at

game_round
  game_id, room_id, round_no, winner_id, reason, settled_at

score_change
  game_id, round_no, player_id, before_score, delta, after_score, detail_json

friend_score
  group_id, player_id, total_score, games, wins, updated_at
```

## 排名规则

- 第一排序字段：累计积分降序。
- 第二排序字段：胜局数降序。
- 第三排序字段：达到当前积分的时间升序。
- 单局结算先写 `game_round` 和 `score_change`，事务成功后更新 `friend_score`。
- 未完成或被撤销的牌局不计入累计积分。
- 首页显示前若干名和本人名次；结算页显示本局排名及更新后的累计排名。

## 测试职责

- 建立 JUnit 5 测试结构、测试命名约定和覆盖率报告。
- 为成员1编写流程集成测试：创建、加入、准备、开局、结算、返回首页。
- 为成员2编写 FXML 加载冒烟测试和关键 ViewModel 测试。
- 协助成员3测试多客户端连接、断线重连、重复请求、超时和错误报文。
- 协助成员4建立规则回归数据集和结算一致性测试。
- 进行4台电脑的端到端测试，记录系统版本、IP、延迟和异常现象。

## 主要代码归属

```text
src/main/java/com/campus/mahjong/infrastructure/persistence/
├─ DatabaseManager.java
├─ migration/
├─ repository/
└─ score/JdbcFriendScoreService.java

src/test/java/com/campus/mahjong/
├─ application/
├─ network/
├─ rule/
├─ persistence/
└─ ui/
```

## 构建与发布任务

- 完善 Maven 依赖、测试插件、JavaFX 插件和资源打包配置。
- 生成 Windows 可运行包，包含客户端；服务端提供独立启动包与配置示例。
- 提供默认端口、服务地址和数据库位置配置文件，不把环境参数写死在源码中。
- 编写安装、启动服务器、启动客户端、创建房间和加入房间说明。
- 准备演示数据、验收清单、已知问题列表和最终版本号。

## 阶段安排

1. 第1周：数据模型、测试规范、构建目录和验收指标。
2. 第2周：数据库初始化、Repository 和演示数据。
3. 第3周：积分写入、好友排名和历史查询。
4. 第4周：流程、网络和规则模块集成测试。
5. 第5周：4台电脑端到端、重连、并发和数据一致性测试。
6. 第6周：打包发布、使用文档、演示环境和最终测试报告。

## 交付物

- 数据库结构、迁移脚本、Repository 和 `FriendScoreService` 实现。
- 自动化测试代码、覆盖率报告和端到端测试记录。
- Windows 客户端包、服务端包及示例配置。
- 用户使用说明、部署说明、已知问题和最终测试报告。

## 验收标准

- 服务重启后玩家资料、历史牌局和好友积分不会丢失。
- 同一结算重复处理不会重复累计积分。
- 排名相同时严格按规定的次级字段排序。
- 四台电脑能够完成一整局并在各客户端看到一致结算和排名。
- 全新电脑按照文档可以完成服务端和客户端启动。
- `mvn clean test` 全部通过，发布包不依赖开发工具即可运行。

## 与其他成员的联调点

- 与成员1确定结算写入时机、事务失败后的流程回退。
- 与成员2确定积分榜字段、分页和空数据状态。
- 与成员3保证玩家身份与重连后积分归属一致。
- 与成员4验证 `Settlement` 各分项和积分总和。
