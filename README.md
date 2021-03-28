<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

[![Java CI with Gradle](https://github.com/themaplelab/swan/workflows/Java%20CI%20with%20Gradle/badge.svg?branch=spds)](https://github.com/themaplelab/swan/actions)

# :new: SWAN

This branch contains the new generation of the SWAN framework. **It is still WIP and not ready for use. ​**:construction: SWAN ESEC/FSE 2020 is located on [this](https://github.com/themaplelab/swan/tree/master) branch.

###  Summary

We have completely redesigned SWAN. It now parses plain-text SIL that can either be dumped with `xcodebuild` or `swiftc`. Previously, we hooked into the Swift compiler which created many build problems and added complexity. We've developed a new IR, called *SWIRL*, that is simple, well documented, and easy to understand. Any analysis engine should be able to analyze SWIRL without having to handle complex semantics. We are currently working on integrating SWIRL into [SPDS](https://github.com/CodeShield-Security/SPDS).

### Relevant Wiki pages

- [SWIRL](https://github.com/themaplelab/swan/wiki/SWIRL)
- [SIL To SWIRL Spec](https://github.com/themaplelab/swan/wiki/SIL-To-SWIRL-Spec)

## Developing

```
git clone git@github.com:themaplelab/swan.git -b spds
```

Add your github username and [personal access token](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) (with read:packages) to `jvm/gradle.properties`. This is required for adding SPDS as a dependency. Do **not** push these.

Run `./build.sh` in the repo root. You can also run nested `build.sh` scripts from root to build separate toolchain components.

All toolchain executables should now be available in `lib/`.
### IDE

Open `jvm/` in IntelliJ. Be sure to select *Import as Gradle Project*.

Run the *SWIRL Tests* and *SIL Tests* configurations to test everything works.

## Toolchain Usage

### Dump SIL using either `swan-swiftc` or `swan-xcodebuild`

`swan-xcodebuild` is used for building and dumping SIL for Xcode projects. Use it as you normally would use `xcodebuild`, but put your arguments after `--`.
```
swan-xcodebuild -- -project MyProject.xcodeproj -scheme MyScheme
```
You can optionally specify the output directory with `--swan-dir`.
```
swan-xcodebuild --swan-dir custom-dir -- -project []...]`
```
The same idea applies for `swan-swiftc`, but you only specify the Swift file you want to dump the SIL for.
```
swan-swiftc -- MyFile.swift
```

### Analysis Client

There is currently no ready dataflow client for SWAN.