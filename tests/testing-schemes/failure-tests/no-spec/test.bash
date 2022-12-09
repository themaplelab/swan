#!/usr/bin/env bash

# This tests the failure when the test.bash does not provide a TAINT_SPEC
# or TYPESTATE_SPEC.

source ../../../tester.bash
EXPECTED_ERROR_MESSAGE="NO ANALYSIS SELECTED"
test_directory
