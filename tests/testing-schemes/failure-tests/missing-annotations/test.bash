#!/usr/bin/env bash

# This tests the failure when the test.bash does not provide a TAINT_SPEC
# or TYPESTATE_SPEC.

source ../../../tester.bash
TAINT_SPEC=DEFAULT
EXPECTED_ERROR_MESSAGE="Missing source annotation"
test_directory
