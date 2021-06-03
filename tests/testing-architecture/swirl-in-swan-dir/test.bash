#!/usr/bin/env bash

# Test combing test.swift with test.swirl
# test.swirl will overwrite a function in test.swift
# and therefore no path should be detected

source ../../tester.bash
test_directory
