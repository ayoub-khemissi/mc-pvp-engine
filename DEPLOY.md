# Deploy to Ubuntu (OVH)

Everything is scripted. If you are an AI agent working on the remote box, read
**`CLAUDE.md`** first — it explains the project; this file only explains the server.

---

## What the install does

`deploy/install.sh` is **idempotent** — run it as many times as you like.

1. Installs **Java 25** (Temurin). Minecraft 26.x does **not** run on Java 21.
2. Installs **MySQL**, keeps it bound to `127.0.0.1`, creates the database and a
   dedicated user with a **randomly generated password** (saved to `/root/.pvp-db-credentials`).
3. Downloads **Paper 26.1.2** (latest build, via the PaperMC v3 API).
4. Copies the plugins from `dist/`, writes `config.yml` with the DB password.
5. Installs a **systemd** service (auto-restart, Aikar GC flags, hardened).
6. **Firewall**: opens 22 + 25565, blocks 3306.

---

## Steps

```bash
# 1. get the code onto the server
git clone <your repo> /root/pvp-engine     # or scp the folder
cd /root/pvp-engine

# 2. build the plugins  (needs JDK 25 — install.sh installs it, so you can run
#    install.sh once first, or build locally and upload dist/)
chmod +x deploy/*.sh
./deploy/build.sh

# 3. install
sudo ./deploy/install.sh
```

Then:

```bash
systemctl status pvpengine
journalctl -u pvpengine -f
```

---

## First run: become an operator and build the map

The server has no map yet (the world is void).

1. **Op yourself.** Stop the server, add your Minecraft UUID to `ops.json`:

```bash
sudo systemctl stop pvpengine
# get your UUID: https://api.mojang.com/users/profiles/minecraft/<YourName>
sudo -u mcpvp tee /opt/pvp-server/ops.json > /dev/null <<'JSON'
[{"uuid":"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx","name":"YourName","level":4,"bypassesPlayerLimit":true}]
JSON
sudo systemctl start pvpengine
```

2. **Join the server**, then in chat:

```
/pvpadmin setup 4
```

That builds the lobby and 4 arenas (4 simultaneous matches) and writes the map files.
It is safe to re-run: it clears its volume first.

---

## Security — read this

| | |
|---|---|
| **`online-mode=true`** | **Enforced by the installer.** Local dev used `false` so one person could join twice. On a public server, `false` lets **anyone log in as any username — including yours, as an operator**. Never ship with it off. |
| **RCON is disabled** | It was only enabled locally so I could drive the console. If you turn it on, use a strong password and firewall the port — RCON is unencrypted. |
| **MySQL is localhost-only** | Bound to `127.0.0.1`, and 3306 is blocked by ufw. The DB password is random, stored in `/root/.pvp-db-credentials` (mode 600). |
| **Not running as root** | The server runs as the `mcpvp` system user. |
| **No anti-cheat** | The engine is not one. If the server goes public, add a real anti-cheat plugin — the server-side region check only guards the arena bounds. |

---

## Updating the plugins

```bash
cd /root/pvp-engine
git pull
./deploy/build.sh            # runs the tests; refuses to produce jars if they fail
sudo cp dist/*.jar /opt/pvp-server/plugins/
sudo systemctl restart pvpengine
```

The database migrates itself on start (`schema_migrations` tracks what has run).

---

## Backups

```bash
sudo cp deploy/backup.sh /opt/pvp-server/
sudo chmod +x /opt/pvp-server/backup.sh
sudo crontab -e
# 0 4 * * *  /opt/pvp-server/backup.sh >> /var/log/pvp-backup.log 2>&1
```

The **database is the thing that matters** — ratings and match history cannot be
regenerated. Worlds can (`/pvpadmin setup`), except designed maps.

---

## Sizing

- **2–4 GB heap** is plenty: the world is void, there are no mobs, no chunk generation.
- Arenas are pre-built slots in one world — no world copying, no disk I/O per match.
- `/pvpadmin setup N` → N simultaneous matches. Raise N before raising the RAM.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| Plugin disables itself at boot | Database unreachable. It says so explicitly. Check `journalctl -u pvpengine` and `/root/.pvp-db-credentials` vs `plugins/PvPEngine/config.yml`. |
| `UnsupportedClassVersionError` | Wrong Java. `java -version` must say 25. |
| Players bump into nothing | Stale `barrier` blocks from an older layout. Re-run `/pvpadmin setup N` — it clears its volume first. |
| No game modes in the Play menu | `ModeDuel.jar` missing, or it failed to load because `PvPEngine` did. |
