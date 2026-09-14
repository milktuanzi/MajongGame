package com.campus.mahjong.model.ai;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** 本地 Mock：检索证据驱动的确定性文字生成。不会读取环境变量或发起 HTTP 请求。 */
public final class MockTeacherExplanationProvider implements TeacherExplanationProvider {
    @Override public CompletionStage<Response> explain(Request request) {
        if (request.retrievedRules().isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("缺少规则证据"));
        String rules = request.retrievedRules().stream().filter(c -> c.kind().equals("HARD"))
                .map(c -> "[" + c.id() + "] " + c.text()).collect(java.util.stream.Collectors.joining("\n"));
        String advice = request.retrievedRules().stream().filter(c -> c.kind().equals("STRATEGY"))
                .map(c -> "[" + c.id() + "] " + c.text()).collect(java.util.stream.Collectors.joining("\n"));
        String explanation = "出牌依据\n" + request.decisionEvidence() + "\n\n规则约束\n" + rules + "\n\n策略参考\n" + advice;
        return CompletableFuture.completedFuture(new Response(request.lessonId(), explanation,
                request.retrievedRules().stream().map(RuleKnowledgeBase.Citation::id).toList(), "MOCK"));
    }
}
