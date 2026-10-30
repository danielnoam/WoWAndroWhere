#!/data/data/com.termux/files/usr/bin/bash
# Runs inside termux/termux-docker:aarch64 after build.sh, called by solo-server.yml. Starts the bundled server
# the way the app does (SoloServer.kt, SoloConfig.kt, SoloSetup.kt) on a copy of the built database, so a build
# that can't start never gets published. Keep the flags, config keys and SQL here in step with those files.
# It runs on a Linux kernel, not Android, so Android-only behaviour (linker64, netd's resolver) isn't covered.
# In: /work/out (from build.sh), /work/data (client-data dbc, maps, vmaps, Cameras).
set -euxo pipefail

W=/work
S=$W/out/server/usr
T=$W/smoke
rm -rf $T
mkdir -p $T/run/tmp $T/logs $T/etc
cp -a $W/out/db $T/db
SOCK=$T/run/mysql.sock

# Only the bundled libraries, as on the phone.
export LD_LIBRARY_PATH=$S/lib
export OPENSSL_MODULES=$S/lib/ossl-modules
export HOME=$T TMPDIR=$T/run/tmp

pids=()
cleanup() { for p in "${pids[@]}"; do kill -9 "$p" 2>/dev/null || true; done; }
trap cleanup EXIT

# Waits up to $3 seconds for log $2 to contain $4 while process $1 is alive.
wait_for() {
    local pid=$1 log=$2 secs=$3 text=$4
    for _ in $(seq "$secs"); do
        grep -q -- "$text" "$log" && return 0
        if ! kill -0 "$pid" 2>/dev/null; then tail -50 "$log"; echo "::error::$(basename "$log" .log) stopped before printing '$text'"; return 1; fi
        sleep 1
    done
    tail -50 "$log"; echo "::error::$(basename "$log" .log) didn't print '$text' in ${secs}s"; return 1
}

# Sends SIGTERM like SoloServer.stopProcess and fails if the process outlives $2 seconds.
stop() {
    local pid=$1 secs=$2 name=$3
    kill -TERM "$pid"
    for _ in $(seq "$secs"); do kill -0 "$pid" 2>/dev/null || return 0; sleep 1; done
    echo "::error::$name didn't stop within ${secs}s of SIGTERM"; return 1
}

# OpenSSL's legacy provider must load from the bundle: the world server's packet encryption (RC4) needs it.
# build.sh compiled the check, since this container has no compiler.
$W/rc4check

# SoloServer.startDatabase
SHARE=$S/share/mariadb
$S/bin/mariadbd --no-defaults --basedir=$S --datadir=$T/db --socket=$SOCK --skip-networking \
    --tmpdir=$T/run/tmp --pid-file=$T/run/mariadbd.pid --lc-messages-dir=$SHARE --character-sets-dir=$SHARE/charsets \
    --innodb-use-native-aio=0 --innodb-buffer-pool-size=256M --innodb-log-file-size=512M \
    --innodb-read-io-threads=2 --innodb-write-io-threads=2 \
    --performance-schema=OFF --skip-log-bin --max-connections=40 > $T/logs/mariadbd.log 2>&1 &
DB=$!; pids+=($DB)
wait_for $DB $T/logs/mariadbd.log 180 "ready for connections"

# SoloSetup.accountSql and gmSql, run through SoloServer.sql's command line. The verifier is a dummy: login isn't tested.
sql() { $S/bin/mariadb --socket=$SOCK -uacore -pacore --database=acore_auth --batch -e "$1"; }
sql "INSERT INTO acore_auth.account (username, salt, verifier, expansion) VALUES ('SOLO', X'$(printf '%064d' 0)', X'$(printf '%064d' 0)', 2) ON DUPLICATE KEY UPDATE salt = VALUES(salt), verifier = VALUES(verifier); DELETE FROM acore_auth.account_access WHERE id IN (SELECT id FROM acore_auth.account WHERE username = 'SOLO'); INSERT INTO acore_auth.account_access (id, gmlevel, RealmID) SELECT id, 3, -1 FROM acore_auth.account WHERE username = 'SOLO';"
sql "SELECT a.username, x.gmlevel FROM acore_auth.account a JOIN acore_auth.account_access x ON x.id = a.id" | grep -q "SOLO"

# SoloConfig.write
conf() {
    local dist=$1 out=$2; shift 2
    cp "$dist" "$out"
    while (($#)); do
        grep -q "^$1 *=" "$out" || { echo "::error::$1 missing from $(basename "$dist")"; exit 1; }
        sed -i "s|^$1 *=.*|$1 = $2|" "$out"
        shift 2
    done
}
db() { echo "\".;$SOCK;acore;acore;$1\""; }
COMMON=(LogsDir "\"$T/logs\"" BindIP '"127.0.0.1"' PidFile '""' LoginDatabaseInfo "$(db acore_auth)"
    Updates.EnableDatabases 0 Updates.AutoSetup 0 MySQLExecutable '""' TempDir "\"$T/run/tmp\"")
conf $S/etc/authserver.conf.dist $T/etc/authserver.conf "${COMMON[@]}"
conf $S/etc/worldserver.conf.dist $T/etc/worldserver.conf "${COMMON[@]}" \
    DataDir "\"$W/data\"" WorldDatabaseInfo "$(db acore_world)" CharacterDatabaseInfo "$(db acore_characters)" \
    Console.Enable 0 Ra.Enable 0 SOAP.Enabled 0 Metric.Enable 0 Warden.Enabled 0 ThreadPool 2 MapUpdate.Threads 1 \
    MoveMaps.Enable 0

cd $T
$S/bin/authserver -c $T/etc/authserver.conf > $T/logs/authserver.log 2>&1 </dev/null &
AUTH=$!; pids+=($AUTH)
sleep 5
kill -0 $AUTH || { cat $T/logs/authserver.log; echo "::error::authserver stopped right away"; exit 1; }
(exec 3<>/dev/tcp/127.0.0.1/3724) || { cat $T/logs/authserver.log; echo "::error::authserver isn't listening on 3724"; exit 1; }

$S/bin/worldserver -c $T/etc/worldserver.conf > $T/logs/worldserver.log 2>&1 </dev/null &
WORLD=$!; pids+=($WORLD)
wait_for $WORLD $T/logs/worldserver.log 900 "ready..."
(exec 3<>/dev/tcp/127.0.0.1/8085) || { tail -50 $T/logs/worldserver.log; echo "::error::worldserver isn't listening on 8085"; exit 1; }

# SoloServer.stopAll
stop $WORLD 60 worldserver
stop $AUTH 10 authserver
stop $DB 60 mariadbd
pids=()
echo "Smoke test passed"
rm -rf $T
