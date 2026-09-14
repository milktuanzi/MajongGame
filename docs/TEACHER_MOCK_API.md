# 机器人老师 · Mock 与 RAG 接口

## 当前已实现

本版本基于 main 的 `ea8699d`，联机协议升级至 7，联机电脑需使用同一版本。默认实现是 `MockTeacherExplanationProvider`：不会读密钥、不会发 HTTP 请求、没有外部 SDK，也不会消耗 API 余额。

首页点击“开始教学 · 三位老师陪练”，按所选玩法创建一轮练习并自动填入三位老师；在等待房点击开始。教学模式会在换三张、定缺、出牌和响应阶段根据本人快照给出建议，操作仍由学生自己决定。成功出牌后，老师比较实际选择与原先建议并点评，不推断学生的主观意图，也不把不同选择判为错误。点评及 Mock 补充仅发给出牌学生本人。

牌桌“文字讲解”开关默认开启，普通房与教学房都有；关闭会隐藏建议、最新讲解和历史窗口入口，不影响机器人动作、倒计时和后台历史收集。重新打开可继续查看。开关在本次应用运行期间记忆，重启后恢复开启。目前沿用原牌局计时，并非可暂停的教学关卡。

房主在等待房点击“添加机器人老师”填入空席，最多三位；老师自动准备，房主可在开局前移除最后一位。四席准备后照常开局。老师根据本人快照选择合法动作，支持四川/红中的换三张、定缺、出牌、胡牌和响应过。当前为基础结构权重策略，暂不主动碰杠，也没有实现向听数、胜率或搜索式最优策略。

老师每次成功出牌后生成一条教学记录，先显示本地决策依据，再补充 Mock 讲解。牌桌底部自动显示最新依据；“依据与历史”可查看本次会话最近 100 条记录及规则原文。窗口不阻塞牌局。历史是内存数据，退出房间后不保留。普通房仅讲解机器人出牌；教学房额外点评真人的主动出牌。原有超时自动出牌暂不生成教学点评。

教学模式或含机器人房间均视为练习场：仍显示完整零和分数和流水，但不向好友长期排行榜和对局库计分。纯真人普通房仍正常保存。

`FriendRoomSettings.teachingMode` 是房间级布尔字段。旧的六参数 Java 构造器默认 `false`；首页教学入口传 `true`，其余房间默认 `false`。显示开关仅在客户端，不通过网络修改房间或机器人状态。

## 数据与规则流向

```text
本人视角快照 → TeacherBotPolicy → 合法动作请求 → 原牌局服务校验执行
                                                        ↓ 成功出牌
模式+动作主题 → RuleKnowledgeBase 检索 → 决策事实+规则片段
                                                        ↓
                                  TeacherExplanationProvider.explain
                                                        ↓
                                   Mock 返回带引用的说明
                                                        ↓
                              校验记录ID/引用 → TEACHER_LESSON
                                                        ↓
                                    牌桌提示与教学历史
```

教学建议另走客户端本人快照 → 同一个 TeacherBotPolicy → 操作提示的路径；真人出牌经服务端执行成功后，服务端按操作前的本人快照生成比较点评，进入上述讲解接口，按学生身份单独推送。机器人讲解面向全房间。代码规则负责强制合法性；RAG 检索负责为教学文本提供适用证据，两者不能互相替代。没有引用时不生成无依据讲解。

规则库是 `src/main/resources/com/campus/mahjong/ai/rules.md`，版本 `teacher-rules-v1`。按 Markdown 小节分块，每块含稳定 ID、适用玩法、主题和 HARD/STRATEGY 类型。检索先过滤玩法，再匹配出牌/定缺/换牌等主题，硬规则优先。当前库很小，采用本地元数据检索，不依赖 embedding 或向量数据库。Mock 用检索片段与决策事实生成确定性模板；不能称为已接入大模型。

HARD 表示本房硬规则；STRATEGY 表示启发式建议。规则库按当前项目规约编写，两种玩法都换三张、定缺；平胡 0 番，付款按 `10 × 房间倍率 × min(2^番数, 封顶倍率)`，不封顶时直接使用 `2^番数`。修改玩法或番表时必须同步更新规则库版本及检索回归测试，不要复用旧版本规则文本。

## 预留的 Java API

文件：`model/ai/TeacherExplanationProvider.java`。

```java
CompletionStage<Response> explain(Request request);
```

请求字段：

| 字段 | 含义 |
|---|---|
| lessonId | 已成功出牌对应的不可变记录 ID，响应必须原样返回 |
| ruleVersion | 当前规则库版本 |
| mode | SICHUAN 或 RED_CENTER |
| action | 当前接入 DISCARD |
| tile | 已经公开打出的牌，例如“一万” |
| decisionEvidence | 本地算法的决策依据；教学点评包含实际选择与建议的比较，可能包含一张建议保留/打出的手牌 |
| retrievedRules | 检索结果：id/title/kind/text/version |

响应字段：`lessonId, explanation, citedRuleIds, provider`。`provider` 当前为 MOCK，用于明确标识讲解来源。请求故意不包含真人昵称、玩家 ID、密钥、其他暗手、原始牌墙或整个 GameSnapshot。

未来接 HTTP 服务时可以直接映射这些 DTO 为 JSON。例如：

```json
{
  "lessonId": "example-only",
  "ruleVersion": "teacher-rules-v1",
  "mode": "SICHUAN",
  "action": "DISCARD",
  "tile": "一万",
  "decisionEvidence": "先打出缺门牌，这是定缺规则要求。",
  "retrievedRules": [{
    "id": "COMMON-MISSING",
    "title": "定缺优先",
    "kind": "HARD",
    "text": "仍持有缺门牌时必须先打缺门牌。",
    "version": "teacher-rules-v1"
  }]
}
```

```json
{
  "lessonId": "example-only",
  "explanation": "先处理缺门牌，这是本房规则要求。[COMMON-MISSING]",
  "citedRuleIds": ["COMMON-MISSING"],
  "provider": "MOCK"
}
```

上述是传输约定示例，当前没有启动 REST 讲解服务器；实际 Mock 是进程内实现。

## 如何替换为真实模型

实现 `TeacherExplanationProvider`，异步调用外部服务并映射 Response，然后通过以下入口注入：

```java
GameServer.open(port, explanationProvider);
// 或从房主客户端创建入口注入：
LanSession.host(player, settings, port, advertisedHost, explanationProvider);
```

默认重载仍使用 Mock。未来适配器应立即返回 CompletionStage，不能在 explain 调用内阻塞游戏线程。网络密钥只保存在房主环境或安全配置里，不进请求 DTO、不进客户端快照、不进仓库。

已经预留 3 秒超时及回退：错误、异常、空文本、超长文本、错记录 ID 或引用不在检索集合中时，保留已经显示的本地依据及引用，状态改为 FALLBACK。异步结果按 lessonId 归档，不会覆盖其他出牌。关闭房间后的结果被丢弃。当前校验能检查身份与引用集合，不能证明任意外部模型输出的语义正确；接模型时还需增加输出约束、内容一致性测试与费用上限。

## 网络协作

新增 ADD_BOT、REMOVE_BOT、TEACHER_LESSON 三种消息，RoomPlayer 增加 bot 标识。添加/移除由服务端验证房主身份与等待阶段。机器人没有 Socket，开局可视为就绪，网络发送仍只面向真实连接。

每 800 毫秒最多推进一次 AI 动作，不在定时线程 sleep。每次重读快照、重新计算，已提交的换牌/定缺/响应不会重复执行，已胡或终局座位不会再操作。真人响应超时 10 秒时仅将未提交的选择设为过。

快照读取与修改使用同一 Context 锁；他家暗杠使用四个“暗牌”占位，界面与记牌器已适配。策略只读本人快照，不使用 DemoSession 中用于联调的他家手牌读取逻辑。

## 验证

- 普通回归：`mvn clean verify`。
- 机器人/规约/Mock：`mvn -Dtest=TeacherBotPolicyTest,TeacherRoomIntegrationTest test`。
- 有桌面的 JavaFX 验证：`mvn -Dteacher.uiTest=true -Dtest=TeacherUiSmokeTest test`。

测试覆盖两种玩法固定种子多轮结束、合法候选、检索玩法隔离、规则/建议区分、Mock 确定性、添加移除权限、一真人三老师实际 TCP 对局、每次讲解和出牌 ID 的绑定、非法引用回退，以及等待房/牌桌/引用历史的真实 JavaFX 加载与截图。
