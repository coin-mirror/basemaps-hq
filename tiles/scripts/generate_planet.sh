#!/bin/bash
set -e

# Script for generating world PMTiles on Hetzner Cloud using Docker
# Required environment variables:
# - HCLOUD_TOKEN: Hetzner Cloud API token
# - R2_ENDPOINT: Cloudflare R2 endpoint URL
# - R2_BUCKET: Bucket name
# - R2_ACCESS_KEY: R2 access key
# - R2_SECRET_KEY: R2 secret key

# Check required environment variables
if [[ -z "${HCLOUD_TOKEN}" ]]; then
  echo "Error: HCLOUD_TOKEN environment variable is not set"
  exit 1
fi

if [[ -z "${R2_ENDPOINT}" || -z "${R2_BUCKET}" || -z "${R2_ACCESS_KEY}" || -z "${R2_SECRET_KEY}" ]]; then
  echo "Error: R2 credentials environment variables are not set"
  exit 1
fi

# Install hcloud CLI if not installed
if ! command -v hcloud &> /dev/null; then
  echo "Installing hcloud CLI..."
  curl -sL https://github.com/hetznercloud/cli/releases/latest/download/hcloud-darwin-amd64.tar.gz | tar -xz
  chmod +x hcloud
  sudo mv hcloud /usr/local/bin/
fi

# Current date for naming files
CURRENT_DATE=$(date +"%Y%m%d")

SERVER_NAME="planetiler-${CURRENT_DATE}"
VOLUME_NAME="planetiler-data-${CURRENT_DATE}"

# Create a volume for storing data
echo "Creating Hetzner Volume..."
hcloud volume create --name $VOLUME_NAME --size 850 --location nbg1
sleep 2
VOLUME_ID=$(hcloud volume describe $VOLUME_NAME -o json | jq -r '.id')

# Create Hetzner server
echo "Creating Hetzner server..."
hcloud server create \
  --name $SERVER_NAME \
  --type ccx53 \
  --image docker-ce \
  --ssh-key ~/.ssh/id_ed25519.pub \
  --location nbg1

sleep 5

SERVER_IP=$(hcloud server describe $SERVER_NAME -o json | jq -r '.public_net.ipv4.ip')

# Check if the server IP is empty
if [[ -z "$SERVER_IP" ]]; then
  echo "Error: Failed to get server IP address"
  exit 1
fi

echo "Server created with IP: $SERVER_IP"

# Attach volume to the server
echo "Attaching volume to server..."
hcloud volume attach $VOLUME_NAME --automount --server $SERVER_NAME

# Wait for SSH to be available
echo "Waiting for SSH to be available..."
while ! ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 root@$SERVER_IP echo 'SSH ready'; do
  echo "Waiting for SSH..."
  sleep 10
done

# Install required dependencies directly
echo "Installing required dependencies..."
ssh -o StrictHostKeyChecking=no root@$SERVER_IP "apt-get update && apt-get install -y screen jq unzip rclone"
echo "✅ Dependencies installed"

# Format and mount the volume
echo "Formatting and mounting volume..."
ssh -o StrictHostKeyChecking=no root@$SERVER_IP "mkfs.ext4 /dev/disk/by-id/scsi-0HC_Volume_${VOLUME_ID} && \
mkdir -p /mnt/data && \
mount /dev/disk/by-id/scsi-0HC_Volume_${VOLUME_ID} /mnt/data && \
echo \"/dev/disk/by-id/scsi-0HC_Volume_${VOLUME_ID} /mnt/data ext4 defaults 0 0\" >> /etc/fstab && \
chmod 777 /mnt/data"
echo "✅ Volume mounted at /mnt/data"

# Create the build script on the server
ssh -o StrictHostKeyChecking=no root@$SERVER_IP bash -c "cat > /root/build_planet.sh << 'EOFSCRIPT'
#!/bin/bash
set -e

# Configure AWS CLI for R2
mkdir -p ~/.config/rclone
cat > ~/.config/rclone/rclone.conf << EOC
[r2]
type = s3
provider = Cloudflare
endpoint = ${R2_ENDPOINT}
acl = private
access_key_id = ${R2_ACCESS_KEY}
secret_access_key = ${R2_SECRET_KEY}
region = auto
EOC

# Clone repository
cd /root
git clone https://github.com/coin-mirror/basemaps-hq.git basemaps
cd basemaps/tiles

# Create a custom Dockerfile with optimized memory settings
cat > Dockerfile.planet << EOF
FROM maven:3-eclipse-temurin-22-alpine

WORKDIR /tiles
COPY src src
COPY pom.xml pom.xml

# Build the project
RUN mvn clean package

# Entrypoint with memory optimization for planet generation
ENTRYPOINT [\"java\", \"-Xmx112g\", \"-XX:MaxHeapFreeRatio=40\", \"-jar\", \"/tiles/target/protomaps-basemap-HEAD-with-deps.jar\"]
EOF

# Build the Docker image
echo \"Building Docker image for planet generation...\"
docker build -t protomaps/basemaps-planet:${CURRENT_DATE} -f Dockerfile.planet .
echo \"✅ Docker image built successfully\"

# Create data directory for output
OUTPUT_DIR=/root/output
mkdir -p \$OUTPUT_DIR

# Data directory (for base map data and planet files) - use mounted volume
DATA_DIR=/mnt/data
mkdir -p \$DATA_DIR/sources

# Run test build for Monaco using Docker
echo \"Starting test build for Monaco...\"
docker run --rm \\
  -v \$OUTPUT_DIR:/tiles/output \\
  -v \$DATA_DIR:/tiles/data \\
  protomaps/basemaps-planet:${CURRENT_DATE} \\
  --area=monaco --download \\
  --download-threads=10 --download-chunk-size-mb=1000 \\
  --fetch-wikidata \\
  --output=output/monaco-${CURRENT_DATE}.pmtiles \\
  --nodemap-type=sparsearray --nodemap-storage=ram

# Upload Monaco test build to R2
echo \"Uploading Monaco test build to R2...\"
rclone copy \$OUTPUT_DIR/monaco-${CURRENT_DATE}.pmtiles r2:${R2_BUCKET}/

echo \"✅ Monaco test build uploaded to R2\"

# Prepare world build script
cat > run_world_build.sh << EOF
#!/bin/bash
set -e

# Run world build using Docker
docker run --rm \\
  -v \$OUTPUT_DIR:/tiles/output \\
  -v \$DATA_DIR:/tiles/data \\
  protomaps/basemaps-planet:${CURRENT_DATE} \\
  --area=planet --bounds=world --download \\
  --download-threads=10 --download-chunk-size-mb=1000 \\
  --fetch-wikidata \\
  --output=output/${CURRENT_DATE}.pmtiles \\
  --nodemap-type=sparsearray --nodemap-storage=ram 2>&1 | tee \$OUTPUT_DIR/build-${CURRENT_DATE}.logs

# Upload to R2 bucket
rclone copy \$OUTPUT_DIR/${CURRENT_DATE}.pmtiles r2:${R2_BUCKET}/

# Upload logs
rclone copy \$OUTPUT_DIR/build-${CURRENT_DATE}.logs r2:${R2_BUCKET}/logs/

echo \"✅ World build completed and uploaded to R2\"
EOF

chmod +x run_world_build.sh

# Start world build in a screen session
echo \"Starting world build in a screen session...\"
screen -dmS world_build ./run_world_build.sh

echo \"World build started in background. You can attach to the session with: screen -r world_build\"
echo \"Check progress with: tail -f \${OUTPUT_DIR}/world_build_logs.txt\"
EOFSCRIPT"

# Make the build script executable and run it
ssh -o StrictHostKeyChecking=no root@$SERVER_IP "chmod +x /root/build_planet.sh && /root/build_planet.sh"

echo "Build process initiated on server $SERVER_IP"
echo "Connect to the server with: ssh root@$SERVER_IP"
echo "View world build progress with: tail -f /root/output/world_build_logs.txt" 