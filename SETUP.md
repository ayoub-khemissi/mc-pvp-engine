# Setup

No Docker. MySQL runs natively — on your Windows machine for development, and on the
OVH server in production. The same jar works in both places; only `config.yml` changes.

---

## 1. Build

Needs **JDK 25** (Minecraft 26.x runs on Java 25).

```powershell
.\gradlew build
```

- Plugin jar → `engine-core/build/libs/engine-core-0.1.0.jar`
- Run the tests only: `.\gradlew test`

The tests use an **in-memory H2 database**, so `gradlew test` needs **no MySQL and no
Docker**. You can run it anywhere, any time.

---

## 2. MySQL

### Windows (local dev)

1. Install **MySQL Community Server** (or MariaDB) — pick "Developer default", set a root password.
2. Open *MySQL Command Line Client* (or `mysql -u root -p`) and run:

```sql
CREATE DATABASE pvp_engine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pvp'@'localhost' IDENTIFIED BY 'a-strong-password';
GRANT ALL PRIVILEGES ON pvp_engine.* TO 'pvp'@'localhost';
FLUSH PRIVILEGES;
```

### OVH (production, Debian/Ubuntu)

```bash
sudo apt update && sudo apt install -y mysql-server
sudo mysql_secure_installation

sudo mysql -u root -p
```
```sql
CREATE DATABASE pvp_engine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pvp'@'localhost' IDENTIFIED BY 'a-strong-password';
GRANT ALL PRIVILEGES ON pvp_engine.* TO 'pvp'@'localhost';
FLUSH PRIVILEGES;
```

> Keep MySQL bound to `localhost` (the default) and let the Minecraft server talk to it
> locally. Do **not** expose port 3306 to the internet.

**You never create the tables by hand** — the plugin runs its migrations at startup
(`schema_migrations` tracks what has been applied).

---

## 3. Paper server

1. Download **Paper 26.1.2** from <https://papermc.io/downloads/paper>.
2. Start it once to generate the files, accept the EULA (`eula.txt`).
3. Drop `engine-core-0.1.0.jar` into `plugins/`.
4. Start the server once → it creates `plugins/PvPEngine/config.yml`.
5. Fill in the database section:

```yaml
database:
  host: localhost
  port: 3306
  name: pvp_engine
  user: pvp
  password: a-strong-password
  pool-size: 10
```

6. Restart. You should see:

```
[PvPEngine] PvP Engine enabled (database ready).
```

If the database is unreachable the plugin **disables itself** and tells you why, instead of
running half-broken.

> HikariCP and the MySQL driver are **not** bundled in the jar — Paper downloads them at
> startup (the `libraries:` section of `plugin.yml`). The server needs internet access the
> first time.

---

## 4. Day-to-day

```powershell
.\gradlew test     # fast — pure logic + SQL against H2. Run this constantly.
.\gradlew build    # tests + plugin jar
```

**The rule:** `gradlew test` must be green before anything is deployed.
