#!/usr/bin/env bash
#
# PvP Engine — production install on Ubuntu (OVH).
#
# Idempotent: safe to run again. It installs Java 25, MySQL, Paper, the plugins,
# a systemd service and a firewall rule, then starts the server.
#
#   sudo ./install.sh
#
set -euo pipefail

MC_VERSION="26.1.2"
SERVER_DIR="/opt/pvp-server"
SERVICE_USER="mcpvp"
DB_NAME="pvp_engine"
DB_USER="pvp"
XMS="${XMS:-2G}"
XMX="${XMX:-4G}"

log()  { echo -e "\e[36m[install]\e[0m $*"; }
warn() { echo -e "\e[33m[warn]\e[0m $*"; }
die()  { echo -e "\e[31m[error]\e[0m $*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run me with sudo"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/dist"

# ---------------------------------------------------------------------------
log "1/8  packages"
# ---------------------------------------------------------------------------
apt-get update -qq
apt-get install -y -qq curl jq wget gnupg ca-certificates ufw >/dev/null

# ---------------------------------------------------------------------------
log "2/8  Java 25 (Minecraft 26.x will not run on anything older)"
# ---------------------------------------------------------------------------
if ! java -version 2>&1 | grep -q '"25'; then
    install -d -m 0755 /etc/apt/keyrings
    if [[ ! -f /etc/apt/keyrings/adoptium.asc ]]; then
        wget -qO /etc/apt/keyrings/adoptium.asc https://packages.adoptium.net/artifactory/api/gpg/key/public
    fi
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
        > /etc/apt/sources.list.d/adoptium.list
    apt-get update -qq
    apt-get install -y -qq temurin-25-jdk >/dev/null
fi
java -version 2>&1 | head -1

# ---------------------------------------------------------------------------
log "3/8  MySQL"
# ---------------------------------------------------------------------------
apt-get install -y -qq mysql-server >/dev/null
systemctl enable --now mysql

# MySQL listens only on localhost by default on Ubuntu. Make sure of it.
if grep -qE '^\s*bind-address\s*=\s*0\.0\.0\.0' /etc/mysql/mysql.conf.d/mysqld.cnf 2>/dev/null; then
    warn "MySQL was listening on all interfaces — restricting it to localhost"
    sed -i 's/^\s*bind-address.*/bind-address = 127.0.0.1/' /etc/mysql/mysql.conf.d/mysqld.cnf
    systemctl restart mysql
fi

CRED_FILE="/root/.pvp-db-credentials"
if [[ -f "$CRED_FILE" ]]; then
    DB_PASS="$(grep -oP '(?<=^password=).*' "$CRED_FILE")"
    log "reusing the existing database password ($CRED_FILE)"
else
    DB_PASS="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
    printf 'user=%s\npassword=%s\ndatabase=%s\n' "$DB_USER" "$DB_PASS" "$DB_NAME" > "$CRED_FILE"
    chmod 600 "$CRED_FILE"
    log "generated a database password → $CRED_FILE"
fi

mysql <<SQL
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
# The plugin creates its own tables (migrations) on first start.

# ---------------------------------------------------------------------------
log "4/8  server user + directory"
# ---------------------------------------------------------------------------
id -u "$SERVICE_USER" >/dev/null 2>&1 || useradd --system --create-home --shell /usr/sbin/nologin "$SERVICE_USER"
mkdir -p "$SERVER_DIR/plugins/PvPEngine"

# ---------------------------------------------------------------------------
log "5/8  Paper $MC_VERSION"
# ---------------------------------------------------------------------------
BUILD_JSON="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds")"
BUILD_ID="$(echo "$BUILD_JSON" | jq -r '.[0].id')"
PAPER_URL="$(echo "$BUILD_JSON" | jq -r '.[0].downloads."server:default".url')"
PAPER_JAR="$SERVER_DIR/paper-${MC_VERSION}-${BUILD_ID}.jar"

if [[ ! -f "$PAPER_JAR" ]]; then
    log "downloading Paper build $BUILD_ID"
    curl -fsSL -o "$PAPER_JAR" "$PAPER_URL"
fi
ln -sf "$PAPER_JAR" "$SERVER_DIR/paper.jar"

# ---------------------------------------------------------------------------
log "6/8  plugins + configuration"
# ---------------------------------------------------------------------------
[[ -d "$DIST_DIR" ]] || die "no dist/ folder — run deploy/build.sh first (or upload the jars there)"
cp -f "$DIST_DIR"/*.jar "$SERVER_DIR/plugins/"
ls "$SERVER_DIR/plugins/"*.jar

echo "eula=true" > "$SERVER_DIR/eula.txt"

# server.properties — written once, then left alone so your edits survive.
if [[ ! -f "$SERVER_DIR/server.properties" ]]; then
cat > "$SERVER_DIR/server.properties" <<'PROPS'
server-port=25565
# MUST stay true in production: false lets anyone log in as any username, including an operator.
online-mode=true
motd=PvP
max-players=100
difficulty=normal
gamemode=adventure
allow-flight=true
spawn-protection=0
view-distance=6
simulation-distance=4
level-name=world
level-type=minecraft:flat
generate-structures=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
enable-command-block=false
enable-rcon=false
PROPS
fi

# The plugin config is rewritten every run so the DB password always matches.
cat > "$SERVER_DIR/plugins/PvPEngine/config.yml" <<YML
database:
  host: 127.0.0.1
  port: 3306
  name: ${DB_NAME}
  user: ${DB_USER}
  password: "${DB_PASS}"
  pool-size: 10

world:
  name: pvp
  auto-create: true
  # Builds the lobby + 4 arenas on first start, if no map exists yet.
  # Nothing to upload, nothing to type. Set to 0 once you have designed maps.
  auto-setup-arenas: 4

lobby:
  world: pvp
  x: 0.5
  y: 101.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
YML
chmod 640 "$SERVER_DIR/plugins/PvPEngine/config.yml"
chown -R "$SERVICE_USER:$SERVICE_USER" "$SERVER_DIR"

# ---------------------------------------------------------------------------
log "7/8  systemd service"
# ---------------------------------------------------------------------------
sed -e "s|@SERVER_DIR@|$SERVER_DIR|g" \
    -e "s|@USER@|$SERVICE_USER|g" \
    -e "s|@XMS@|$XMS|g" \
    -e "s|@XMX@|$XMX|g" \
    "$SCRIPT_DIR/pvpengine.service" > /etc/systemd/system/pvpengine.service
systemctl daemon-reload
systemctl enable pvpengine

# ---------------------------------------------------------------------------
log "8/8  firewall"
# ---------------------------------------------------------------------------
ufw allow 22/tcp     >/dev/null 2>&1 || true
ufw allow 25565/tcp  >/dev/null 2>&1 || true
ufw deny  3306/tcp   >/dev/null 2>&1 || true   # MySQL never leaves the box
ufw --force enable   >/dev/null 2>&1 || warn "could not enable ufw"

systemctl restart pvpengine
sleep 5

echo
log "done."
echo "  status :  systemctl status pvpengine"
echo "  logs   :  journalctl -u pvpengine -f"
echo "  console:  screen -r  (not used) — send commands with: systemd-run --pipe ... , or enable rcon"
echo
echo "First time only — build the map, then restart:"
echo "  sudo -u $SERVICE_USER tmux ...   (or temporarily enable rcon)"
echo "  in game, as an operator:  /pvpadmin setup 4"
echo
warn "Give yourself operator rights: add your Mojang UUID to $SERVER_DIR/ops.json,"
warn "or run once with the console attached and type: op <YourName>"
