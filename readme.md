
<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# SWAN (a.k.a Swift-WALA)
A static program analysis framework for analyzing Swift applications using [WALA](https://github.com/wala/WALA) as the analysis core. 


## Introduction

This static program analysis framework is being developed for detecting security vulnerabilities in Swift applications using taint analysis. A custom translator converts Swift Intermediate Language ([SIL](https://github.com/apple/swift/blob/master/docs/SIL.rst)) to WALA IR (called CAst). The SIL is retrieved by hooking into the Swift compiler and grabbing the SIL modules during compilation. The resulting CAst nodes are analyzed using custom analysis written on top of WALA.

The current translator only supports the most common SIL instructions, and we recently added general support for Swift v5, so better SIL instruction support is likely to come soon.

## Current work
The translator and basic toolchain/dataflow has been implemented. We are currently working on implementing the architecture for the analysis to be built on top of WALA. Then we will implement points-to analysis and taint analysis with basic sources and sinks identified.

## Future plans
- Lifecycle awareness for iOS and macOS applications (custom call graph building)
- Sources and sinks for iOS and macOS libraries
- Xcode plugin
- Better (maybe full) SIL instruction support for latest Swift version

## Getting Started

First, you should consider that the final build may be as large as ~52GB.

**Disclaimer:** SWAN doesn't target a specific Swift or WALA source code version. The source code pulled from the Apple and IBM are not versioned, nor do they have stable branches. Therefore, the build is pretty volatile as changes made by Apple and IBM to their source code can break the build for SWAN. Changes in the Swift compiler are often problematic for SWAN. We try our best to make sure the build works with the most up to date dependency source code. Please open up an issue if it is breaking for you.

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

#### WALA

```
cd ./WALA
./gradlew assemble
cd ..
```

#### Swift

```
cd ./swift
./utils/update-checkout --clone
./utils/build-script
cd ..
```
Optionally, the `-d` flag can be added to the `build-script` so Swift can compile in debug mode.

#### Edit Swift-WALA Configurations

```
cd swift-wala/com.ibm.wala.cast.swift
cp gradle.properties.example gradle.properties
```

Edit `gradle.properties` and provide proper paths. Some example paths are already provided to give you an idea of what they might look like for you. For macOS, change the `linux` to `macosx` in the paths. (e.g `swift-linux-x86_64` to `swift-macosx-x86_64`)


#### Build Swift-WALA

```
./gradlew assemble
```


#### Trouble building
Depending on what and where the failure is, you can try checking out an older commit. For Swift, you must first run `./utils/update-checkout --clone` and then `git checkout <commit>`. This is because `update-checkout` will pull the latest Swift from master. It will also pull in latest dependencies (which are unlikely to be the cause of build failure), but you can revert back to an older version of the `apple/swift` repo. For WALA, you can simply try checking out an older commit. 

If you would like to find the offending commit, you should first narrow down the timeframe of where an offending change could have occured. This is easier to do if you know when the last successful build was and if Swift and WALA were up-to-date at that time. The build failure should be obvious enough to point you to either WALA or Swift as the culprit. From there, you can browse the commit history and try `revert` on suspicious commits until a successful build occurs. Yes, this can be a lengthy process since the Swift compiler takes very long to compile.

Please open up an issue if you are experiencing build issues or difficulties.

### Running Swift-WALA

- First you need to setup environment variables. You can also add this to your `~/.bashrc` or `~/.bash_profile`. Make sure to `source` after. The first two are the same as those set in `gradle.properties` and the third is just the directory this repo is in. 

```
export WALA_PATH_TO_SWIFT_BUILD={path/to/your/swift/build/dir}
export WALA_DIR={path/to/your/wala/dir}
export SWIFT_WALA_DIR={path/to/your/swift-wala/dir}
```

#### Standalone executable

The standalone C++ program is the current method of running the framework. Once SWAN is built, the executable can be found in `{swift-wala/dir/}/com.ibm.wala.cast.swift/swift-wala-translator/build/external-build/swiftWala/linux_x86-64/bin`

The program takes one parameter, which is the Swift file you want to analyze. SWAN only support one Swift file currently.
```
./swift-wala-translator-standalone example.swift
```
