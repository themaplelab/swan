#!/bin/bash

echo "Running master build script"

set -e

rm -rf lib
mkdir lib

./jvm/build.sh

./swift/build.sh

# ./dev/build.sh