package fr.ayoub.pvp.domain.region;

import java.util.Locale;
import java.util.Map;

/**
 * Reads the {@code bounds} block of a map.yml.
 *
 * Takes a plain Map so it stays pure and testable — engine-core converts the
 * Bukkit configuration section into one.
 *
 * <pre>
 * bounds:
 *   type: cylinder
 *   center-x: 120
 *   center-z: 0
 *   radius: 25
 *   min-y: 60
 *   max-y: 90
 * </pre>
 */
public final class RegionParser {

    private RegionParser() {
    }

    public static Region parse(Map<String, Object> spec) {
        String type = string(spec, "type").toLowerCase(Locale.ROOT);

        return switch (type) {
            case "cuboid" -> new Region.Cuboid(
                    new Vec3(number(spec, "min-x"), number(spec, "min-y"), number(spec, "min-z")),
                    new Vec3(number(spec, "max-x"), number(spec, "max-y"), number(spec, "max-z")));

            case "cylinder" -> new Region.Cylinder(
                    number(spec, "center-x"),
                    number(spec, "center-z"),
                    number(spec, "radius"),
                    number(spec, "min-y"),
                    number(spec, "max-y"));

            case "sphere" -> new Region.Sphere(
                    new Vec3(number(spec, "center-x"), number(spec, "center-y"), number(spec, "center-z")),
                    number(spec, "radius"));

            default -> throw new IllegalArgumentException(
                    "unknown bounds type '" + type + "' (expected: cuboid, cylinder or sphere)");
        };
    }

    private static String string(Map<String, Object> spec, String key) {
        Object value = spec.get(key);
        if (value == null) {
            throw new IllegalArgumentException("bounds: missing '" + key + "'");
        }
        return value.toString();
    }

    private static double number(Map<String, Object> spec, String key) {
        Object value = spec.get(key);
        if (value == null) {
            throw new IllegalArgumentException("bounds: missing '" + key + "'");
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bounds: '" + key + "' must be a number, got '" + value + "'");
        }
    }
}
