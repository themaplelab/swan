<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

[![Java CI with Gradle](https://github.com/themaplelab/swan/workflows/Java%20CI%20with%20Gradle/badge.svg?branch=spds)](https://github.com/themaplelab/swan/actions)

# SWAN

This branch contains the new generation of the SWAN framework.

The SWAN version described in our ESEC/FSE 2020 [paper](https://karimali.ca/resources/papers/swan.pdf) is located on [this](https://github.com/themaplelab/swan/tree/master) branch.

###  Summary

SWAN is a static program analysis framework that enables deep dataflow analysis for Swift applications (incl. iOS/macOS). We are developing it for various analysis applications, such as finding API misuses and detecting security vulnerabilities using taint analysis.

:new: We have redesigned SWAN entirely. It now parses plain-text Swift Intermediate Language (SIL) that our wrappers for `xcodebuild` and `swiftc` can dump for Xcode projects and single Swift files. Previously, we hooked into the Swift compiler, which created many build problems and added complexity. We have developed a new IR, called *SWIRL*, that is simple, well documented, and easy to understand. Any analysis engine should be able to analyze SWIRL without having to handle complex semantics. We are currently working on integrating SWIRL into [Synchronized Pushdown Systems (SPDS)](https://github.com/CodeShield-Security/SPDS).

We aim to provide developers and researchers with an easy-to-use and well-documented platform for analyzing Swift applications. SWAN will enable many analysis possibilities, such as defining sources and sinks for taint analysis, API dataflow and security modelling, and API misuse schemes.

:construction: **It is still WIP and not ready for use. We have developed the language frontend and are working on the analysis.**

### Features

- SIL parser (99% coverage, up to 100k lines/second)
- Well documented intermediate representation (IR) is easy to convert to other IRs (includes parser)
- Wrappers for `xcodebuild` and `swiftc` that build and dump SIL
- Modular IR translation pipeline (for integration with other engines)
- Optimizations: multi-threaded module processing, caching, selective parsing
- Development tool for viewing Swift, SIL, and SWIRL side-by-side
- SPDS integration (:construction: WIP)

### Relevant Wiki pages

- [SWIRL](https://github.com/themaplelab/swan/wiki/SWIRL)
- [SIL To SWIRL Spec](https://github.com/themaplelab/swan/wiki/SIL-To-SWIRL-Spec)

## Toolchain Usage

SWAN's toolchain uses a two-step process: 1) build and dump SIL, 2) analyze SIL.

#### Build systems

You need to build your project with `xcodebuild`, and therefore you need an `.xcodeproj`.

If your project uses the **Swift Package Manager**, you will need to generate a `.xcodeproj` for your project, which you can do with `swift package generate-xcodeproj`.

If you use **CocoaPods**, make sure to use `-workspace` instead of `-project`. 

You can also look into adding [XcodeGen](https://github.com/yonaskolb/XcodeGen) to your project to generate the `.xcodeproj`.

If you are unsure what schemes or targets you can build, you can use `-list` with `xcodebuild`.

If you can build your project with `xcodebuild`, you can build your project with `swan-xcodebuild`.

#### Dump SIL using either `swan-swiftc` or `swan-xcodebuild`

Use `swan-xcodebuild` for building and dumping SIL for Xcode projects. Give it the same arguments you give `xcodebuild`, but put them after `--`. e.g.,

```
swan-xcodebuild -- -project MyProject.xcodeproj -scheme MyScheme
```

It will build your project and then dump the SIL to the `swan-dir/` directory. You can optionally specify an alternative directory with `--swan-dir`.

```
swan-xcodebuild --swan-dir custom-dir -- [...]
```

The same idea applies for `swan-swiftc`, which dumps SIL for single `.swift` files, but you only need to specify the Swift file you want to dump the SIL for. This tool wraps `swiftc` so any arguments you give it will go to `swiftc`.

```
swan-swiftc -- MyFile.swift
```

### Client

There is currently no ready dataflow client for SWAN. However, you can use `driver.jar` to parse, translate, and combine the dumped SIL modules.

```
java -jar driver.jar swan-dir/
```

You can use `-h` to view driver options. You can use `-d` and view the generated IRs inside of `swan-dir/debug/`. The caching (`-c`) option is currently experimental. We are investigating ways of making subsequent SIL consumption faster.

## Developing

```
git clone git@github.com:themaplelab/swan.git -b spds
```

Add your GitHub username and [personal access token](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) (with read:packages) to `jvm/gradle.properties`. The SPDS dependency requires this. Do **not** push these credentials.

Run `./build.sh` in the repo root. You can also run nested `build.sh` scripts **from root** to build separate toolchain components.

All toolchain executables should now be available in `lib/`.

### IDE

Open `jvm/` in IntelliJ. Be sure to select *Import as Gradle Project*.

Install the Scala plugin (*Preferences -> Plugins*, Search for *Scala*).

Run the *General Tests* configuration to test everything works.

See [IDE Configuration](https://github.com/themaplelab/swan/wiki/IDE-Configuration) if you would like to configure syntax highlighting for SWIRL and SIL.

You can use the *Playground* run configurations to debug Swift, SIL, and SWIRL code. Just paste the code in question to the appropriate `playground.*` in `resources/`.
