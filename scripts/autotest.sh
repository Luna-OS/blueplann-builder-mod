#!/usr/bin/env bash
# End-to-end test: starts a vanilla dedicated server, then a client (under Xvfb if no display is
# available) that runs dev.blueprintplanner.autotest.AutoTestDriver. Results and screenshots end up
# in build/run/autotest/.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
SERVER_DIR="$ROOT/build/autotest-server"
CLIENT_DIR="$ROOT/build/run/autotest"
SERVER_JAR="${SERVER_JAR:-$HOME/.gradle/caches/fabric-loom/26.3/minecraft-server.jar}"
JAVA="${JAVA_HOME:-/usr}/bin/java"

rm -rf "$SERVER_DIR" "$CLIENT_DIR/screenshots" "$CLIENT_DIR/autotest-result.txt" "$CLIENT_DIR/blueprintplanner" "$CLIENT_DIR/saves/sptest"
mkdir -p "$SERVER_DIR" "$CLIENT_DIR/saves"
echo "eula=true" > "$SERVER_DIR/eula.txt"
cat > "$SERVER_DIR/server.properties" <<PROPS
server-port=25599
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
difficulty=peaceful
generate-structures=false
view-distance=6
simulation-distance=6
enforce-secure-profile=false
PROPS
UUID=$(python3 -c "import hashlib,uuid;h=bytearray(hashlib.md5(b'OfflinePlayer:Tester').digest());h[6]=h[6]&0x0f|0x30;h[8]=h[8]&0x3f|0x80;print(uuid.UUID(bytes=bytes(h)))")
echo "[{\"uuid\":\"$UUID\",\"name\":\"Tester\",\"level\":4,\"bypassesPlayerLimit\":false}]" > "$SERVER_DIR/ops.json"

FIFO="$SERVER_DIR/console"
mkfifo "$FIFO"
# the server reads its console from the FIFO; fd 3 keeps a writer open so stdin never hits EOF
(cd "$SERVER_DIR" && exec "$JAVA" -Xmx2G -jar "$SERVER_JAR" --nogui < "$FIFO" > server.log 2>&1) &
SERVER_PID=$!
exec 3>"$FIFO"

stop_server() {
	if kill -0 "$SERVER_PID" 2>/dev/null; then
		echo "stop" >&3 2>/dev/null || true
		for i in $(seq 1 30); do
			kill -0 "$SERVER_PID" 2>/dev/null || break
			sleep 1
		done
		kill -9 "$SERVER_PID" 2>/dev/null || true
	fi
	exec 3>&- 2>/dev/null || true
}
trap stop_server EXIT
for i in $(seq 1 180); do
	grep -q 'Done (' "$SERVER_DIR/server.log" 2>/dev/null && break
	sleep 1
done
grep -q 'Done (' "$SERVER_DIR/server.log" || { echo "server did not start"; tail -30 "$SERVER_DIR/server.log"; exit 1; }
echo "save-all flush" >&3
sleep 3
cp -r "$SERVER_DIR/world" "$CLIENT_DIR/saves/sptest"
rm -f "$CLIENT_DIR/saves/sptest/session.lock"

# optional: other mods to test together with the planner (e.g. EXTRA_MODS=/path/a.jar:/path/b.jar)
rm -rf "$CLIENT_DIR/mods"
mkdir -p "$CLIENT_DIR/mods"
if [ -n "${EXTRA_MODS:-}" ]; then
	IFS=':' read -ra extra <<< "$EXTRA_MODS"
	for jar in "${extra[@]}"; do cp "$jar" "$CLIENT_DIR/mods/"; done
fi

# optional: a shader pack for Iris (e.g. SHADERPACK=/path/ComplementaryReimagined.zip, needs Iris in EXTRA_MODS)
rm -rf "$CLIENT_DIR/shaderpacks" "$CLIENT_DIR/config/iris.properties"
if [ -n "${SHADERPACK:-}" ]; then
	mkdir -p "$CLIENT_DIR/shaderpacks" "$CLIENT_DIR/config"
	cp "$SHADERPACK" "$CLIENT_DIR/shaderpacks/"
	printf 'enableShaders=true\nshaderPack=%s\n' "$(basename "$SHADERPACK")" > "$CLIENT_DIR/config/iris.properties"
fi

# skip first-start screens, keep running without focus
cat > "$CLIENT_DIR/options.txt" <<OPTS
onboardAccessibility:false
pauseOnLostFocus:false
joinedFirstServer:true
skipMultiplayerWarning:true
tutorialStep:none
renderDistance:8
OPTS

if [ -z "${DISPLAY:-}" ]; then
	export DISPLAY=:99
	pgrep -f "Xvfb :99" >/dev/null || (Xvfb :99 -screen 0 1280x720x24 +extension GLX +render -noreset > /dev/null 2>&1 &)
	export LIBGL_ALWAYS_SOFTWARE=1 BLUEPRINTPLANNER_HEADLESS=1
	export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp/xdg-${USER:-user}}"
	mkdir -p "$XDG_RUNTIME_DIR" && chmod 700 "$XDG_RUNTIME_DIR"
	sleep 1
fi

set +e
./gradlew runAutotest -PstrictCompat --no-configuration-cache > "$ROOT/build/autotest-client.log" 2>&1
set -e
stop_server

# a mixin of this mod that fails to apply is only logged by Mixin, so check the log explicitly
if grep -E "\(FabricLoader/Mixin\).*(from mod blueprintplanner|blueprintplanner[a-z.]*\.mixins\.json)" "$ROOT/build/autotest-client.log" | grep -qE "ERROR|Critical"; then
	echo "Blueprint Planner mixin errors:"
	grep -E "\(FabricLoader/Mixin\).*(from mod blueprintplanner|blueprintplanner[a-z.]*\.mixins\.json)" "$ROOT/build/autotest-client.log"
	exit 1
fi

if [ -f "$CLIENT_DIR/autotest-result.txt" ]; then
	cat "$CLIENT_DIR/autotest-result.txt"
	grep -q "AUTOTEST PASSED" "$CLIENT_DIR/autotest-result.txt"
else
	echo "no result file - see build/autotest-client.log"
	exit 1
fi
