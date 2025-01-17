## Docker

> Notice: This version is an adjusted version of the basemap with higher quality constraints!

```
docker build -t protomaps/basemaps .
```

## Planet Build

Requires more than 0.5 x OSM PBF of RAM (OSM PBF is currently about 79GB => ~40GB RAM), better . See also [here](https://github.com/onthegomap/planetiler/blob/main/PLANET.md).

> See also, the `planet-generation.sh` script which includes everything for running planet generation on a big machine.

```bash
docker run -v ./data:/tiles/data --rm -it protomaps/basemaps --entrypoint \
  `# Adjusting entrypoint to use more memory (made for a Hetzner CXX53 machine or similar)` \
  java -Xmx126g -XX:MaxHeapFreeRatio=40 -jar /tiles/target/protomaps-basemap-HEAD-with-deps.jar \
  `# Download the latest planet.osm.pbf from geofabrik` \
  --output=data/planet.pmtiles --area=planet --bounds=planet --download \
  `# Accelerate the download by fetching the 16x 1GB chunks at a time in parallel` \
  --download-threads=16 --download-chunk-size-mb=1000 \
  `# Store temporary node locations in memory (requires > 1.5x of OSM PBF), use '--storage=mmap' otherwise` \
  --nodemap-type=array --storage=ram
```

## Region Build (Example)

Good for testing out the current style.

```
docker run -v ./data:/tiles/data --rm -it protomaps/basemaps --output=data/baden-wuerttemberg.pmtiles --area=baden-wuerttemberg --download
```
