package com.campus.mahjong.view;

import com.campus.mahjong.controller.navigation.AppNavigator;
import com.campus.mahjong.infrastructure.network.client.LanSession;
import com.campus.mahjong.infrastructure.persistence.LocalDataServices;
import com.campus.mahjong.model.ai.*;
import com.campus.mahjong.model.common.MahjongTypes.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 JavaFX 加载/布局检查，需有桌面：-Dteacher.uiTest=true -Dtest=TeacherUiSmokeTest */
@EnabledIfSystemProperty(named="teacher.uiTest", matches="true")
class TeacherUiSmokeTest {
    @TempDir Path temporary;
    private Stage stage;
    private static <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(action.call()); } catch(Throwable error) { result.completeExceptionally(error); } });
        return result.get(12,TimeUnit.SECONDS);
    }
    @Test void waitingRoomGameAndCitedHistoryRenderAtDesktopSizes() throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        var settings=new FriendRoomSettings(ModeCode.SICHUAN,1,Optional.of(128),2,false,"",true);
        var profile=new PlayerProfile(new PlayerId("ui-student"),"练习同学","",0,1);
        try (var host=LanSession.host(profile,settings,0,"127.0.0.1").toCompletableFuture().get(5,TimeUnit.SECONDS)) {
            LanSession.install(host);
            fx(() -> { LocalDataServices.initialize(temporary.resolve("test.db")); stage=new Stage(); AppNavigator.start(stage);
                assertNotNull(stage.getScene().lookup("#teachingButton")); shot("teacher-home",stage);
                AppNavigator.waitingRoom(); return null; });
            for(int i=0;i<3;i++) host.addBot().toCompletableFuture().get(5,TimeUnit.SECONDS);
            fx(() -> {
                assertFalse(((Button)stage.getScene().lookup("#startButton")).isDisabled());
                assertTrue(((Button)stage.getScene().lookup("#addBotButton")).isDisabled());
                shot("teacher-waiting",stage);
                ((Button)stage.getScene().lookup("#startButton")).fire(); return null;
            });
            var policy=new TeacherBotPolicy(new RuleKnowledgeBase());
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
            while(System.nanoTime()<end && host.teacherHistory().stream().noneMatch(l -> l.status().equals("MOCK"))) {
                var state=host.currentGame().orElse(null);
                if(state!=null) {
                    var decision=policy.choose(settings.mode(),Seat.EAST,state);
                    if(decision.isPresent()) host.perform(decision.get().request(state,profile.id())).toCompletableFuture().get(5,TimeUnit.SECONDS);
                }
                Thread.sleep(50);
            }
            assertFalse(host.teacherHistory().isEmpty());
            fx(() -> {
                assertTrue(((Label)stage.getScene().lookup("#teacherTitle")).getText().contains("打出"));
                assertFalse(((Label)stage.getScene().lookup("#teachingHint")).getText().isBlank());
                CheckBox toggle=(CheckBox)stage.getScene().lookup("#textExplanations");
                toggle.fire();
                assertFalse(stage.getScene().lookup("#teacherContent").isVisible());
                assertFalse(stage.getScene().lookup("#teacherContent").isManaged());
                assertTrue(((Button)stage.getScene().lookup("#teacherHistoryButton")).isDisabled());
                shot("teacher-text-off",stage);
                toggle.fire();
                assertTrue(stage.getScene().lookup("#teacherContent").isVisible());
                shot("teacher-game-1280",stage);
                stage.setWidth(1024); stage.setHeight(720); return null;
            });
            fx(() -> { shot("teacher-game-1024",stage); ((Button)stage.getScene().lookup("#teacherHistoryButton")).fire(); return null; });
            fx(() -> {
                Stage history=(Stage)Window.getWindows().stream().filter(w -> w instanceof Stage s && "机器人老师 · 出牌依据".equals(s.getTitle())).findFirst().orElseThrow();
                TextArea text=(TextArea)history.getScene().lookup(".text-area");
                assertTrue(text.getText().contains("COMMON-DISCARD"));
                assertTrue(text.getText().contains("规则约束"));
                shot("teacher-history",history); history.close(); return null;
            });
        } finally {
            fx(() -> { for(var window:List.copyOf(Window.getWindows())) window.hide(); return null; });
            LanSession.closeCurrent(); Platform.exit();
        }
    }
    private static void shot(String name,Stage stage) throws IOException {
        var root=stage.getScene().getRoot(); root.applyCss(); root.layout();
        WritableImage image=root.snapshot(null,null);
        Path folder=Path.of("target","teacher-ui");Files.createDirectories(folder);
        writePng(image,folder.resolve(name+".png"));
    }
    // Pure JDK PNG encoder, avoids adding a Swing dependency to the application module.
    private static void writePng(WritableImage image,Path path) throws IOException {
        int w=(int)image.getWidth(),h=(int)image.getHeight();
        var compressed=new ByteArrayOutputStream();
        try(var pixels=new DeflaterOutputStream(compressed)) {
            for(int y=0;y<h;y++) { pixels.write(0); for(int x=0;x<w;x++) {
                int c=image.getPixelReader().getArgb(x,y); pixels.write(c>>16 &255);pixels.write(c>>8 &255);pixels.write(c&255);pixels.write(c>>>24);
            }}
        }
        try(var out=new DataOutputStream(Files.newOutputStream(path))) {
            out.writeLong(0x89504e470d0a1a0aL);
            var header=new ByteArrayOutputStream();var data=new DataOutputStream(header);
            data.writeInt(w);data.writeInt(h);data.write(new byte[]{8,6,0,0,0});
            chunk(out,"IHDR",header.toByteArray());chunk(out,"IDAT",compressed.toByteArray());chunk(out,"IEND",new byte[0]);
        }
    }
    private static void chunk(DataOutputStream out,String type,byte[] data)throws IOException {
        byte[] name=type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);var crc=new CRC32();crc.update(name);crc.update(data);
        out.writeInt(data.length);out.write(name);out.write(data);out.writeInt((int)crc.getValue());
    }
}
