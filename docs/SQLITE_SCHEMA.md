# SQLite 本地数据设计

数据库默认创建在 `data/mahjong.db`，由 `LocalDataServices` 在程序启动时自动迁移。

## 表结构

```text
players
  id PK                  稳定玩家 UUID
  nickname UNIQUE        昵称
  avatar_url             头像地址
  coins / level          预留用户成长字段
  created_at / updated_at

friend_scores
  player_id PK/FK        玩家
  total_score            好友累计积分
  games / wins           对局数与胜局数
  last_played_at

game_records
  id PK                  幂等对局记录号
  room_code / mode       房间与玩法
  round_number           轮次
  winner_id FK           流局时为空
  played_at

game_score_entries
  game_id + player_id PK 每局每人唯一明细
  score_delta            本局分差
```

## 一致性策略

- 玩家、对局、积分明细和累计排名在同一个事务中写入。
- `game_records.id` 用作幂等键，网络重试不会重复累计积分。
- 开启外键、WAL 和 5 秒 busy timeout，适合桌面客户端并发读写。
- 联网阶段数据库应部署在房主/中心服务端，客户端只读取服务端排行榜快照。
