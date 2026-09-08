package com.campus.mahjong.model.repository;

import com.campus.mahjong.model.common.MahjongTypes.PlayerId;
import com.campus.mahjong.model.common.MahjongTypes.PlayerProfile;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository {
    void save(PlayerProfile profile);
    Optional<PlayerProfile> findById(PlayerId id);
    Optional<PlayerProfile> findByNickname(String nickname);
    List<PlayerProfile> findAll();
}
