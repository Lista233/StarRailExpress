package org.agmas.noellesroles.game.modes.echo.map;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EchoMapConfigTest {
    @TempDir Path world;

    private String example() throws IOException {
        try(var input=getClass().getResourceAsStream("/echo-freight/example-map.json")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void exampleValidForAllPlayerCountsAndCoordinatesTranslateExactlyOnce() throws Exception {
        var map=EchoMapRepository.parse(example());
        for(int count=6;count<=12;count++) assertEquals(java.util.List.of(),EchoMapValidator.validate(map,count));
        var offset=new EchoMapConfig.Vec(100,10,200);
        var moved=map.translated(offset);
        assertEquals("world",moved.coordinateSpace());
        assertEquals(map.coreArea().minX()+100,moved.coreArea().minX());
        assertEquals(map.carrierSpawns().getFirst().z()+200,moved.carrierSpawns().getFirst().z());
        assertEquals(map.coreGate().doors().getFirst().volume().minY()+10,moved.coreGate().doors().getFirst().volume().minY());
        assertEquals(map.coreGate().freeExit().outsideSafe().x()+100,moved.coreGate().freeExit().outsideSafe().x());
        assertEquals(map.evacuation().exitDropAnchor().z()+200,moved.evacuation().exitDropAnchor().z());
        assertEquals(map.blackDropoffs().getFirst().pos().y()+10,moved.blackDropoffs().getFirst().pos().y());
        assertEquals(map.brokerExit().pos().x()+100,moved.brokerExit().pos().x());
        assertThrows(IllegalStateException.class,() -> moved.translated(offset));
        assertThrows(UnsupportedOperationException.class,() -> map.beacons().clear());
    }

    @Test void strictParserRejectsMissingMalformedTrailingDuplicateAndNonfinite() throws Exception {
        String json=example();
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json+" {}"));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("\"schemaVersion\": 1","\"schemaVersion\": 2")));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("\"schemaVersion\": 1","\"schemaVersion\": 4294967297")));
        assertThrows(IOException.class,() -> EchoMapRepository.parse("{\"schemaVersion\":1}"));
        assertThrows(IOException.class,() -> EchoMapRepository.parse("{broken}"));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("\"schemaVersion\": 1","\"schemaVersion\":1,\"schemaVersion\":1")));
        assertThrows(IOException.class,() -> EchoMapRepository.parse(json.replace("-45.5","NaN")));
        var root=JsonParser.parseString(json).getAsJsonObject();
        root.getAsJsonArray("carrierSpawns").get(0).getAsJsonObject().remove("x");
        assertThrows(IOException.class,() -> EchoMapRepository.parse(root.toString()));
    }

    @Test void fileLoadingRejectsTraversalMismatchAndLargeFiles() throws Exception {
        Path mapFile=world.resolve("echo_freight/maps/season1/echo_station.json");
        Files.createDirectories(mapFile.getParent());
        Files.writeString(mapFile,example());
        assertEquals("season1/echo_station",EchoMapRepository.load(world,"season1/echo_station").mapId());
        for(String id:new String[]{"../escape","/absolute","a//b","a/../b","a\\b","a:b","UPPER","a/./b"}) {
            assertThrows(IOException.class,() -> EchoMapRepository.load(world,id));
        }
        Files.writeString(mapFile,example().replace("season1/echo_station","different"));
        assertThrows(IOException.class,() -> EchoMapRepository.load(world,"season1/echo_station"));
        Files.writeString(mapFile," ".repeat(1024*1024+1));
        assertThrows(IOException.class,() -> EchoMapRepository.load(world,"season1/echo_station"));
    }

    @Test void duplicatePointsAndWrongRegionReportSpecificErrors() throws Exception {
        var root=JsonParser.parseString(example()).getAsJsonObject();
        root.getAsJsonArray("altars").get(1).getAsJsonObject().addProperty("id","a1");
        root.getAsJsonArray("beacons").get(0).getAsJsonObject().addProperty("region","core");
        var errors=EchoMapValidator.validate(EchoMapRepository.parse(root.toString()),6);
        assertTrue(errors.stream().anyMatch(s -> s.contains("altars")));
        assertTrue(errors.stream().anyMatch(s -> s.contains("beacons.outer_a")));
    }
}
