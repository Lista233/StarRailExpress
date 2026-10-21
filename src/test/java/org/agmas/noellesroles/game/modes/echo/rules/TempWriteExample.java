package org.agmas.noellesroles.game.modes.echo.rules;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** 临时：把 EchoRuleRepository.example() 落成 docs/echo-freight/rules.example.json，跑完即删。 */
class TempWriteExample {
    @Test void write() throws Exception {
        Files.createDirectories(Path.of("docs/echo-freight"));
        Files.writeString(Path.of("docs/echo-freight/rules.example.json"),
                EchoRuleRepository.example(), StandardCharsets.UTF_8);
    }
}
