# 项目目录与功能说明

## 1. 根目录

| 路径 | 作用 | 是否应提交 Git |
|---|---|---|
| `src/` | Java 源码、FXML、CSS、图片和测试代码 | 是 |
| `docs/` | 架构、接口、规则、数据库及团队分工文档 | 是 |
| `data/` | 本机运行后生成的 SQLite 数据库，如玩家资料、积分与排行榜 | 否 |
| `target/` | Maven 编译结果、测试报告和运行时构建产物 | 否 |
| `.m2/` | 项目本地 Maven 依赖缓存 | 否 |
| `.idea/` | IntelliJ IDEA 工程配置 | 通常否 |
| `pom.xml` | Maven 配置：Java 21、JavaFX、JUnit、SQLite、Jackson 等依赖 | 是 |
| `README.md` | 项目简介、启动方法和玩法说明 | 是 |
| `.gitignore` | 指定数据库、构建结果等不上传 Git 的文件 | 是 |

## 2. 主程序源码 `src/main/java`

主包为 `com.campus.mahjong`，整体调用方向为：

`app → controller → model/service → infrastructure → SQLite 或 TCP`

页面通过 `controller` 调用牌局和联机功能；核心规则集中在 `model`，不会依赖 JavaFX 页面。

### 2.1 `app/`：程序启动

- `MainApplication`：JavaFX 主入口，初始化窗口、样式和首页。

### 2.2 `controller/`：页面控制层

负责接收按钮、鼠标等界面操作，调用牌局或联机服务，再把结果刷新到页面。

#### `controller/page/`

- `HomeController`：首页、昵称、四川/红中模式选择、创建/加入房间、积分榜。
- `WaitingRoomController`：好友房等待页、玩家席位、准备状态、邀请信息和开局。
- `GameController`：对局界面、换三张、定缺、选牌、出牌、碰杠胡、倒计时、记牌器和动画。
- `SettlementController`：最终排名、累计积分和逐笔流水展示与保存。

#### `controller/navigation/`

- `NavigationService`：页面跳转接口。
- `AppNavigator`：实际执行首页、等待房、牌桌、结算页切换。
- `ViewFactory`：加载 FXML 页面并创建控制器。

#### `controller/command/`

- 当前只有包说明，是为“把页面操作封装成命令对象”预留的目录，尚无实际实现。

### 2.3 `model/`：核心业务与领域模型

#### `model/common/`

- `MahjongTypes`：集中定义跨模块使用的数据结构，包括玩家、房间、模式、操作、快照、结算等 record/enum。

#### `model/game/`

实现一场真实麻将对局的状态与流程。

- `MahjongMatch`：管理多轮比赛、累计积分、胡牌事件和积分流水。
- `MahjongRound`：单轮权威状态机，处理发牌、换三张、定缺、摸牌、出牌、碰杠胡及轮次推进。
- `RoundPhase`：单轮当前阶段。
- `TileType`：麻将牌类型、花色、点数和牌墙生成。
- `Meld`：碰牌、杠牌等公开牌组。
- `RoundOutcome`：一次胡牌、杠牌或轮次结束产生的结算变化。
- `ScoreEntry`：排行榜/结算页使用的逐笔积分流水。
- `WinEvent`：胡牌玩家、胡牌张、番型和分数变化。
- `ExchangeDirection`：换三张的交换方向。
- `VisibleTileCounter`：根据手牌、弃牌和副露计算每种牌的未见张数。

#### `model/rule/`

负责判断能否胡牌、识别番型和计算付款金额。

- `StandardHandEvaluator`：普通牌型胡牌拆解。
- `WildcardHandEvaluator`：红中赖子替代后的胡牌判断。
- `HandPatternAnalyzer`：识别七对、龙七对、十八罗汉、九莲宝灯等番型，并计算根。
- `SettlementCalculator`：根据底分、倍率、番数、封顶计算胡牌和杠牌积分。
- `MahjongRuleEngine`：规则引擎接口。
- `RuleEngineRegistry`：规则引擎注册与查找。

#### `model/rule/region/`

按玩法隔离地区规则。

- `RegionalRuleSet`：地区规则统一接口。
- `SichuanRuleSet`：四川麻将牌墙、缺门和番型规则。
- `RedCenterRuleSet`：在四川基础上加入四张红中赖子。
- `RegionalRules`：根据模式选择对应规则实现。
- `RuleSupport`：构造不同模式牌墙等共享辅助方法。

#### `model/session/`

- `DemoSession`：单机演示和无网络情况下的本地会话；页面也通过它读取本地牌局状态。

#### `model/service/`

业务用例接口层，用“接口”隔离页面和具体实现。

- `service/game/GameSessionService`：牌局快照、可执行操作、操作提交、重连和结算查询。
- `service/mode/GameModeCatalog`：四川麻将、红中麻将模式目录。
- `service/player/PlayerAccountService`：读取和刷新玩家资料。
- `service/room/FriendRoomService`：创建、加入、准备、开局、离开和观察好友房。
- `service/room/FriendScoreService`：积分榜和最近牌友查询。

#### `model/repository/`

持久化接口，不关心底层使用 SQLite 还是其他数据库。

- `PlayerRepository`：保存和查询本机玩家。
- `GameRecordRepository`：保存轮次、流水，查询积分榜和历史对局。

#### `model/event/`

- `GameEventBus`：发布和订阅牌局事件，降低模块之间的直接依赖。

#### `model/dto/` 与 `model/entity/*/`

- 当前主要是包说明和结构预留。
- 实际传输数据目前集中在 `MahjongTypes` 和网络协议 `Payloads` 中。
- 实际玩家、房间、排名对象也主要使用 `MahjongTypes` 内的 record。

### 2.4 `infrastructure/`：技术实现层

#### `infrastructure/network/client/`

- `LanSession`：客户端当前联机会话，连接房主并接收房间/牌局快照。
- `GameNetworkClient`：TCP 客户端连接、收发消息。
- `LanInvitation`：解析和生成 `IP:端口#房间码` 邀请信息。
- `LanAddressResolver`：查找适合热点联机的本机地址。

#### `infrastructure/network/server/`

- `GameServer`：房主电脑上的 TCP 游戏服务器。
- `RoomManager`：房间、玩家、准备状态和服务端权威牌局管理。
- `ClientConnection`：维护单个客户端连接及消息发送。

#### `infrastructure/network/protocol/`

- `MessageEnvelope`：网络消息统一外层结构。
- `MessageType`：加入房间、准备、操作、快照等消息类型。
- `Payloads`：各种网络请求和响应的数据体。
- `JsonMessageCodec`：使用 Jackson 将消息与 JSON 相互转换。

#### `infrastructure/persistence/`

- `SqliteDatabase`：创建数据库连接、数据表和索引。
- `LocalDataServices`：为页面提供全局本地数据库服务入口。

##### `persistence/repository/`

- `SqlitePlayerRepository`：`PlayerRepository` 的 SQLite 实现。
- `SqliteGameRecordRepository`：`GameRecordRepository` 的 SQLite 实现，处理积分和排行榜。

##### `persistence/service/`

- `SqlitePlayerAccountService`：玩家账户服务的 SQLite 实现。
- `SqliteFriendScoreService`：好友积分服务的 SQLite 实现。

##### `persistence/migration/`

- 当前只有包说明，为未来数据库版本迁移脚本预留。

#### `infrastructure/game/`

- `InMemoryGameSessionService`：基于内存的 `GameSessionService` 实现，适合 Demo、测试或不接服务器的场景。

#### `infrastructure/config/`

- 当前只有包说明，为应用配置、依赖装配等功能预留。

### 2.5 `view/`：可复用界面组件

#### `view/component/`

- `MahjongTileView`：使用 JavaFX Canvas 绘制万、筒、条和字牌。
- `PlayerInfoView`：显示玩家名称、座位、定缺、积分和胡牌信息。
- `TableSeatLayout`：根据本地玩家座位换算上、下、左、右玩家位置。

#### `view/converter/`

- 当前只有包说明，为 FXML 数据转换器预留。

### 2.6 `util/`

- 当前只有包说明，尚无通用工具类。

### 2.7 `module-info.java`

- Java 模块描述文件。
- 声明 JavaFX、SQLite、Jackson 依赖。
- 控制哪些包允许导出，以及哪些控制器允许 FXML 反射访问。

## 3. 页面资源 `src/main/resources`

### `view/fxml/`

- `home-view.fxml`：首页和创建/加入好友房界面。
- `waiting-room-view.fxml`：等待房页面。
- `game-view.fxml`：对局牌桌页面。
- `settlement-view.fxml`：总结算、排行和流水页面。

### `view/css/`

- `home.css`：目前的统一主题文件，覆盖首页、等待房、对局、结算、排行榜、麻将牌和按钮样式。

### `view/image/`

- 保存早期首页/牌桌背景及当前排行榜背景。
- `mahjong-ranking-bg-v1.png` 是淡黄与青绿排行榜专用背景。

### `view/images/table/`

- 当前正在使用的大厅、牌桌、庭院和胡牌提示图片。
- `ARTWORK.md` 记录图片素材用途。

## 4. 测试代码 `src/test/java`

测试目录结构基本与主代码对应。

- `integration/`：模块化启动和局域网多端连接测试。
- `infrastructure/persistence/`：SQLite 玩家、战绩、排行榜及幂等保存测试。
- `model/game/`：回合状态机、血战到底、换三张、定缺、出牌超时、碰杠胡和记牌测试。
- `model/rule/`：胡牌拆解、地区规则、番型和结算公式测试。
- `view/component/`：玩家座位与桌面方向映射测试。
- `controller/`、`model/` 下仅有 `package-info.java` 的目录是测试结构预留。

## 5. 文档目录 `docs`

- `INTERFACE_DESIGN.md`：接口和核心类型设计。
- `MVC_PACKAGE_STRUCTURE.md`：MVC 包结构说明。
- `STATE_MACHINE_AND_METHOD_DESIGN.md`：回合状态与方法设计。
- `REGIONAL_RULES.md`：四川和红中规则、番型及结算口径。
- `SQLITE_SCHEMA.md`：玩家、对局、积分和流水数据表设计。
- `IMPLEMENTATION_ROADMAP.md`：开发阶段和完成情况。
- `team-tasks/`：五名成员的模块分工建议。

## 6. 一次操作经过的主要目录

以“玩家在牌桌点击一张牌出牌”为例：

1. `view/fxml/game-view.fxml` 显示牌桌。
2. `controller/page/GameController` 接收点击。
3. 联机时通过 `infrastructure/network/client` 发送操作；单机时进入 `model/session/DemoSession`。
4. 房主端 `infrastructure/network/server/RoomManager` 调用 `model/game/MahjongRound`。
5. `MahjongRound` 校验阶段、座位、定缺和 revision，再修改权威状态。
6. 最新快照通过 `network/protocol` 编码后广播给客户端。
7. `GameController` 根据新快照刷新手牌、弃牌、玩家信息和操作按钮。
8. 胡牌或杠牌时由 `model/rule` 计算番型与积分，最终通过 `infrastructure/persistence` 保存到 SQLite。
