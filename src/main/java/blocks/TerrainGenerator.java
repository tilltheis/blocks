package blocks;

import com.jme3.math.FastMath;

import java.util.Optional;
import java.util.Random;

public class TerrainGenerator {
  private final Noise mountainNoise;
  private final Noise flatlandNoise;
  private final Noise hillNoise;
  private final Noise oceanNoise;

  private final Noise treeNoise;

  public static final float LAND_LEVEL = -0.2f;

  public TerrainGenerator(long seed) {
    mountainNoise = new Noise(4, 0, 1500, 4.1, -4, 0, new Random(seed));
    flatlandNoise = new Noise(4, 0, 1500, 3.5, 0, 0, new Random(seed));
    hillNoise = new Noise(4, 0, 500, 3.5, 0, 0, new Random(seed));
    oceanNoise = new Noise(4, 0, 1000, 3.5, 0, 0, new Random(seed));
    treeNoise = new Noise(4, 0, 10, 4, 0, 0, new Random(seed));
  }

  // mu is percentage between x and y, must be in range (0, 1)
  private static float cosineInterpolation(float x, float y, float mu) {
    float mu2 = (1 - FastMath.cos(mu * FastMath.PI)) / 2;
    return y * (1 - mu2) + x * mu2;
  }

  public Terrain terrainAt(int x, int z) {
    float mountainValue = mountainNoise.getValue(x, z);
    float flatlandValue = flatlandNoise.getValue(x, z);
    float hillValue = hillNoise.getValue(x, z);
    float oceanValue = oceanNoise.getValue(x, z);

    float scaledMountainValue = mountainValue > 0 ? mountainValue * mountainValue : mountainValue;
    float scaledFlatlandValue = flatlandValue * 0.4f;
    float scaledHillValue = hillValue - 0.5f;
    float scaledOceanValue = oceanValue * 1;

    TerrainType terrainType = TerrainType.FLATLAND;

    float interpolatedValue = scaledFlatlandValue;

    float mountainDifference = scaledMountainValue - scaledFlatlandValue;

    if (mountainDifference > 0) {
      if (mountainDifference <= 0.2f) {
        float mu = mountainDifference * 5;
        interpolatedValue = cosineInterpolation(scaledMountainValue, scaledFlatlandValue, mu);
      } else {
        interpolatedValue = scaledMountainValue;
      }

      terrainType = TerrainType.MOUNTAIN;
    }

    if (scaledHillValue > 0) {
      interpolatedValue += scaledHillValue;
      terrainType = TerrainType.HILL;
    }

    if (scaledOceanValue < -0.2f) {
      interpolatedValue += (scaledOceanValue + 0.2f);
    }

    if (interpolatedValue < -0.2f) {
      terrainType = TerrainType.OCEAN;
    }

    Optional<Flora> flora = floraAt(x, z, terrainType);
    return new Terrain(terrainType, interpolatedValue, flora);
  }

  private Optional<Flora> floraAt(int x, int z, TerrainType terrainType) {
    if (terrainType == TerrainType.FLATLAND) {
      int scale = 1000;
      float treeValue = treeNoise.getValue(x * scale, z * scale);
      float scaledTreeValue = treeValue * treeValue * treeValue;
      boolean shouldSpawn = scaledTreeValue >= 0.5;
      if (shouldSpawn) return Optional.of(Flora.TREE);
    }

    return Optional.empty();
  }
}
