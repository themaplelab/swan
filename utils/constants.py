"""
//===---------------------------------------------------------------------===//
//
// This source file is part of the SWAN open source project
//
// Copyright (c) 2019 Maple @ University of Alberta
// All rights reserved. This program and the accompanying materials (unless
// otherwise specified by a license inside of the accompanying material)
// are made available under the terms of the Eclipse Public License v2.0
// which accompanies this distribution, and is available at
// http://www.eclipse.org/legal/epl-v20.html
//
//===---------------------------------------------------------------------===//
"""

import os
import sys
from sys import platform as _platform

# -----------------------------------------------------------------------------
# Constants

# Repos

SWIFT_REPO_URL = "https://github.com/apple/swift"
WALA_REPO_URL = "https://github.com/wala/WALA"
SWAN_VSCODE_REPO_URL = "https://github.com/themaplelab/swan-vscode"

# Paths

ROOT = os.getcwd()
PACKAGES_DIR = ROOT + "/packages/"
SWIFT_UTILS_DIR = PACKAGES_DIR + "swift/utils/"
CPP_TRANSLATOR_DIR = "ca.maple.swan.translator/"

ROOT_BUILD = PACKAGES_DIR + "build/"
BUILD_DIR = ROOT_BUILD + "Ninja-DebugAssert/"
SYSTEM = "macosx-x86_64" if (_platform == "darwin") else "linux-x86_64"
LLVM_BUILD_DIR = BUILD_DIR + "llvm-" + SYSTEM
CMARK_BUILD_DIR = BUILD_DIR + "cmark-" + SYSTEM
SWIFT_BUILD_DIR = BUILD_DIR + "swift-" + SYSTEM
WALA_DIR = PACKAGES_DIR + "WALA/"
SWAN_VSCODE_DIR = PACKAGES_DIR + "swan-vscode/swan/"

# IMPORTANT TO MAINTAIN

SUPPORTED_SWIFT_TAG = "swift-DEVELOPMENT-SNAPSHOT-2020-01-24-a"
SUPPORTED_WALA_TAG = "v1.5.4"

# -----------------------------------------------------------------------------

def check_dir():
    if not os.path.exists(ROOT + "/utils/build-swan"):
        spaced_print("ERROR: Script must be run from SWAN root directory!")
        sys.exit(1)

def spaced_print(s):
    print(os.linesep + s + os.linesep)