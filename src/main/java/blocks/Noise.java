package blocks;

import java.util.Arrays;
import java.util.Objects;

public final class Noise {
  public int octaves;
  public double frequencyDivisor;
  public double lacunarity;
  public double granularity;
  public long[] seeds;

  public Noise(
      int octaves, double frequencyDivisor, double lacunarity, double granularity, long[] seeds) {
    this.octaves = octaves;
    this.seeds = seeds;
    this.frequencyDivisor = frequencyDivisor;
    this.lacunarity = lacunarity;
    this.granularity = granularity;
  }

  float getValue(int x, int z) {
    float total = 0;
    double frequency = 1d / frequencyDivisor;
    double gain = 1d / lacunarity;
    double amplitude = gain;
    double range = 0;
    for (int i = 0; i < octaves; ++i) {
      float noise = OpenSimplex2.noise2(seeds[i], x * frequency, z * frequency);
      total += noise * amplitude;
      range += amplitude;
      frequency *= lacunarity;
      amplitude *= gain;
    }
    total /= range; // scale to (-1, +1)
    if (granularity > 0) total = (float) (Math.round(total * granularity) / granularity);
    return total;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Noise noise = (Noise) o;
    return octaves == noise.octaves
        && Double.compare(noise.frequencyDivisor, frequencyDivisor) == 0
        && Double.compare(noise.lacunarity, lacunarity) == 0
        && Arrays.equals(seeds, noise.seeds);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(octaves, frequencyDivisor, lacunarity);
    result = 31 * result + Arrays.hashCode(seeds);
    return result;
  }

  @Override
  public String toString() {
    return "Noise{"
        + "octaves="
        + octaves
        + ", frequencyDivisor="
        + frequencyDivisor
        + ", lacunarity="
        + lacunarity
        + ", seeds="
        + Arrays.toString(seeds)
        + '}';
  }
}
