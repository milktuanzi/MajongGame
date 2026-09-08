# JavaFX 麻将游戏接口骨架

当前工程包含可编译的 MVC 接口骨架，以及“创建/加入好友房 → 等待入房 → 好友牌局 → 单局结算 → 下一轮/累计排名”的 JavaFX 可交互 Demo。

- 接口源码：`src/main/java/com/campus/mahjong/api/`
- 结构说明：`docs/INTERFACE_DESIGN.md`
- MVC 包结构：`docs/MVC_PACKAGE_STRUCTURE.md`
- 分阶段实现计划：`docs/IMPLEMENTATION_ROADMAP.md`
- 地区规则口径：`docs/REGIONAL_RULES.md`
- SQLite 数据设计：`docs/SQLITE_SCHEMA.md`
- 编译验证：`mvn compile`
- 启动主页面：`mvn javafx:run`

## 当前已完成

- 明亮轻快的统一页面主题与响应式卡片布局
- 创建/加入房间、玩法与倍率/轮次/封顶配置
- 等待房间、玩家席位、准备状态和开局校验
- 动态手牌、选牌、出牌、摸牌及牌桌弃牌区
- 单局结算、本房累计积分、好友长期积分榜和多轮推进
- 136 张牌墙、权威回合状态机、吃碰杠胡校验、胡牌/流局结算
- revision 乐观锁、基础牌型分析、倍率与封顶计分

当前数据由 `DemoSession` 在内存中驱动，便于 UI 与流程联调。下一阶段按实现计划将房间状态替换为 TCP/WebSocket 服务端快照，实现多电脑真实同步。

玩家档案、对局记录与累计积分已写入 `data/mahjong.db`；该数据库文件不会提交到 Git。
