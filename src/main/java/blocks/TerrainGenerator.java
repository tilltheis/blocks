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

  private final Noise heatNoise;

  private final long subterrainSeed;
  private final Noise subterrainNoise;

  public static final float LAND_LEVEL = -0.2f;

  private record TerrainHeight(TerrainType terrainType, float height, Temperature temperature) {}

  public TerrainGenerator(long seed) {
    mountainNoise = new Noise(4, 0, 1500, 4.1, -4, 0, new Random(seed));
    flatlandNoise = new Noise(4, 0, 1500, 3.5, 0, 0, new Random(seed));
    hillNoise = new Noise(4, 0, 500, 3.5, 0, 0, new Random(seed));
    oceanNoise = new Noise(4, 0, 1000, 3.5, 0, 0, new Random(seed));
    treeNoise = new Noise(4, 0, 10, 4, 0, 0, new Random(seed));

    heatNoise = new Noise(2, 0, 3000, 2, 0, 0, new Random(seed));

    subterrainSeed = new Random(seed).nextLong();
    subterrainNoise = new Noise(2, 0, 50, 6, 0.1f, 0, new Random(seed));
  }

  // mu is percentage between x and y, must be in range (0, 1)
  private static float cosineInterpolation(float x, float y, float mu) {
    float mu2 = (1 - FastMath.cos(mu * FastMath.PI)) / 2;
    return y * (1 - mu2) + x * mu2;
  }

  private TerrainHeight terrainHeightAt(int x, int z) {
    float mountainValue = mountainNoise.getValue(x, z);
    float flatlandValue = flatlandNoise.getValue(x, z);
    float hillValue = hillNoise.getValue(x, z);
    float oceanValue = oceanNoise.getValue(x, z);
    float heatValue = heatNoise.getValue(x, z);

    float scaledMountainValue = mountainValue > 0 ? mountainValue * mountainValue : mountainValue;
    float scaledFlatlandValue = flatlandValue * 0.4f;
    float scaledHillValue = hillValue - 0.5f;
    float scaledOceanValue = oceanValue * 1;
    float scaledHeatNoise = heatValue * heatValue * Math.signum(heatValue);

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

    float temperatureFalloff = 0.5f;
    return new TerrainHeight(
        terrainType,
        interpolatedValue,
        scaledHeatNoise < -temperatureFalloff
            ? Temperature.COLD
            : (heatValue <= 1 - temperatureFalloff ? Temperature.NORMAL : Temperature.HOT));
  }

  public Terrain terrainAt(int x, int z) {
    TerrainHeight terrainHeight = terrainHeightAt(x, z);
    Optional<Flora> flora = floraAt(x, z, terrainHeight.terrainType);
    return new Terrain(
        terrainHeight.terrainType, terrainHeight.height, terrainHeight.temperature, flora);
  }

  public Optional<TerrainType> subterrainAt(int x, int y, int z) {
    float factor = 0.01f;
    float horizontalFactor = 1;
    float noiseValue = subterrainNoise.getValue3(x, y, z);
    return noiseValue > 0.60f ? Optional.empty() : Optional.of(TerrainType.HILL);
  }

  public Optional<TerrainType> _subterrainAt(int x, int y, int z) {
    float factor = 0.003f;
    float horizontalFactor = 1;
    float noiseValue =
        OpenSimplex2.noise3_ImproveXZ(
            subterrainSeed,
            x * factor * horizontalFactor,
            z * factor * horizontalFactor,
            y * factor);
    return noiseValue * noiseValue > 0.60f ? Optional.empty() : Optional.of(TerrainType.HILL);
  }

  private Optional<Flora> floraAt(int x, int z, TerrainType terrainType) {
    if (terrainType == TerrainType.FLATLAND) {
      if (hasTreeAt(x, z)) return Optional.of(Flora.TREE);
    }

    return Optional.empty();
  }

  private boolean hasTreeAt(int x, int z) {
    int scale = 1000;
    float treeValue = treeNoise.getValue(x * scale, z * scale);
    float scaledTreeValue = treeValue * treeValue * treeValue;
    return scaledTreeValue >= 0.7;
  }
}
