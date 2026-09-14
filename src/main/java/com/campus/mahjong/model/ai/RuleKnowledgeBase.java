package com.campus.mahjong.model.ai;

import com.campus.mahjong.model.common.MahjongTypes.ModeCode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 小规模规则库的本地 RAG 检索：玩法过滤、主题匹配、硬约束优先；无需向量服务。 */
public final class RuleKnowledgeBase {
    public static final String VERSION = "teacher-rules-v1";
    public record Citation(String id, String title, String kind, String text, String version) {}
    private record Chunk(String mode, Set<String> topics, Citation citation) {}
    private final List<Chunk> chunks;

    public RuleKnowledgeBase() {
        try (var input = getClass().getResourceAsStream("/com/campus/mahjong/ai/rules.md")) {
            if (input == null) throw new IllegalStateException("老师规则库缺失");
            chunks = parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException error) { throw new IllegalStateException("老师规则库读取失败", error); }
    }
    private List<Chunk> parse(String source) {
        List<Chunk> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String block : source.split("(?m)^## ")) {
            if (block.startsWith("#")) continue;
            String[] lines = block.split("\\R", 2), fields = lines[0].trim().split("\\|", -1);
            if (fields.length != 5 || lines.length != 2 || !ids.add(fields[0])) throw new IllegalStateException("规则库格式错误");
            if (!Set.of("HARD", "STRATEGY").contains(fields[3])) throw new IllegalStateException("未知规则类型");
            if (!fields[1].equals("ALL")) ModeCode.valueOf(fields[1]);
            result.add(new Chunk(fields[1], Set.of(fields[2].split(",")),
                    new Citation(fields[0], fields[4], fields[3], lines[1].trim(), VERSION)));
        }
        if (result.isEmpty()) throw new IllegalStateException("规则库为空");
        return List.copyOf(result);
    }
    public List<Citation> retrieve(ModeCode mode, Set<String> topics) {
        Objects.requireNonNull(mode);
        var found = chunks.stream().filter(c -> c.mode.equals("ALL") || c.mode.equals(mode.name()))
                .filter(c -> !Collections.disjoint(c.topics, topics))
                .sorted(Comparator.comparingInt((Chunk c) -> c.citation.kind.equals("HARD") ? 0 : 1)
                        .thenComparing(c -> c.citation.id))
                .map(Chunk::citation).toList();
        if (found.isEmpty()) throw new IllegalArgumentException("未检索到适用规则，不能生成讲解");
        return found;
    }
}
