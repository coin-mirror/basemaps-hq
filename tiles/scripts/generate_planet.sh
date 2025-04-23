#!/bin/bash
set -e

# Script for generating world PMTiles on Hetzner Cloud
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

# Create Hetzner server
echo "Creating Hetzner server..."
SERVER_ID=$(hcloud server create \
  --name "planetiler-${CURRENT_DATE}" \
  --type cx53 \
  --image ubuntu-22.04 \
  --ssh-key ~/.ssh/id_rsa.pub \
  --user-data-from-file cloud-init.yml \
  --datacenter nbg1-dc3 \
  --output json | jq -r '.id')

SERVER_IP=$(hcloud server describe $SERVER_ID -o json | jq -r '.public_net.ipv4.ip')

echo "Server created with IP: $SERVER_IP"

# Wait for SSH to be available
echo "Waiting for SSH to be available..."
while ! ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 root@$SERVER_IP echo 'SSH ready'; do
  echo "Waiting for SSH..."
  sleep 10
done

# Create cloud-init.yml file for server setup
cat > cloud-init.yml << 'EOF'
#cloud-config
package_update: true
package_upgrade: true
packages:
  - apt-transport-https
  - ca-certificates
  - curl
  - gnupg
  - lsb-release
  - jq
  - openjdk-21-jre-headless
  - maven
  - screen
  - awscli

runcmd:
  # Install Docker
  - curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
  - echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
  - apt-get update
  - apt-get install -y docker-ce docker-ce-cli containerd.io
  - systemctl enable docker
  - systemctl start docker
EOF

# Copy the script to the server
scp -o StrictHostKeyChecking=no cloud-init.yml root@$SERVER_IP:/root/
rm cloud-init.yml

# Create the build script on the server
ssh -o StrictHostKeyChecking=no root@$SERVER_IP bash -c "cat > /root/build_planet.sh << 'EOFSCRIPT'
#!/bin/bash
set -e

# Configure AWS CLI for R2
mkdir -p ~/.aws
cat > ~/.aws/credentials << EOC
[default]
aws_access_key_id=${R2_ACCESS_KEY}
aws_secret_access_key=${R2_SECRET_KEY}
EOC

# Clone repository
cd /root
git clone https://github.com/protomaps/basemaps.git
cd basemaps/tiles

# Build the project
mvn clean package

# Current date for file naming
CURRENT_DATE=\$(date +\"%Y%m%d\")

# Run test build for Monaco
echo \"Starting test build for Monaco...\"
java -jar target/*-with-deps.jar --download --force --area=monaco --output=monaco-\${CURRENT_DATE}.pmtiles

# Upload Monaco test build to R2
echo \"Uploading Monaco test build to R2...\"
aws s3 cp monaco-\${CURRENT_DATE}.pmtiles s3://${R2_BUCKET}/monaco-\${CURRENT_DATE}.pmtiles \
  --endpoint-url ${R2_ENDPOINT}

# Prepare world build script
cat > run_world_build.sh << EOF
#!/bin/bash
set -e

cd /root/basemaps/tiles

# Run world build
java -Xmx100g \\
  -XX:MaxHeapFreeRatio=40 \\
  -jar target/*-with-deps.jar \\
  --area=planet --bounds=world --download \\
  --download-threads=10 --download-chunk-size-mb=1000 \\
  --fetch-wikidata \\
  --output=planet-\${CURRENT_DATE}.pmtiles \\
  --nodemap-type=sparsearray --nodemap-storage=ram 2>&1 | tee world_build_logs.txt

# Upload to R2 bucket
aws s3 cp planet-\${CURRENT_DATE}.pmtiles s3://${R2_BUCKET}/planet-\${CURRENT_DATE}.pmtiles \\
  --endpoint-url ${R2_ENDPOINT}

# Upload logs
aws s3 cp world_build_logs.txt s3://${R2_BUCKET}/logs/planet-\${CURRENT_DATE}.txt \\
  --endpoint-url ${R2_ENDPOINT}

echo \"World build completed and uploaded to R2\"
EOF

chmod +x run_world_build.sh

# Start world build in a screen session
echo \"Starting world build in a screen session...\"
screen -dmS world_build ./run_world_build.sh

echo \"World build started in background. You can attach to the session with: screen -r world_build\"
echo \"Check progress with: tail -f /root/basemaps/tiles/world_build_logs.txt\"
EOFSCRIPT"

# Make the build script executable and run it
ssh -o StrictHostKeyChecking=no root@$SERVER_IP "chmod +x /root/build_planet.sh && /root/build_planet.sh"

echo "Build process initiated on server $SERVER_IP"
echo "Connect to the server with: ssh root@$SERVER_IP"
echo "View world build progress with: tail -f /root/basemaps/tiles/world_build_logs.txt" 