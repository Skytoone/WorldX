package fr.skynex.worldx.region;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialIndexTest {

    private SpatialIndex spatialIndex;

    @BeforeEach
    public void setUp() {
        spatialIndex = new SpatialIndex();
    }

    @Test
    @DisplayName("Test region insertion and spatial chunk indexing")
    public void testAddAndGetRegionsInChunk() {
        Region region = new Region("spawn_zone", "world", 0, 0, 0, 100, 100, 100, ShapeType.CUBOID);
        region.addOwner(UUID.randomUUID());

        spatialIndex.addRegion(region);

        List<Region> inside = spatialIndex.getRegionsInChunk("world", 0, 0);
        assertEquals(1, inside.size());
        assertEquals("spawn_zone", inside.get(0).getId());

        List<Region> outside = spatialIndex.getRegionsInChunk("world", 50, 50);
        assertTrue(outside.isEmpty());
    }

    @Test
    @DisplayName("Test spatial index region removal")
    public void testRemoveRegion() {
        Region region = new Region("test_zone", "world", -50, 0, -50, 50, 50, 50, ShapeType.CUBOID);
        spatialIndex.addRegion(region);

        List<Region> beforeRemove = spatialIndex.getRegionsInChunk("world", 0, 0);
        assertFalse(beforeRemove.isEmpty());

        spatialIndex.removeRegion(region);

        List<Region> afterRemove = spatialIndex.getRegionsInChunk("world", 0, 0);
        assertTrue(afterRemove.isEmpty());
    }

    @Test
    @DisplayName("Test global shape type indexing")
    public void testGlobalRegionIndexing() {
        Region globalRegion = new Region("global_zone", "world");
        spatialIndex.addRegion(globalRegion);

        assertTrue(globalRegion.contains(1000, 100, 1000));
    }
}
