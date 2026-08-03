#!/bin/sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/blueplay-collision.XXXXXX")
trap 'rm -rf "$test_dir"' EXIT
cd "$repo_dir"

kotlinc \
    Actor.kt \
    World.kt \
    Image.kt \
    BluePlayFunctions.kt \
    -d "$test_dir/framework.jar"

kotlinc \
    -classpath "$test_dir/framework.jar" \
    tests/CollisionTest.kt \
    -d "$test_dir/tests.jar"

kotlin -J-Djava.awt.headless=true \
    -classpath "$test_dir/framework.jar:$test_dir/tests.jar" \
    CollisionTestKt "$repo_dir"
