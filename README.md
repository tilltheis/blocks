# Blocks

A Minecraft-style world generator.

## Algorithm

Biome cells are first randomly placed on the map using Poisson disk sampling.
Then their shapes are determined by creating a Voronoi diagram out of all cell positions.
Finally, neighboring cells are merged together to achieve a minimum biome size.