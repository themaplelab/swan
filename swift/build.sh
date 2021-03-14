#!/bin/bash

echo "Running Swift build scripts"

set -e

scriptdir=`dirname "$BASH_SOURCE"`

if [ "${scriptdir}" != "./swift" ]; then
  echo "Please run the build script from repo root."
  exit 1
fi

"${scriptdir}"/swan-xcodebuild/build.sh

# "${scriptdir}"/swan-swiftc/build.sh
