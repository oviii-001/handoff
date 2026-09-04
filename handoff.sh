#!/usr/bin/env bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BIN_PATH="$DIR/desktopApp/build/install/desktopApp/bin/desktopApp"

if [ ! -f "$BIN_PATH" ]; then
    echo "[Handoff] Building desktop CLI..."
    "$DIR/gradlew" :desktopApp:installDist -q
fi

"$BIN_PATH" "$@"
