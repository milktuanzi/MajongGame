package com.campus.mahjong.integration;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** 与 javafx:run 一样启用模块边界；普通 classpath 测试无法发现 FXML 访问权限缺失。 */
class ModularGameStartupTest {
    @Test void fourReadyPlayersEnterTableWithProductionModuleBoundaries() throws Exception {
        var log = java.nio.file.Files.createTempFile("mahjong-module-startup-", ".log");
        String classpath = System.getProperty("java.class.path");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--module-path", classpath, "--add-modules", "com.campus.mahjong.contracts",
                "-cp", "target/test-classes", ModularGameStartupProbe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "模块启动测试超时：" + log);
            assertEquals(0, process.exitValue(), java.nio.file.Files.readString(log));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
