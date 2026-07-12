package fr.ayoub.pvp.domain.region;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 'bounds' block of a map.yml. */
class RegionParserTest {

    @Test
    void parsesACuboid() {
        Region region = RegionParser.parse(Map.of(
                "type", "cuboid",
                "min-x", 0, "min-y", 60, "min-z", 0,
                "max-x", 20, "max-y", 70, "max-z", 20));

        assertInstanceOf(Region.Cuboid.class, region);
        assertTrue(region.contains(new Vec3(10, 65, 10)));
        assertTrue(!region.contains(new Vec3(30, 65, 10)));
    }

    @Test
    void parsesACylinder() {
        Region region = RegionParser.parse(Map.of(
                "type", "cylinder",
                "center-x", 120, "center-z", 0,
                "radius", 25,
                "min-y", 60, "max-y", 90));

        assertInstanceOf(Region.Cylinder.class, region);
        assertTrue(region.contains(new Vec3(140, 65, 0)));
        assertTrue(!region.contains(new Vec3(150, 65, 0)));
    }

    @Test
    void parsesASphere() {
        Region region = RegionParser.parse(Map.of(
                "type", "sphere",
                "center-x", 0, "center-y", 100, "center-z", 0,
                "radius", 10));

        assertInstanceOf(Region.Sphere.class, region);
        assertTrue(region.contains(new Vec3(0, 105, 0)));
    }

    @Test
    void theTypeIsCaseInsensitive() {
        assertInstanceOf(Region.Sphere.class, RegionParser.parse(Map.of(
                "type", "SPHERE",
                "center-x", 0, "center-y", 100, "center-z", 0,
                "radius", 10)));
    }

    @Test
    void anUnknownShapeIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RegionParser.parse(Map.of("type", "pyramid")));

        assertTrue(error.getMessage().contains("pyramid"), "the error must name the bad type");
    }

    @Test
    void aMissingTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RegionParser.parse(Map.of("radius", 10)));
    }

    @Test
    void aMissingValueIsReportedByName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RegionParser.parse(Map.of(
                        "type", "cylinder",
                        "center-x", 120, "center-z", 0,
                        "min-y", 60, "max-y", 90)));   // no radius

        assertTrue(error.getMessage().contains("radius"), "the error must name the missing key");
    }

    @Test
    void numbersMayBeWrittenAsIntegersOrDecimals() {
        Region region = RegionParser.parse(Map.of(
                "type", "cylinder",
                "center-x", 120.5, "center-z", 0,
                "radius", 25,
                "min-y", 60.0, "max-y", 90));

        assertEquals(120.5, ((Region.Cylinder) region).centerX(), 1e-9);
    }
}
