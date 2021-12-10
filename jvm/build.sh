#!/bin/bash

echo "Running JVM build script"

set -e

scriptdir=`dirname "$BASH_SOURCE"`

if [ "${scriptdir}" != "./jvm" ]; then
  echo "Please run the build script from repo root."
  exit 1
fi

cd "${scriptdir}" && ./gradlew shadowJar && cd ..

cp "${scriptdir}"/ca.ualberta.maple.swan.viewer/build/libs/ca.ualberta.maple.swan.viewer-all.jar lib/viewer.jar

cp lib/viewer.jar dev/SwanViewer/viewer.jar

cp "${scriptdir}"/ca.ualberta.maple.swan.drivers/build/libs/ca.ualberta.maple.swan.drivers-all.jar lib/driver.jar

cp "${scriptdir}"/ca.ualberta.maple.swan.test/build/libs/ca.ualberta.maple.swan.test-all.jar lib/annotation.jar

cp "${scriptdir}"/ca.ualberta.maple.swan.spds/build/libs/ca.ualberta.maple.swan.spds.jar lib/probe.jar