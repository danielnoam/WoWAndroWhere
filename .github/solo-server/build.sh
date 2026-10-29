#!/data/data/com.termux/files/usr/bin/bash
# Runs inside termux/termux-docker:aarch64 (Android's libc, Termux packages), called by solo-server.yml.
# Builds AzerothCore's authserver and worldserver, bundles them with Termux's MariaDB and every library
# they load from $PREFIX/lib, then imports the world database once so the phone gets a ready data directory.
# In: /work/ac (AzerothCore source). Out: /work/out/server (usr tree) and /work/out/db (MariaDB data directory).
set -euxo pipefail

W=/work
OUT=$W/out
JOBS=$(nproc)

apt-get update
apt-get -y -o Dpkg::Options::=--force-confnew dist-upgrade
apt-get -y -o Dpkg::Options::=--force-confnew install \
    git cmake ninja clang binutils boost boost-headers openssl mariadb readline zlib

MARIADB_VERSION=$(mariadbd --version | sed -n 's/.*Ver \([0-9.]*\)-MariaDB.*/\1/p')
echo "$MARIADB_VERSION" > $W/mariadb-version

cmake -S $W/ac -B $W/build -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX=$W/inst \
    -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
    -DAPPS_BUILD=all -DTOOLS_BUILD=db-only \
    -DSCRIPTS=static -DMODULES=none \
    -DWITH_WARNINGS=0 -DWITH_COREDEBUG=0 -DBUILD_TESTING=0
ninja -C $W/build -j"$JOBS" install

# --- server tree ---
S=$OUT/server/usr
mkdir -p $S/bin $S/lib $S/etc $S/share
cp $W/inst/bin/authserver $W/inst/bin/worldserver $S/bin/
cp "$(command -v mariadbd)" "$(command -v mariadb)" $S/bin/
cp $W/inst/etc/authserver.conf.dist $W/inst/etc/worldserver.conf.dist $S/etc/
cp -r "$PREFIX/share/mariadb" $S/share/mariadb

# Every shared library the binaries need that comes from Termux (not Android's own /system libs).
queue=("$S"/bin/*)
declare -A seen
while ((${#queue[@]})); do
    f=${queue[0]}; queue=("${queue[@]:1}")
    for lib in $(readelf -d "$f" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p'); do
        [[ -n ${seen[$lib]:-} ]] && continue
        seen[$lib]=1
        if [[ -e $PREFIX/lib/$lib ]]; then
            cp -L "$PREFIX/lib/$lib" "$S/lib/$lib"
            queue+=("$S/lib/$lib")
        fi
    done
done
llvm-strip --strip-unneeded $S/bin/* $S/lib/*.so* || true
ls -la $S/bin $S/lib

# --- world database ---
D=$OUT/db
SOCK=$W/mysql.sock
mariadb-install-db --no-defaults --basedir=$PREFIX --datadir=$D --auth-root-authentication-method=normal --skip-test-db
mariadbd --no-defaults --datadir=$D --socket=$SOCK --bind-address=127.0.0.1 --port=3306 \
    --innodb-use-native-aio=0 --innodb-buffer-pool-size=2G --innodb-log-file-size=512M \
    --performance-schema=OFF --skip-log-bin &
DBPID=$!
for _ in $(seq 120); do mariadb --socket=$SOCK -uroot -e 'SELECT 1' >/dev/null 2>&1 && break; sleep 1; done

mariadb --socket=$SOCK -uroot <<'SQL'
CREATE USER 'acore'@'localhost' IDENTIFIED BY 'acore';
CREATE USER 'acore'@'127.0.0.1' IDENTIFIED BY 'acore';
GRANT ALL PRIVILEGES ON *.* TO 'acore'@'localhost' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'acore'@'127.0.0.1' WITH GRANT OPTION;
DELETE FROM mysql.global_priv WHERE User = '';
FLUSH PRIVILEGES;
SQL

sed -e "s|^SourceDirectory *=.*|SourceDirectory = \"$W/ac\"|" \
    -e "s|^MySQLExecutable *=.*|MySQLExecutable = \"$(command -v mariadb)\"|" \
    -e 's|^Updates.AutoSetup *=.*|Updates.AutoSetup = 1|' \
    -e 's|^Updates.EnableDatabases *=.*|Updates.EnableDatabases = 7|' \
    $W/inst/etc/dbimport.conf.dist > $W/dbimport.conf
$W/inst/bin/dbimport -c $W/dbimport.conf

mariadb --socket=$SOCK -uroot <<'SQL'
UPDATE acore_auth.realmlist SET name = 'Solo', address = '127.0.0.1', localAddress = '127.0.0.1' WHERE id = 1;
SELECT COUNT(*) AS creatures FROM acore_world.creature_template;
SET GLOBAL innodb_fast_shutdown = 0;
SHUTDOWN;
SQL
wait $DBPID || true
rm -f $D/*.err $D/*.pid $D/aria_log_control.lock
du -sh $OUT/server $D
