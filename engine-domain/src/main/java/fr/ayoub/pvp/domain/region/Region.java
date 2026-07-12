package fr.ayoub.pvp.domain.region;

/**
 * The playable area of an arena — the "invisible wall".
 *
 * Two operations are all the engine needs:
 *   - {@link #contains} : is the player still inside?
 *   - {@link #nearestInside} : where do we push them back to?
 *
 * Pure geometry, no Bukkit, fully unit-tested.
 */
public sealed interface Region permits Region.Cuboid, Region.Cylinder, Region.Sphere {

    boolean contains(double x, double y, double z);

    default boolean contains(Vec3 point) {
        return contains(point.x(), point.y(), point.z());
    }

    /** The centre of the area (used to push players back towards it). */
    Vec3 center();

    /**
     * The closest point that is still inside.
     * A point already inside is returned unchanged.
     */
    Vec3 nearestInside(Vec3 point);

    // ---------------------------------------------------------------------------

    /** A box. Corners may be given in any order. */
    record Cuboid(Vec3 min, Vec3 max) implements Region {

        public Cuboid {
            Vec3 low = new Vec3(
                    Math.min(min.x(), max.x()),
                    Math.min(min.y(), max.y()),
                    Math.min(min.z(), max.z()));
            Vec3 high = new Vec3(
                    Math.max(min.x(), max.x()),
                    Math.max(min.y(), max.y()),
                    Math.max(min.z(), max.z()));
            min = low;
            max = high;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            return x >= min.x() && x <= max.x()
                    && y >= min.y() && y <= max.y()
                    && z >= min.z() && z <= max.z();
        }

        @Override
        public Vec3 center() {
            return new Vec3(
                    (min.x() + max.x()) / 2,
                    (min.y() + max.y()) / 2,
                    (min.z() + max.z()) / 2);
        }

        @Override
        public Vec3 nearestInside(Vec3 point) {
            return new Vec3(
                    clamp(point.x(), min.x(), max.x()),
                    clamp(point.y(), min.y(), max.y()),
                    clamp(point.z(), min.z(), max.z()));
        }
    }

    // ---------------------------------------------------------------------------

    /** A ring: a circle on the X/Z plane, between two heights. The usual arena shape. */
    record Cylinder(double centerX, double centerZ, double radius, double minY, double maxY) implements Region {

        public Cylinder {
            if (radius <= 0) {
                throw new IllegalArgumentException("radius must be > 0, got " + radius);
            }
            double low = Math.min(minY, maxY);
            double high = Math.max(minY, maxY);
            minY = low;
            maxY = high;
        }

        @Override
        public boolean contains(double x, double y, double z) {
            if (y < minY || y > maxY) {
                return false;
            }
            double dx = x - centerX;
            double dz = z - centerZ;
            return dx * dx + dz * dz <= radius * radius;
        }

        @Override
        public Vec3 center() {
            return new Vec3(centerX, (minY + maxY) / 2, centerZ);
        }

        @Override
        public Vec3 nearestInside(Vec3 point) {
            double y = clamp(point.y(), minY, maxY);

            double dx = point.x() - centerX;
            double dz = point.z() - centerZ;
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance <= radius) {
                return new Vec3(point.x(), y, point.z());
            }
            // pull the point back onto the wall, keeping its direction
            double scale = radius / distance;
            return new Vec3(centerX + dx * scale, y, centerZ + dz * scale);
        }
    }

    // ---------------------------------------------------------------------------

    /** A ball. Handy for sky arenas. */
    record Sphere(Vec3 center, double radius) implements Region {

        public Sphere {
            if (radius <= 0) {
                throw new IllegalArgumentException("radius must be > 0, got " + radius);
            }
        }

        @Override
        public boolean contains(double x, double y, double z) {
            double dx = x - center.x();
            double dy = y - center.y();
            double dz = z - center.z();
            return dx * dx + dy * dy + dz * dz <= radius * radius;
        }

        @Override
        public Vec3 center() {
            return center;
        }

        @Override
        public Vec3 nearestInside(Vec3 point) {
            double distance = point.distance(center);
            if (distance <= radius) {
                return point;
            }
            double scale = radius / distance;
            return new Vec3(
                    center.x() + (point.x() - center.x()) * scale,
                    center.y() + (point.y() - center.y()) * scale,
                    center.z() + (point.z() - center.z()) * scale);
        }
    }

    // ---------------------------------------------------------------------------

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
