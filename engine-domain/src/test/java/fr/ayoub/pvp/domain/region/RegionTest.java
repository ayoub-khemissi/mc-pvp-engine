package fr.ayoub.pvp.domain.region;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTest {

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-9, "x");
        assertEquals(expected.y(), actual.y(), 1e-9, "y");
        assertEquals(expected.z(), actual.z(), 1e-9, "z");
    }

    @Nested
    class Cuboids {

        // a 20x10x20 box from (0,60,0) to (20,70,20)
        private final Region box = new Region.Cuboid(new Vec3(0, 60, 0), new Vec3(20, 70, 20));

        @Test
        void containsAPointInside() {
            assertTrue(box.contains(new Vec3(10, 65, 10)));
        }

        @Test
        void theEdgeCounts() {
            assertTrue(box.contains(new Vec3(0, 60, 0)));
            assertTrue(box.contains(new Vec3(20, 70, 20)));
        }

        @Test
        void rejectsAPointOutside() {
            assertFalse(box.contains(new Vec3(21, 65, 10)), "past +x");
            assertFalse(box.contains(new Vec3(10, 59, 10)), "below");
            assertFalse(box.contains(new Vec3(10, 65, -1)), "past -z");
        }

        @Test
        void cornersCanBeGivenInAnyOrder() {
            Region swapped = new Region.Cuboid(new Vec3(20, 70, 20), new Vec3(0, 60, 0));
            assertTrue(swapped.contains(new Vec3(10, 65, 10)));
        }

        @Test
        void aPointInsideIsAlreadyTheNearestPoint() {
            Vec3 inside = new Vec3(10, 65, 10);
            assertVec(inside, box.nearestInside(inside));
        }

        @Test
        void aPointOutsideIsClampedBackIn() {
            assertVec(new Vec3(20, 70, 20), box.nearestInside(new Vec3(50, 100, 50)));
            assertVec(new Vec3(0, 60, 10), box.nearestInside(new Vec3(-5, 20, 10)));
        }

        @Test
        void theCentreIsInTheMiddle() {
            assertVec(new Vec3(10, 65, 10), box.center());
        }
    }

    @Nested
    class Cylinders {

        // a ring of radius 25 around (120, ?, 0), from y=60 to y=90
        private final Region ring = new Region.Cylinder(120, 0, 25, 60, 90);

        @Test
        void containsAPointInsideTheRing() {
            assertTrue(ring.contains(new Vec3(120, 65, 0)), "the axis");
            assertTrue(ring.contains(new Vec3(140, 65, 0)), "20 blocks out, radius is 25");
        }

        @Test
        void rejectsAPointOutsideTheRadius() {
            assertFalse(ring.contains(new Vec3(150, 65, 0)), "30 blocks out, radius is 25");
        }

        @Test
        void heightStillMatters() {
            assertFalse(ring.contains(new Vec3(120, 59, 0)), "below the floor");
            assertFalse(ring.contains(new Vec3(120, 91, 0)), "above the ceiling");
        }

        @Test
        void aPointTooHighIsPulledDown() {
            assertVec(new Vec3(120, 90, 0), ring.nearestInside(new Vec3(120, 200, 0)));
        }

        @Test
        void aPointOutsideTheRadiusIsPulledOntoTheWall() {
            // straight out along +x: the nearest point inside is on the wall, at radius 25
            assertVec(new Vec3(145, 65, 0), ring.nearestInside(new Vec3(200, 65, 0)));
        }

        @Test
        void aRadiusMustBePositive() {
            assertThrows(IllegalArgumentException.class, () -> new Region.Cylinder(0, 0, 0, 60, 90));
        }
    }

    @Nested
    class Spheres {

        private final Region ball = new Region.Sphere(new Vec3(0, 100, 0), 10);

        @Test
        void containsAPointInside() {
            assertTrue(ball.contains(new Vec3(0, 100, 0)));
            assertTrue(ball.contains(new Vec3(0, 110, 0)), "the surface counts");
        }

        @Test
        void rejectsAPointOutside() {
            assertFalse(ball.contains(new Vec3(0, 111, 0)));
        }

        @Test
        void aPointOutsideIsPulledOntoTheSurface() {
            assertVec(new Vec3(0, 110, 0), ball.nearestInside(new Vec3(0, 500, 0)));
        }

        @Test
        void aPointInsideIsUnchanged() {
            Vec3 inside = new Vec3(1, 101, 1);
            assertVec(inside, ball.nearestInside(inside));
        }
    }
}
