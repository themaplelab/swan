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
SWIFT_DIR= PACKAGES_DIR + "swift/"
SWIFT_UTILS_DIR = SWIFT_DIR + "utils/"
TEST_DIR = ROOT + "/ca.maple.swan.swift.test/"

ROOT_BUILD = PACKAGES_DIR + "build/"
BUILD_DIR = ROOT_BUILD + "Ninja-DebugAssert/"
SYSTEM = "macosx-x86_64" if (_platform == "darwin") else "linux-x86_64"
LLVM_BUILD_DIR = BUILD_DIR + "llvm-" + SYSTEM
CMARK_BUILD_DIR = BUILD_DIR + "cmark-" + SYSTEM
SWIFT_BUILD_DIR = BUILD_DIR + "swift-" + SYSTEM
WALA_DIR = PACKAGES_DIR + "WALA/"
VSCODE_DIR = PACKAGES_DIR + "swan-vscode/"
SWAN_VSCODE_DIR = VSCODE_DIR + "swan/"
TAGS_FILE = ROOT + "/tags"

# -----------------------------------------------------------------------------

def check_dir():
    if not os.path.exists(ROOT + "/utils/build-swan"):
        spaced_print("ERROR: Script must be run from SWAN root directory!")
        sys.exit(1)

def spaced_print(s):
    print(os.linesep + s + os.linesep)

def system(cmd):
    print(cmd)
    os.system(cmd)

def chdir(dir):
    print("cd " + dir)
    os.chdir(dir)

def prompt():
    # raw_input returns the empty string for "enter"
    yes = {'yes','y', 'ye', ''}
    no = {'no','n'}

    choice = input().lower()
    if choice in yes:
       return True
    elif choice in no:
       return False
    else:
       sys.stdout.write("Please respond with 'yes' or 'no'")