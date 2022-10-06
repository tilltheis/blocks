package blocks;

import java.util.Optional;

record Terrain(TerrainType terrainType, float height, Optional<Flora> flora) {}
