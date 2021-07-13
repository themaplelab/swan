#!/usr/bin/env bash

# Test the case where the test has both taint and typestate specifications.

source ../../tester.bash
TAINT_SPEC=DEFAULT
TYPESTATE_SPEC_ROOT=examples/typestate-json-only.json
test_directory
