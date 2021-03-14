#!/bin/bash

echo "Running swan-xcodebuild build script"

set -e

scriptdir=`dirname "$BASH_SOURCE"`

if [ "${scriptdir}" != "./swift/swan-xcodebuild" ]; then
  echo "Please run the build script from repo root."
  exit 1
fi

if [ ! -d "lib" ]; then
  mkdir lib
fi

cd "${scriptdir}" && swift build -c release && cd ../..

cp "${scriptdir}"/.build/release/swan-xcodebuild lib/swan-xcodebuild
