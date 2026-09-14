package com.campus.mahjong.model.ai;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** 可替换的讲解端口。实现只负责文字，不能修改动作；没有任何网络依赖。 */
public interface TeacherExplanationProvider {
    record Request(String lessonId, String ruleVersion, String mode, String action, String tile,
                   String decisionEvidence, List<RuleKnowledgeBase.Citation> retrievedRules) {
        public Request { retrievedRules = List.copyOf(retrievedRules); }
    }
    record Response(String lessonId, String explanation, List<String> citedRuleIds, String provider) {
        public Response { citedRuleIds = List.copyOf(citedRuleIds); }
    }
    CompletionStage<Response> explain(Request request);
}
