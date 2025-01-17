#!/bin/bash

# Building the complete planet on a Hetzner CCX53 instance for pmtiles and uploading to Cloudflare R2

set -e

docker pull ghcr.io/coin-mirror/basemaps-hq:latest

# Install unzip if not present
if ! command -v unzip &> /dev/null; then
  sudo apt-get update
  sudo apt-get install -y unzip
fi

# Install AWS CLI if not present
if ! command -v aws &> /dev/null; then
  # Download and install AWS CLI
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
  unzip awscliv2.zip
  sudo ./aws/install

  # Clean up downloaded files
  rm -rf aws awscliv2.zip
fi

mkdir -p ./data/sources

# Download planet.osm.pbf from OSM PDS
aws s3 cp --no-sign-request s3://osm-pds/planet-latest.osm.pbf ./data/sources/planet.osm.pbf

# Requires a machine with at least 110GB of RAM (we are using CCX53 from Hetzner)
# May enable SWAP on the machine to avoid OOM errors: https://www.digitalocean.com/community/tutorials/how-to-add-swap-space-on-ubuntu-20-04
cat << EOL > Dockerfile
FROM maven:3-eclipse-temurin-22-alpine
WORKDIR /tiles
COPY src src
COPY pom.xml pom.xml
RUN mvn clean package
ENTRYPOINT ["java","-Xmx110g","-XX:MaxHeapFreeRatio=40","-jar","/tiles/target/protomaps-basemap-HEAD-with-deps.jar"]
EOL

docker build -t protomaps/basemaps .

docker run -v ./data:/tiles/data --rm -it protomaps/basemaps \
  --output=data/planet.pmtiles --area=planet --bounds=planet --download \
  --download-threads=16 --download-chunk-size-mb=1000 \
  --nodemap-type=array --storage=ram

# NOTICE: The .env.local file is not checked into the repo, so it needs to be created manually! (see .env.example)
source .env.local

VERSION=$(date +%Y%m%d)

# Uploading to Cloudflare R2
AWS_ACCESS_KEY_ID=$R2_ACCESS_KEY AWS_SECRET_ACCESS_KEY=$R2_SECRET_KEY aws s3 --endpoint-url $R2_ENDPOINT cp ./data/planet.pmtiles s3://$R2_BUCKET/$VERSION.pmtiles
