package blocks;

import com.jme3.math.FastMath;

import java.util.Random;

public class TerrainGenerator {
  private final Noise mountainNoise;
  private final Noise flatlandNoise;
  private final Noise hillNoise;
  private final Noise oceanNoise;

  public TerrainGenerator(long seed) {
    mountainNoise = new Noise(4, 0, 1500, 4.1, -4, 0, new Random(seed));
    flatlandNoise = new Noise(4, 0, 1500, 3.5, 0, 0, new Random(seed));
    hillNoise = new Noise(4, 0, 500, 3.5, 0, 0, new Random(seed));
    oceanNoise = new Noise(4, 0, 1000, 3.5, 0, 0, new Random(seed));
  }

  // mu is percentage between x and y, must be in range (0, 1)
  private static float cosineInterpolation(float x, float y, float mu) {
    float mu2 = (1 - FastMath.cos(mu * FastMath.PI)) / 2;
    return y * (1 - mu2) + x * mu2;
  }

  public TerrainHeight terrainHeightAt(int x, int z) {
    float mountainValue = mountainNoise.getValue(x, z);
    float flatlandValue = flatlandNoise.getValue(x, z);
    float hillValue = hillNoise.getValue(x, z);
    float oceanValue = oceanNoise.getValue(x, z);

    float scaledMountainValue = mountainValue > 0 ? mountainValue * mountainValue : mountainValue;
    float scaledFlatlandValue = flatlandValue * 0.4f;
    float scaledHillValue = hillValue - 0.5f;
    float scaledOceanValue = oceanValue * 1;

    Terrain terrain = Terrain.FLATLAND;

    float interpolatedValue = scaledFlatlandValue;

    float mountainDifference = scaledMountainValue - scaledFlatlandValue;

    if (mountainDifference > 0) {
      if (mountainDifference <= 0.2f) {
        float mu = mountainDifference * 5;
        interpolatedValue = cosineInterpolation(scaledMountainValue, scaledFlatlandValue, mu);
      } else {
        interpolatedValue = scaledMountainValue;
      }

      terrain = Terrain.MOUNTAIN;
    }

    if (scaledHillValue > 0) {
      interpolatedValue += scaledHillValue;
      terrain = Terrain.HILL;
    }

    if (scaledOceanValue < -0.2f) {
      interpolatedValue += (scaledOceanValue + 0.2f);
    }

    if (interpolatedValue < -0.2f) {
      terrain = Terrain.OCEAN;
    }

    return new TerrainHeight(terrain, interpolatedValue);
  }
}
