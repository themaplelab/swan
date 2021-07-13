#!/usr/bin/env bash

# This tests using regex to specify sources and sinks.
# Regex is currently not supported for sanitizers.

source ../../tester.bash
TAINT_SPEC=custom-spec.json
test_directory
