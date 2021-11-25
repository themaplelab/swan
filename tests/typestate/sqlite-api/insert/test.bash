#!/usr/bin/env bash

# This tests a realistic database example.
# The typestate counts the number of method calls (limited to 10 calls).

source ../../../tester.bash
TYPESTATE_SPEC_ROOT=sqlite-api/Insert.json
MACOS_ONLY=1
test_directory
