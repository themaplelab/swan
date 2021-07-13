#!/usr/bin/env bash

# This tests specifying which arguments of a sink
# are sensitive to tainted data.

source ../../tester.bash
TAINT_SPEC=custom-spec.json
test_directory
