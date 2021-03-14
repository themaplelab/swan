#!/bin/bash

echo "Running JVM build script"

set -e

scriptdir=`dirname "$BASH_SOURCE"`

if [ "${scriptdir}" != "./jvm" ]; then
  echo "Please run the build script from repo root."
  exit 1
fi

cd "${scriptdir}" && ./gradlew shadowJar && cd ..

if [ ! -d "lib" ]; then
  mkdir lib
fi

cp "${scriptdir}"/ca.ualberta.maple.swan.viewer/build/libs/ca.ualberta.maple.swan.viewer-all.jar lib/viewer.jar

cp lib/viewer.jar dev/SwanViewer/viewer.jar

cp "${scriptdir}"/ca.ualberta.maple.swan.client/build/libs/ca.ualberta.maple.swan.client-all.jar lib/client.jar
