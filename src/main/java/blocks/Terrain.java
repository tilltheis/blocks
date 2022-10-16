package blocks;

import java.util.Optional;

record Terrain(
    TerrainType terrainType, float height, Temperature temperature, Optional<Flora> flora) {}
