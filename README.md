# Protomaps Basemaps

Build a cartographic "basemap" PMTiles from OpenStreetMap + other datasources, plus MapLibre styles for displaying them in a browser.

To get started, check out some basic examples:

* [display markers on a map](https://maps.protomaps.com/examples/basic.html)
* [change the map theme and language](https://maps.protomaps.com/examples/theme_language.html)
* [sandwich data between map layers](https://maps.protomaps.com/examples/data_sandwich.html)

This repository is organized into parts:

* `app`: A single-page app, [maps.protomaps.com](maps.protomaps.com) for viewing and downloading basemap builds.
* `tiles/`: A [Planetiler](https://github.com/onthegomap/planetiler) build profile that generates `planet.pmtiles` from OpenStreetMap and Natural Earth in 2-3 hours on a modest computer.
* `styles/`: A TypeScript package that generates [MapLibre GL](http://github.com/maplibre) styles, in multiple color themes, that can be used via `npm` or exported as JSON.

Assets such as fonts and sprites are hosted and downloadable at the [basemaps-assets](https://github.com/protomaps/basemaps-assets) repository.

## Development

You will need [Java 21+](https://github.com/onthegomap/planetiler/blob/main/CONTRIBUTING.md) and [Maven](https://maven.apache.org/install.html) installed, which is available in most package managers.

Generate and inspect a basemap PMTiles of any named area:

1. Clone this repository.

```shell
git clone git@github.com:protomaps/basemaps.git
```
2. change to the `tiles` directory, download dependencies and compile the JAR:

```shell
cd basemaps/tiles
mvn clean package
```
3. Download and generate `monaco.pmtiles` in the current directory:

```shell
java -jar target/*-with-deps.jar --download --force --area=monaco
```

4. Switch to the `app/` directory to run the map frontend:

```shell
cd app
nvm use
npm ci
npm run dev
```

The locally generated pmtiles can be fetched from http://localhost:5173/monaco.pmtiles

5. Linting to apply code formatting

```shell
mvn spotless:apply
```

## Licensing and Attribution Guidelines

Summary:

* All code is BSD-3
* Map design is CC0
* Tilesets are ODbL, attribute OSM
* We like shoutouts!
* Name your fork, commercial product or service something different

We kindly request that you attribute the Protomaps project if you use the basemap styles unmodified or with additions, but you are not required to do so. The visual design copyright of the maps is released under a [Creative Commons Zero (CC0) license](https://creativecommons.org/publicdomain/zero/1.0/) to eliminate ambiguity around derivative or combined works of map styling code, and to publish software, such as NPM packages, under the standard BSD-3 license.

The tilesets that power the Protomaps basemap are [Produced Works of the OpenStreetMap dataset](https://osmfoundation.org/wiki/Licence/Community_Guidelines/Produced_Work_-_Guideline) under the [Open Database License](https://www.openstreetmap.org/copyright). Web maps and native apps that use this Produced Work must visibly attribute © OpenStreetMap - for example, in the corner of the map display. If your map only uses the Map Styles with non-OpenStreetMap tilesets, this attribution is not required.

Example web map corner attribution:

```html
<a href="https://github.com/protomaps/basemaps">Protomaps</a> © <a href="https://openstreetmap.org">OpenStreetMap</a>
```

Tilesets also include data from [Natural Earth](https://www.naturalearthdata.com) and other sources, which does not require corner attribution. Other data sources your map uses may require additional attribution: See [LICENSE_DATA.md](/LICENSE_DATA.md).

Depending on map style, MIT-licensed icons may also be included from the [Tangram Icons repository](https://github.com/tangrams/icons/issues).

The software in this repository is made available under a [BSD 3-Clause License](/LICENSE.md), and includes license notices related to Protomaps LLC, Mapzen, and the Linux Foundation. You must retain these license notices in software source code derived from this repository.

If you distribute a modified "fork" of these basemap styles or tilesets, or provide a tiles API based on them, you must name your product or service something different from Protomaps. Free and unmodified redistributions of tiles and styles are permitted to use the name. No restrictions apply to the underlying technology `.pmtiles` which is an [open specification in the public domain.](https://github.com/protomaps/PMTiles#license)

These guidelines are subject to change with the addition of other open datasets. Any questions can be addressed to [support@protomaps.com](mailto:support@protomaps.com).

## Planet Generation

The `tiles` directory contains tools for generating PMTiles files from OpenStreetMap data. For small regions, you can use the instructions in the [Development](#development) section above. For generating a full planet PMTiles file, you'll need a more powerful machine.

### Automated Planet Generation

A script is provided in `tiles/scripts/generate_planet.sh` to automate the process of generating a planet PMTiles file. This script:

1. Creates a properly sized Hetzner Cloud instance (~128GB RAM)
2. Installs all required dependencies (Java 21, Maven, Docker, etc.)
3. Builds the project and runs a test build for Monaco
4. Uploads the Monaco test build to an R2 bucket
5. Starts the full planet build in a background session
6. Uploads the final planet PMTiles file to R2 when complete

#### Prerequisites

To use the automated planet generation script, you'll need:

1. A Hetzner Cloud account and API token
2. A Cloudflare R2 bucket for storing the generated files
3. The following environment variables set:
   - `HCLOUD_TOKEN`: Hetzner Cloud API token
   - `R2_ENDPOINT`: Cloudflare R2 endpoint URL
   - `R2_BUCKET`: R2 bucket name
   - `R2_ACCESS_KEY`: R2 access key
   - `R2_SECRET_KEY`: R2 secret key

#### Running the Script

```bash
cd tiles/scripts
chmod +x generate_planet.sh
./generate_planet.sh
```

The script will output the server IP address and instructions for connecting to monitor the build progress. The planet generation process can take several hours (typically 2-3 hours on dedicated server hardware).

### Manual Planet Generation

If you prefer to set up your own server manually, follow these steps:

1. Provision a server with at least 128GB RAM and sufficient disk space (at least 500GB)
2. Install Java 21+, Maven, and other dependencies
3. Clone this repository and navigate to the `tiles` directory
4. Build the project with `mvn clean package`
5. Run the planet generation command:

```bash
java -Xmx100g -XX:MaxHeapFreeRatio=40 \
  -jar target/*-with-deps.jar \
  --area=planet --bounds=world --download \
  --download-threads=10 --download-chunk-size-mb=1000 \
  --fetch-wikidata \
  --output=planet.pmtiles \
  --nodemap-type=sparsearray --nodemap-storage=ram
```

For more details on planet generation options, refer to the [Planetiler documentation](https://github.com/onthegomap/planetiler/blob/main/PLANET.md).
