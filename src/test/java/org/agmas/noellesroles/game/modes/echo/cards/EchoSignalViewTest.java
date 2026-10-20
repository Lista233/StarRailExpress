package org.agmas.noellesroles.game.modes.echo.cards;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoSignalViewTest {
    @Test void everyDirectionKindRejectsTargetIdentity() {
        for(String kind:List.of("tracking","key_contract","salvage","cargo_pulse","key_pulse","hearing")) {
            assertThrows(IllegalArgumentException.class,() -> new EchoSignalView(kind,UUID.randomUUID(),0,20));
            assertNull(new EchoSignalView(kind,null,7,20).target());
        }
    }
    @Test void authorizedOutlinesKeepTheirTargetWhileSectorBoundsStayStrict() {
        UUID target=UUID.randomUUID(); assertEquals(target,new EchoSignalView("radar",target,0,20).target());
        assertThrows(IllegalArgumentException.class,() -> new EchoSignalView("key_pulse",null,8,20));
    }
}
