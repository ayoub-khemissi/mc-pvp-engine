package fr.ayoub.pvp.domain.fortress;

/**
 * Turning a fortress round, in blueprint coordinates.
 *
 * The two fortresses of a match <b>face each other</b>, and a blueprint's front is its
 * {@code z = 0} face (see {@link Blueprint}). One of the two therefore has to be turned half
 * way round when it is pasted, or a team ends up defending a fortress that has its back to
 * the enemy — gate facing away, blind wall facing the assault.
 *
 * Coordinates only. The <b>blocks</b> have to be turned as well — a staircase has to keep
 * pointing the way the builder pointed it — and that is Bukkit's job
 * ({@code BlockData.rotate}). This is the half of the problem that is arithmetic, so it is
 * the half that gets unit-tested.
 */
public enum CubeRotation {

    NONE,
    CLOCKWISE_90,
    HALF_TURN,
    COUNTERCLOCKWISE_90;

    /**
     * Where a block ends up. Clockwise is seen from above, so east becomes south.
     *
     * @param size the cube's edge, so the far corner is {@code size - 1}
     */
    public BlockPos apply(BlockPos pos, int size) {
        int last = size - 1;

        return switch (this) {
            case NONE -> pos;
            case CLOCKWISE_90 -> new BlockPos(last - pos.z(), pos.y(), pos.x());
            case HALF_TURN -> new BlockPos(last - pos.x(), pos.y(), last - pos.z());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.z(), pos.y(), last - pos.x());
        };
    }
}
