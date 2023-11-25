#!/bin/bash

echo "Running Swift build scripts"

set -e

scriptdir=`dirname "$BASH_SOURCE"`

if [ "${scriptdir}" != "./swift" ]; then
  echo "Please run the build script from repo root."
  exit 1
fi


cd "${scriptdir}" && swift build -c release && cd ..

cp "${scriptdir}"/.build/release/swan-xcodebuild lib/swan-xcodebuild
cp "${scriptdir}"/.build/release/swan-swiftc lib/swan-swiftc
cp "${scriptdir}"/.build/release/swan-xcodebuildlog2sil lib/swan-xcodebuildlog2sil
