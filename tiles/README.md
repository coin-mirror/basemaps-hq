## Docker

```
docker build -t protomaps/basemaps .
```

```
docker run -v ./data:/tiles/data --rm -it protomaps/basemaps --output=data/monaco.pmtiles --area=monaco --download
```

## Planet Generation

For generating a full planet PMTiles file, we provide an automated script that provisions a Hetzner Cloud instance and runs the planet generation process. See the [Planet Generation](/README.md#planet-generation) section in the main README for details.

### Script Usage

The script is located at `scripts/generate_planet.sh` and requires several environment variables to be set:

```bash
# Set required environment variables
export HCLOUD_TOKEN="your-hetzner-cloud-api-token"
export R2_ENDPOINT="https://your-account-id.r2.cloudflarestorage.com"
export R2_BUCKET="your-bucket-name"
export R2_ACCESS_KEY="your-r2-access-key"
export R2_SECRET_KEY="your-r2-secret-key"

# Run the script
chmod +x scripts/generate_planet.sh
./scripts/generate_planet.sh
```

### Planet Generation Parameters

The planet generation process uses the following parameters:

```bash
java -Xmx100g -XX:MaxHeapFreeRatio=40 \
  -jar target/*-with-deps.jar \
  --area=planet --bounds=world --download \
  --download-threads=10 --download-chunk-size-mb=1000 \
  --fetch-wikidata \
  --output=planet-YYYYMMDD.pmtiles \
  --nodemap-type=sparsearray --nodemap-storage=ram
```

For more details on these parameters, refer to the [Planetiler documentation](https://github.com/onthegomap/planetiler/blob/main/PLANET.md).

### Server Requirements

For generating a world map, the recommended server specifications are:
- At least 128GB RAM
- At least 500GB disk space
- Multiple CPU cores (16+ recommended)

The script automatically provisions a properly sized server on Hetzner Cloud.

