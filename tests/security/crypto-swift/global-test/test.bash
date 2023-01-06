#!/usr/bin/env bash

# This tests the crypto analysis global handling

source ../../../tester.bash
IS_CRYPTO=1
DRIVER_OPTIONS="--cg-algo UCG_NO_VTA"
test_directory
