#!/usr/bin/env bash

# Test combining test.swift with test.swirl
# test.swirl will overwrite a function in test.swift
# and therefore no path should be detected

source ../../tester.bash
TAINT_SPEC=DEFAULT
test_directory
