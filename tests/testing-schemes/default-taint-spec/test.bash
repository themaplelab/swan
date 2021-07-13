#!/usr/bin/env bash

# This is the most basic style of test.
# This tests providing the test with the default taint specification file.

source ../../tester.bash
TAINT_SPEC=DEFAULT
test_directory
