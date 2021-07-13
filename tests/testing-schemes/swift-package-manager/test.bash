#!/usr/bin/env bash

# Test building a Swift Packager Manager application.
# TODO: Test dataflow through the package

source ../../tester.bash
TAINT_SPEC=DEFAULT
import "ColorizeSwift.sil"
test_directory
