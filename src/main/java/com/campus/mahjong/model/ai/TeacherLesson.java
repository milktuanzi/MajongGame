package com.campus.mahjong.model.ai;

import com.campus.mahjong.model.common.MahjongTypes.*;
import java.util.List;

/** 仅在出牌成功后发布。无需手牌即可展示的教学证据，不包含身份、密钥或其他暗牌。 */
public record TeacherLesson(String id, int round, Seat seat, long revision, String tile,
                            String explanation, List<RuleKnowledgeBase.Citation> citations,
                            String enhancement, String status) {
    public TeacherLesson { citations = List.copyOf(citations); }
    public TeacherLesson withEnhancement(String text, String source) {
        return new TeacherLesson(id, round, seat, revision, tile, explanation, citations, text, source);
    }
}
