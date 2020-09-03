
<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# SWAN
A static program analysis framework for analyzing Swift (incl. iOS/macOS) applications using [WALA](https://github.com/wala/WALA) as the analysis core. SWAN is being developed for detecting security vulnerabilities in Swift applications using taint analysis.

:page_facing_up: [SWAN: A Static Analysis Framework for Swift](https://karimali.ca/resources/papers/swan.pdf)

:tv: [Video demonstration](https://www.youtube.com/watch?v=AZwfhOGqwFs)

![](https://github.com/themaplelab/swan/blob/master/docs/readmeContent/exampleUse.gif)

Note: This GIF shows an Xcode project being analyzed. We can no longer analyze Xcode projects due to nuances with building the Swift compiler. See "Important Notes" section for more details.

-----------

## Using SWAN

This section will go over how to use a SWAN release distribution.

### Dependencies

- macOS (tested on Mojave 10.14.6)
- Xcode 11.2 - Make sure your Command Line Tools are set. Go to File->Preferences->Locations and set _Command Line Tools_ to 11.2
- [Java 8](https://github.com/themaplelab/swan/wiki/Switching-to-Java-8)

### Setup

First, download the latest release from [here](https://github.com/themaplelab/swan/releases). Please read the description of the release to understand what you should expect from it.

```
$ tar -xvf swan-release
$ npm install -p swan-release/vscode
```
Add `PATH_TO_SWAN` environment variable to `~./bash_profile` and set it to point to `swan-release/`.

Restart your computer if you would like to use the VSCode extension. (Sorry, this is necessary for VSCode to use the environment variable.)

### Usage

SWAN has two frontends: a CLI script and a VSCode extension. The preferred way to run SWAN is using the VSCode extension.

**CLI Script**

The CLI script can be called in the following way.
```
$ ./swan-release/cli/run-swan-single -sdk <full_sdk_path> -path <full_path_to_swift_file>
```
We suggest adding the SDK path to an environment variable. The SDK can be found here.

```
/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/
```

There are example test files located in `./swan-release/test-files/`. SWAN will automatically use sources and sinks defined in the file. This is the only way to define sources and sinks using the CLI frontend. e.g.
```
//SWAN:sources: "StringConcat.source() -> Swift.String"
//SWAN:sinks: "StringConcat.sink(sunk: Swift.String) -> ()"
```

After running the script, you should see the analysis results printed to the terminal. e.g.
```
========= RESULTS =========

-- PATH
   -- SOURCE
      12:15 in /Users/tiganov/Documents/swan-release/test-files/Simple.swift
      let sourced = source();
                    ^
   -- SINK
      13:1 in /Users/tiganov/Documents/swan-release/test-files/Simple.swift
      sink(sunk: sourced);
      ^

====== END OF RESULTS ======
```

**VSCode Extension**

A video explaining the frontends is available [here](). It is probably better to watch the video on how to use the VSCode extension than to read the instructions below.

To use the VSCode extension, open up `./swan-release/vscode/` in VSCode and press `F5`. The extension is not published yet so it must be run manually. A new VSCode window should appear. First, you must set up SWAN by navigating to Settings->SWAN. These options have descriptions and, therefore, will not be described here.

Next, navigate be the tab on the left sidebar of VSCode window with the SWAN logo. The tree view on the left hand side will have a triple-dot icon menu in the top-right corner. That menu contains the commands needed to run SWAN.

The extension will start a JVM instance (SWAN analysis server) if it doesn't find one that is already running. Alternatively, you can first run the SWAN analysis server by running 
```
./swan-release/bin/swan-server
```

This is mostly useful for debugging. The original VSCode window's debug view will also contain information that may be useful for debugging, such as errors.

-----------

## Developing SWAN

### Important Notes

- We used to build the Swift compiler in order to link against it with our C++ code. However, Apple keeps changing XCode versions and OS requirements, and, therefore, building the Swift compiler is incredibly unstable. We can no longer build the latest Swift since Catalina is required by Xcode 11.4, and switching to Catalina, at least for now, isn't realistic for us nor many other developers. The last tag we were able to build was [swift-DEVELOPMENT-SNAPSHOT-2019-09-15-a](https://github.com/apple/swift/releases/tag/swift-DEVELOPMENT-SNAPSHOT-2019-09-15-a). We now release our C++ translator, which was compiled against that tag's build, as `./lib/libswiftWala.dylib`. This means that changes cannot be made to the C++ translator. We still have access to a Swift build if we do need to recompile the C++ translator. This is unlikely to be needed.

- Using the aforementioned tag, we were able to compile and analyze Xcode apps using Xcode 11.2 beta 2 and macOS Mojave 10.14.4. We can no longer do this due to `xcodebuild` errors on macOS Mojave 10.14.6, even with the same build binaries and Xcode version.

- We are not sure why, but now compiling a single Swift application that does not use any libraries requires giving the compiler the `-sdk` flag. This can perhaps be worked around by transfering an SDK to the Linux machine. Either way, we can no longer support Linux because building old tags of Swift on Linux is also unstable, and we don't have a build available for us to generate a `libswiftWala.so` from.

- We are working on developing a new way of grabbing SIL by dumping it to plain text and parsing it, as opposed to our current solution of intercepting SIL during compiler runtime and then sending the SIL as jobjects to the JVM. This is WIP and has not been publicly released yet.

- You may see references to "SPDS". This is because we are working towards implementing an alternative engine to WALA, and therefore we have adjusted our infrastructure to eventually support both.

### Building SWAN

**See the dependencies under "Using SWAN".**

```
git clone https://github.com/themaplelab/swan
cd swan/
./utils/build/update-packages
./utils/build/build-packages
./utils/build-swan
```
If you want to rebuild swan, running `./gradlew assemble` is sufficient. Running `./utils/build-swan` is only necessary for first-time compilation because it sets the `gradle.properties` file.

:warning: Set `PATH_TO_SWAN` environment variable to point to the `swan/` directory. You will have to add this env var to `~/.bash_profile` and restart your computer if you would like to use the VSCode extension to run SWAN.

**Old scripts**

There are alternative build scripts located under `./utils/build/with-swift`. These scripts were used when we compiled the Swift compiler, but should not be used now.

**Build a release**

To build a release
1. Navigate to `./packages/swan-vscode/`.
2. Checkout the `release` branch.
3. Navigate back to SWAN root.
4. Use the following script.
```
./utils/build/build-release
```
This script will create a `swan-release/` directory and a `swan-release.tar` in `swan/` (root). You can also give the script the `--make-ready` argument if you wish to you use the VSCode extension located under `swan-release/vscode/`. This is mostly useful if you are actively testing the release.

The reason the release uses a different VSCode extension branch is because the server instantiation is different (it does not use `gradlew`).

### Running SWAN for Development

#### VSCode Extension

SWAN uses its own [VSCode extension](https://github.com/themaplelab/swan-vscode) as the main driver. The extension has not been published yet, so you will need to run it yourself. See the instructions under "Using SWAN" section for how to use the extension.

:mag_right: The extension is located in `packages/swan-vscode/`.

The extension can launch the JVM itself. Alternatively, you can first start the JVM via the Intellij run configuration called _Server_ before using the extension. You can also start it by running `./gradlew assemble`. This is useful for debugging as you will be able to see information such as errors from the Swift compiler, various IRs, and the analysis stage.

#### Scripts

Runs SWAN on a given file and prints the analysis results (paths).
```
./utils/dev/run-swan-single -sdk <path_to_macosx_sdk> -path <path_to_test_file>
```

Runs SWAN on a given file and verifies its annotations are correct.
```
./utils/dev/run-swan-single-annotation -sdk <path_to_macosx_sdk> -path <path_to_test_file>
```

Tests all files under the given dir and verifies their annotations are correct. Test files can be found under `ca.maple.swan.swift.test/tests/`.
```
./utils/dev/run-tests -sdk <path_to_macosx_sdk> -dir <path_to_swift_files>
```
--------------------

:construction: **You should expect errors as SWAN is WIP and not fully functional.**
