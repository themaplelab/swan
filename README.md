
<p align="center">
<img src="https://karimali.ca/resources/images/projects/swan.png" width="300">
</p>

[![macOS CI](https://github.com/themaplelab/swan/actions/workflows/macOS.yml/badge.svg)](https://github.com/themaplelab/swan/actions/workflows/macOS.yml) [![Ubuntu CI](https://github.com/themaplelab/swan/actions/workflows/ubuntu.yml/badge.svg)](https://github.com/themaplelab/swan/actions/workflows/ubuntu.yml)

# SWAN

This branch contains the new generation of the SWAN framework.

The SWAN version described in our ESEC/FSE 2020 [paper](https://karimali.ca/resources/papers/swan.pdf) is located on [this](https://github.com/themaplelab/swan/tree/2020) branch. This paper no longer represents the current state of SWAN because we have redesigned it entirely.

###  Summary

SWAN is a static program analysis framework that enables deep dataflow analysis for Swift applications (incl. iOS/macOS). Its applications include finding API misuses using typestate analysis and detecting security vulnerabilities using taint analysis.

SWAN parses plain-text Swift Intermediate Language (SIL). We have developed an intermediate representation (IR), called *SWIRL*. It is simple, well documented, and easy to understand. Any analysis engine should be able to analyze SWIRL without having to handle complex semantics.

We aim to provide developers and researchers with an easy-to-use and well-documented platform for analyzing Swift applications.

:construction: **SWAN is WIP. However, we are working on a pre-release to get the community involved. We will release an extensive video playlist explaining how SWAN works, which should enable you to experiment with SWAN.**

### Features

- Wrappers for `xcodebuild` and `swiftc` that build and dump SIL
- SIL parser (99% coverage, up to 100k lines/second)
- Well documented intermediate representation, called SWIRL, that is easy to convert to other IRs
- Ability to write models for black-box functions with SWIRL
- Modular IR translation pipeline (for integration with other engines)
- Development tool for viewing Swift, SIL, and SWIRL side-by-side
- Optimizations: multi-threaded module processing, caching, selective parsing
- Cross-module analysis support
- [Synchronized Pushdown Systems (SPDS)](https://github.com/CodeShield-Security/SPDS) integration
- Call graph construction
- Configurable taint analysis with annotation checker for regression testing

### Currently working on

- typestate analysis with the eventual goal of integrating [CogniCrypt](https://github.com/CROSSINGTUD/CryptoAnalysis) into SWAN
- Swift Standard Library modeling
- iOS lifecycle support
- ... and much more!

### Relevant Wiki pages

- [SWIRL](https://github.com/themaplelab/swan/wiki/SWIRL)
- [SIL To SWIRL Spec](https://github.com/themaplelab/swan/wiki/SIL-To-SWIRL-Spec)
- [IDE Configuration](https://github.com/themaplelab/swan/wiki/IDE-Configuration)

## Getting started

For now, you will need to build the framework to use SWAN, but soon we will make a release available.

We have tested SWAN on macOS Big Sur Xcode 12.5 and Ubuntu 20.04, Swift 5.4. You need Xcode Command Line Tools installed for macOS, or the latest Swift release for Linux (see [this](https://linuxconfig.org/how-to-install-swift-on-ubuntu-20-04)). Anything involving "Xcode" will not work on Linux, but you should be able to build Swift Package Manager projects. You also need Java 8.

```
git clone https://github.com/themaplelab/swan.git -b spds
```

Add your GitHub username and [personal access token](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) (with read:packages) to `jvm/gradle.properties`. The SPDS dependency requires this. Do **not** push these credentials.

Copy `swift-demangle` to `/usr/local/bin/` or add it to `$PATH`. On Linux, `swift-demangle` is distributed alongside `swiftc`, so you do not need to do this step. You also need `swiftc` and `xcodebuild` to be on `$PATH`.

```
sudo cp /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/swift-demangle /usr/local/bin/
```


Run `build.sh` in the repo root. You can also run the nested `build.sh` scripts **from root** to build separate toolchain components.

All toolchain components should now be available in `lib/`. If you want to make sure everything works, you can run run `./gradlew build` in `jvm/` and `./test.bash` in `tests/`.

-------

SWAN's toolchain uses a three-step process:
1. Build the Swift application and dump SIL to a directory
2. Analyze the SIL in the directory
3. Process analysis results

### 1. Dump SIL using either `swan-swiftc` or `swan-xcodebuild`

You can dump SIL for Xcode projects with `swan-xcodebuild`. Give it the same arguments you give `xcodebuild`, but put them after `--` (`swan-xcodebuild` specific arguments go before). If you specify a single architecture with `-arch`, the build time will be faster, and `swan-xcodebuild` will have less output to parse.

It will build your project and then dump the SIL to the `swan-dir/` directory. You can optionally specify an alternative directory name with `--swan-dir`.

```
swan-xcodebuild -- -project MyProject.xcodeproj -scheme MyScheme -arch arm64
```

The same idea applies for `swan-swiftc`, which dumps SIL for single `.swift` files, and you only need to specify the Swift file.

```
swan-swiftc -- MyFile.swift
```
#### Generating Xcode projects

To build your project with `(swan-)xcodebuild` you need an `.xcodeproj`. If your project uses the **Swift Package Manager (SPM)**, you will need to generate a `.xcodeproj` for your project, which you can do with `swift package generate-xcodeproj`. If you use **CocoaPods**, make sure to use `-workspace` instead of `-project`. You can also look into adding [XcodeGen](https://github.com/yonaskolb/XcodeGen) to your project to generate the `.xcodeproj`. If you are unsure what schemes or targets you can build, you can use `-list`.

### 2. Analysis

The analysis currently has no default specification. Use the `driver.jar` to analyze the SIL in the `swan-dir/`. You can use `-h` to view the driver options.

```
java -jar driver.jar -j basic-spec.json swan-dir/
```

If you would like to run taint analysis, you must use the `-j` option and provide a taint analysis JSON specification file like the following:
```
[{
  "name": "testing",
  "sources": [
    "test.source() -> Swift.String"
  ],
  "sinks": [
    "test.sink(sunk: Swift.String) -> ()"
  ],
  "sanitizers": [] // optional
}]
```

### 3. Processing analysis results
The driver writes the taint analysis results to `swan-dir/results.json`.

```
[{
  "name": "testing",
  "paths": [
    {
      "source": "test.source() -> Swift.String",
      "sink": "test.sink(sunk: Swift.String) -> ()",
      "path": [
        "test.swift:9:15",
        "test.swift:10:12"
      ]
    }
  ]
}]
```
You can annotate the source code and verify the results are correct automatically with `annotation.jar`.
```
 9: let sourced = source(); //!testing!source
10: sink(sunk: sourced); //!testing!sink
```
Once you run the driver, you can run the following to check the annotations against the results.
```
java -jar annotation.jar swan-dir/
```
This is intended for automatic regression testing. You can take a look inside `tests/` to get a good idea of how annotation testing works.

### IDE

Open `jvm/` in IntelliJ. Be sure to select *Import as Gradle Project*.

Install the Scala plugin (*Preferences -> Plugins*, Search for *Scala*).

See [IDE Configuration](https://github.com/themaplelab/swan/wiki/IDE-Configuration) if you would like to configure syntax highlighting for SWIRL and SIL.

You can use the *Playground* run configurations to debug specific Swift, SIL, and SWIRL cases. Just paste the code in question to the appropriate `playground.*` in `jvm/resources/playground/`. 
