#!/bin/bash

echo "Running master build script"

set -e

./jvm/build.sh

./swift/build.sh

# ./dev/build.sh