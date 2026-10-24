package org.agmas.noellesroles.game.modes.echo.state;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 机器投影、三语及原生模型资源的离线回归，不启动 Minecraft 注册表。 */
class EchoMachineViewTest {
    private EchoMachineView machine(EchoMachineView.Status status, int remaining) {
        return new EchoMachineView(12, 64, -4, EchoMachineView.Type.BEACON, "b", status, 3, 3, 999, remaining);
    }
    private JsonObject json(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }

    @Test void timersFreezeAndNeverTurnIntoLocalSuccess() {
        var cooldown = machine(EchoMachineView.Status.COOLDOWN, 40);
        assertEquals(2, cooldown.seconds(-1, false));
        assertEquals(2, cooldown.seconds(1000, true));
        assertEquals(2, cooldown.seconds(1000, false));
        var almost = machine(EchoMachineView.Status.COOLDOWN, 1);
        assertEquals(0, almost.seconds(10, false));
        assertFalse(almost.status().actionable());
        assertEquals(EchoMachineView.Status.COOLDOWN, almost.status());
        assertTrue(machine(EchoMachineView.Status.READY, 0).status().actionable());
    }
    @Test void boundedProjectionRejectsInvalidNetworkValues() {
        assertThrows(IllegalArgumentException.class, () -> machine(EchoMachineView.Status.READY, -1));
        assertThrows(IllegalArgumentException.class, () -> machine(EchoMachineView.Status.READY, 720001));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(Integer.MIN_VALUE,64,0,EchoMachineView.Type.BEACON,"x",EchoMachineView.Status.READY,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(0,64,0,EchoMachineView.Type.BEACON,"x".repeat(65),EchoMachineView.Status.READY,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(0,64,0,EchoMachineView.Type.BEACON,"x",EchoMachineView.Status.READY,4,3,0,0));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(0,64,0,EchoMachineView.Type.BEACON,"x",EchoMachineView.Status.READY,0,3,1001,0));
    }
    @Test void allNamesStatusesAndActionsHaveMatchingThreeLanguageKeys() throws Exception {
        Set<String> expected = null;
        for (String language : new String[]{"zh_cn", "zh_tw", "en_us"}) {
            var lang = json(Path.of("src/main/resources/assets/echo_machines/lang/" + language + ".json"));
            if (expected == null) expected = new HashSet<>(lang.keySet()); else assertEquals(expected, lang.keySet());
            for (var type : EchoMachineView.Type.values()) assertTrue(lang.has(type.nameKey()), type.nameKey());
            for (var status : EchoMachineView.Status.values()) assertTrue(lang.has(status.key()), status.key());
            for (String action : new String[]{"collect", "key", "search", "unlock", "leave", "deliver_black", "submit", "pollute", "interact", "overload"})
                assertTrue(lang.has("echo.machine.action." + action), action);
            for (var entry : lang.entrySet()) assertFalse(entry.getValue().getAsString().isBlank(), entry.getKey());
        }
    }
    @Test void everyMachineHasFourFacingsDetailedModelItemLootAndOpaqueTextures() throws Exception {
        Path assets = Path.of("src/main/resources/assets/noellesroles");
        Set<String> checked = new HashSet<>();
        for (var type : EchoMachineView.Type.values()) {
            String id = type.path(), reference = "noellesroles:block/" + id;
            var variants = json(assets.resolve("blockstates/" + id + ".json")).getAsJsonObject("variants");
            assertEquals(4, variants.size());
            String[] directions = {"north", "east", "south", "west"};
            for (int i = 0; i < 4; i++) {
                var variant = variants.getAsJsonObject("facing=" + directions[i]);
                assertEquals(reference, variant.get("model").getAsString());
                assertEquals(i * 90, variant.has("y") ? variant.get("y").getAsInt() : 0);
            }
            assertEquals(reference, json(assets.resolve("models/item/" + id + ".json")).get("parent").getAsString());
            var model = json(assets.resolve("models/block/" + id + ".json"));
            assertTrue(model.getAsJsonArray("elements").size() >= 10, id);
            var textures = model.getAsJsonObject("textures"); assertTrue(textures.has("particle"));
            for (var element : model.getAsJsonArray("elements")) {
                var e = element.getAsJsonObject();
                for (int axis = 0; axis < 3; axis++) {
                    double from = e.getAsJsonArray("from").get(axis).getAsDouble(), to = e.getAsJsonArray("to").get(axis).getAsDouble();
                    assertTrue(from >= 0 && to <= 16 && from < to, id);
                }
                if (e.has("rotation")) assertTrue(Set.of(-45D,-22.5D,0D,22.5D,45D).contains(e.getAsJsonObject("rotation").get("angle").getAsDouble()));
                for (var face : e.getAsJsonObject("faces").entrySet()) {
                    var f = face.getValue().getAsJsonObject();
                    for (var uv : f.getAsJsonArray("uv")) assertTrue(uv.getAsDouble() >= 0 && uv.getAsDouble() <= 16, id);
                    assertTrue(textures.has(f.get("texture").getAsString().substring(1)), id);
                }
            }
            for (var texture : textures.entrySet()) {
                String ref = texture.getValue().getAsString();
                while (ref.startsWith("#")) ref = textures.get(ref.substring(1)).getAsString();
                assertTrue(ref.startsWith("noellesroles:"), ref);
                if (!checked.add(ref)) continue;
                var image = ImageIO.read(assets.resolve("textures/" + ref.substring(ref.indexOf(':') + 1) + ".png").toFile());
                assertNotNull(image, ref); assertTrue(image.getWidth() == 32 || image.getWidth() == 64, ref);
                assertEquals(image.getWidth(), image.getHeight(), ref);
                for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) assertEquals(255, image.getRGB(x,y) >>> 24, ref);
            }
            var loot = json(Path.of("src/main/resources/data/noellesroles/loot_table/blocks/" + id + ".json"));
            assertTrue(loot.toString().contains("noellesroles:" + id), id);
        }
        assertEquals(12, EchoMachineView.Type.values().length);
    }
}
