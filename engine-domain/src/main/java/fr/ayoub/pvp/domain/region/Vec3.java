package fr.ayoub.pvp.domain.region;

/** A point in the world. Deliberately not a Bukkit Location — this stays pure. */
public record Vec3(double x, double y, double z) {

    public double distanceSquared(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distance(Vec3 other) {
        return Math.sqrt(distanceSquared(other));
    }
}
