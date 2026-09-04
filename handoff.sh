#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_PATH="$DIR/cli/build/install/cli/bin/cli"

if [ ! -f "$BIN_PATH" ]; then
    echo "Building HandOff CLI..."
    "$DIR/gradlew" :cli:installDist -q
fi

"$BIN_PATH" "$@"
