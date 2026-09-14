package com.campus.mahjong.integration;

import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.model.ai.*;
import com.campus.mahjong.model.common.MahjongTypes.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TeacherRoomIntegrationTest {
    @Test void teachingReviewsOnlyReachTheStudentWhoDiscarded() throws Exception {
        var settings = new FriendRoomSettings(ModeCode.SICHUAN,1,Optional.of(128),1,false,"",true);
        try (var host=await(LanSession.host(player("教学学生"),settings,0,"127.0.0.1"));
             var guest=await(LanSession.join(player("旁观同学"),host.invitation()))) {
            assertTrue(guest.currentRoom().orElseThrow().settings().orElseThrow().teachingMode());
            await(host.addBot()); var room=await(host.addBot());
            await(guest.setReady(room.roomId(),guest.localPlayer().id(),true));
            await(host.start(room.roomId(),host.localPlayer().id()));
            var policy=new TeacherBotPolicy(new RuleKnowledgeBase());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
            while(System.nanoTime()<deadline && host.teacherHistory().stream().noneMatch(l -> l.seat()==Seat.EAST && l.status().equals("MOCK"))) {
                for(var session:List.of(host,guest)) {
                    var state=session.currentGame().orElse(null);
                    if(state!=null) {
                        var choice=policy.choose(settings.mode(),session==host?Seat.EAST:Seat.SOUTH,state);
                        if(choice.isPresent()) await(session.perform(choice.get().request(state,session.localPlayer().id())));
                    }
                }
                Thread.sleep(50);
            }
            var review=host.teacherHistory().stream().filter(l -> l.seat()==Seat.EAST && l.status().equals("MOCK")).findFirst().orElseThrow();
            assertTrue(review.explanation().contains("你打出"));
            assertTrue(review.explanation().contains("与老师的基础策略建议一致"));
            assertTrue(review.citations().stream().anyMatch(c -> c.id().equals("COMMON-DISCARD")));
            Thread.sleep(150);
            assertTrue(guest.teacherHistory().stream().noneMatch(l -> l.seat()==Seat.EAST),"学生点评不得广播给其他玩家");
        }
    }
    static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(8,TimeUnit.SECONDS); }
    static PlayerProfile player(String name) { return new PlayerProfile(new PlayerId(UUID.randomUUID().toString()),name,"",0,1); }
    @Test void ownerCanAddRemoveTeachersAndGuestsCannotControlThem() throws Exception {
        var settings = new FriendRoomSettings(ModeCode.SICHUAN,1,Optional.of(128),1,false,"");
        try (var host=await(LanSession.host(player("老师房主"),settings,0,"127.0.0.1"));
             var guest=await(LanSession.join(player("同学"),host.invitation()))) {
            assertThrows(ExecutionException.class, () -> await(guest.addBot()));
            var room=await(host.addBot());
            assertEquals(1,room.players().stream().filter(RoomPlayer::bot).count());
            Seat bot=room.players().stream().filter(RoomPlayer::bot).findFirst().orElseThrow().seat();
            assertThrows(ExecutionException.class, () -> await(guest.removeBot(bot)));
            assertThrows(ExecutionException.class, () -> await(host.removeBot(Seat.EAST)));
            room=await(host.removeBot(bot)); assertEquals(2,room.players().size());
            await(host.addBot()); room=await(host.addBot()); assertEquals(4,room.players().size());
            assertThrows(ExecutionException.class, () -> await(host.addBot()));
        }
    }
    @Test void oneHumanThreeTeachersPlayAndPublishCitedMockLessons() throws Exception {
        var settings = new FriendRoomSettings(ModeCode.RED_CENTER,1,Optional.of(128),1,false,"");
        try (var host=await(LanSession.host(player("练习学生"),settings,0,"127.0.0.1"))) {
            await(host.addBot()); await(host.addBot()); RoomSnapshot room=await(host.addBot());
            assertTrue(room.players().stream().allMatch(p -> p.ready() && p.connected()));
            var lessons=new LinkedBlockingQueue<TeacherLesson>();
            try (var subscription=host.observeTeacher(lessons::add)) {
                await(host.start(room.roomId(),host.localPlayer().id()));
                assertThrows(ExecutionException.class, () -> await(host.removeBot(Seat.SOUTH)));
                var policy = new TeacherBotPolicy(new RuleKnowledgeBase());
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
                TeacherLesson explanation=null;
                while (System.nanoTime()<deadline && explanation==null) {
                    var state=host.currentGame().orElse(null);
                    if(state!=null) {
                        var decision=policy.choose(settings.mode(),Seat.EAST,state);
                        if(decision.isPresent()) await(host.perform(decision.get().request(state,host.localPlayer().id())));
                    }
                    TeacherLesson lesson=lessons.poll(100,TimeUnit.MILLISECONDS);
                    if(lesson!=null && lesson.status().equals("MOCK")) explanation=lesson;
                }
                assertNotNull(explanation,"应收到一次成功出牌对应的 Mock 讲解");
                assertNotEquals(Seat.EAST,explanation.seat());
                assertFalse(explanation.citations().isEmpty());
                assertTrue(explanation.enhancement().contains("规则约束"));
                assertTrue(explanation.enhancement().contains("策略参考"));
                String lessonId = explanation.id();
                assertEquals(1, host.teacherHistory().stream().filter(l -> l.id().equals(lessonId)).count(), "本地证据与Mock补充应合并为同一条历史");
            }
        }
    }
    @Test void invalidProviderCitationFallsBackWithoutChangingTheMove() throws Exception {
        var settings = new FriendRoomSettings(ModeCode.SICHUAN,1,Optional.of(128),1,false,"");
        var requests = new LinkedBlockingQueue<TeacherExplanationProvider.Request>();
        TeacherExplanationProvider invalid = request -> {
            requests.add(request);
            return CompletableFuture.completedFuture(new TeacherExplanationProvider.Response(request.lessonId(), "捏造引用", List.of("NOT-IN-CORPUS"), "TEST"));
        };
        try (var host=await(LanSession.host(player("回退验证"), settings, 0, "127.0.0.1", invalid))) {
            await(host.addBot()); await(host.addBot()); var room=await(host.addBot());
            await(host.start(room.roomId(),host.localPlayer().id()));
            var policy = new TeacherBotPolicy(new RuleKnowledgeBase());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
            while (System.nanoTime()<deadline && host.teacherHistory().stream().noneMatch(l -> l.status().equals("FALLBACK"))) {
                var state=host.currentGame().orElse(null);
                if(state!=null) {
                    var choice=policy.choose(settings.mode(),Seat.EAST,state);
                    if(choice.isPresent()) await(host.perform(choice.get().request(state,host.localPlayer().id())));
                }
                Thread.sleep(50);
            }
            var lesson=host.teacherHistory().stream().filter(l -> l.status().equals("FALLBACK")).findFirst().orElseThrow();
            var request=requests.poll(1,TimeUnit.SECONDS); assertNotNull(request);
            assertEquals(lesson.tile(),request.tile());
            assertEquals("DISCARD",request.action());
            assertEquals(lesson.explanation(),request.decisionEvidence());
            assertFalse(lesson.enhancement().contains("捏造引用"));
            assertFalse(lesson.citations().isEmpty());
        }
    }
}
