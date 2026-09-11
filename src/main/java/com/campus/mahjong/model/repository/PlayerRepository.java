package com.campus.mahjong.model.repository;

import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository {
    void save(PlayerProfile profile);
    Optional<PlayerProfile> findById(PlayerId id);
    Optional<PlayerProfile> findByNickname(String nickname);
    /** 返回最近一次保存或使用的本机玩家。 */
    Optional<PlayerProfile> findMostRecent();
    List<PlayerProfile> findAll();
}
