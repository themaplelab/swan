
<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# SWAN
A static program analysis framework for analyzing Swift applications using [WALA](https://github.com/wala/WALA) as the analysis core. 

## Introduction

This static program analysis framework is being developed for detecting security vulnerabilities in Swift applications using taint analysis. A custom translator converts Swift Intermediate Language ([SIL](https://github.com/apple/swift/blob/master/docs/SIL.rst)) to WALA IR (called CAst). The SIL is retrieved by hooking into the Swift compiler and grabbing the SIL modules during compilation. The resulting CAst nodes are analyzed using custom analysis written on top of WALA (specifically, the JS analysis).

The translator supports common SIL instructions, and we recently added general support for Swift v5.

## Current work
We are currently working on the following:
- Fixing translation from SIL to CAst issues
- Taint Analysis

## Future plans
- Lifecycle awareness for iOS and macOS applications (custom call graph building)
- Sources and sinks for iOS and macOS libraries
- Xcode plugin
- Full or more robust SIL instruction support (e.g. more accurate translation)

## Getting Started

First, you should consider that the final build may be as large as ~70GB. Also, please read Swift's [README](https://github.com/apple/swift/blob/master/README.md) for the latest dependencies (e.g. Xcode beta version).

### Release support
Supported Swift (incl. dependencies) and WALA releases on SWAN's `master` branch. 

| OS | Swift Release Tag | WALA Release Tag* | 
| -----------|:-------:|:-----:|
| macOS Mojave | [master](https://github.com/apple/swift/tree/master) | [v1.5.3](https://github.com/wala/WALA/releases/tag/v1.5.3) |
| Linux (Ubuntu 18.04) | [master](https://github.com/apple/swift/tree/master) | [v1.5.3](https://github.com/wala/WALA/releases/tag/v1.5.3) |

**\*You must be using Java 8 in order to compile WALA. If you need help switching to Java 8, please see our [guide](https://github.com/themaplelab/swan/wiki/Switching-to-Java-8) on it.**

### Download Projects

We use the latest Swift compiler and WALA.
```
mkdir swift-source
cd swift-source
git clone https://github.com/apple/swift
git clone https://github.com/wala/WALA
git clone https://github.com/themaplelab/swan
```
`master` branch may not always be the up-to-date branch. In this case, use the `-b` flag when cloning `swan` to select the appropriate branch.

### Build Dependencies
See compatibility table above for supported release tags, according to your OS. Please open up an issue if you are experiencing build issues or difficulties.

#### WALA

```
cd ./WALA
git checkout SUPPORTED_TAG
./gradlew assemble
cd ..
```

#### Swift

```
cd ./swift
./utils/update-checkout --clone --tag SUPPORTED_TAG
./utils/build-script 
cd ..
```

#### Edit SWAN Configurations

```
cd ./swan
cp gradle.properties.example gradle.properties
```

Edit `gradle.properties` and provide proper paths. Some example paths are already provided to give you an idea of what they might look like for you. For macOS, change the `linux` to `macosx` in the paths. (e.g `swift-linux-x86_64` to `swift-macosx-x86_64`)


#### Build SWAN

```
cd ./swan
./gradlew assemble
```

### Running SWAN

First you need to set some environment variables. You can also add the following to your `~/.bashrc` or `~/.bash_profile`, but make sure to source after. **Restart IDEA if you have it open.** The first variable is the same as in `gradle.properties`. The second variable is just the path to the directory containing this cloned repository.

```
export WALA_PATH_TO_SWIFT_BUILD={path/to/your/swift/build/dir}
export PATH_TO_SWAN={path/to/swan/dir}
```

The current analysis driver is just a call graph constructor. Once sinks and sources are identified, it will also be able to do taint analysis.

**IDEA**

There are some run configs ready to use.
 
**From the terminal**

You must run the analysis by running the following in the **root** directory. However, the test files must lie inside of `ca.maple.swan.swift.test/`. You may use the files under `testFiles/` there.
```
./gradlew run --args="YOUR_SWIFT_FILE"
```

## Contributing
Please see the [page](https://github.com/themaplelab/swan/wiki/Contributing) on contributing.

--------------------

**You should expect exceptions on this branch as it is WIP and not fully functional.**
