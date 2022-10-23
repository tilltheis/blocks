package blocks;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.stream.LongStream;

public final class Noise {
  public int octaves;
  public double startAmplitude;
  public double frequencyDivisor;
  public double lacunarity;
  public double gain;
  public double granularity;
  public long[] seeds;

  public Noise(
      int octaves,
      double startAmplitude,
      double frequencyDivisor,
      double lacunarity,
      double gain,
      double granularity,
      Random random) {
    if (octaves > 100) throw new IllegalArgumentException("octaves must be <= 100");

    this.octaves = octaves;
    this.startAmplitude = startAmplitude;
    this.frequencyDivisor = frequencyDivisor;
    this.lacunarity = lacunarity;
    this.gain = gain;
    this.granularity = granularity;

    setRandom(random);
  }

  public void setRandom(Random random) {
    // hard-coded big array because octaves is public mutable
    this.seeds = LongStream.range(0, 100).map(x -> random.nextLong()).toArray();
  }

  float getValue(int x, int y) {
    float total = 0;
    double frequency = 1d / frequencyDivisor;
    double gain = this.gain > 0 ? this.gain : 1d / lacunarity;
    double amplitude = startAmplitude > 0 ? startAmplitude : gain;
    double range = 0;
    for (int i = 0; i < octaves; ++i) {
      float noise = OpenSimplex2.noise2(seeds[i], x * frequency, y * frequency);
      total += noise * amplitude;
      range += amplitude;
      frequency *= lacunarity;
      amplitude *= gain;
    }
    total /= range; // scale to (-1, +1)
    if (granularity > 0) total = (float) (Math.round(total * granularity) / granularity);
    return total;
  }

  float getValue3(int x, int y, int z) {
    float total = 0;
    double frequency = 1d / frequencyDivisor;
    double gain = this.gain > 0 ? this.gain : 1d / lacunarity;
    double amplitude = startAmplitude > 0 ? startAmplitude : gain;
    double range = 0;
    for (int i = 0; i < octaves; ++i) {
      float noise =
          OpenSimplex2.noise3_ImproveXZ(seeds[i], x * frequency, z * frequency, y * frequency);
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
        && Double.compare(noise.startAmplitude, startAmplitude) == 0
        && Double.compare(noise.frequencyDivisor, frequencyDivisor) == 0
        && Double.compare(noise.lacunarity, lacunarity) == 0
        && Double.compare(noise.gain, gain) == 0
        && Double.compare(noise.granularity, granularity) == 0
        && Arrays.equals(seeds, noise.seeds);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(octaves, startAmplitude, frequencyDivisor, lacunarity, gain, granularity);
    result = 31 * result + Arrays.hashCode(seeds);
    return result;
  }

  @Override
  public String toString() {
    return "Noise{"
        + "octaves="
        + octaves
        + ", startAmplitude="
        + startAmplitude
        + ", frequencyDivisor="
        + frequencyDivisor
        + ", lacunarity="
        + lacunarity
        + ", gain="
        + gain
        + ", granularity="
        + granularity
        + ", seeds="
        + Arrays.toString(seeds)
        + '}';
  }
}
