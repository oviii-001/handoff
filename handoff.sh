#!/usr/bin/env bash
export LC_ALL=en_US.UTF-8
export JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 $JAVA_OPTS"

DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_PATH="$DIR/cli/build/install/cli/bin/cli"

if [ ! -f "$BIN_PATH" ]; then
    echo "Building HandOff CLI..." >&2
    "$DIR/gradlew" :cli:installDist -q
fi

"$BIN_PATH" "$@"
