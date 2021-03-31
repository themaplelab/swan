<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

[![Java CI with Gradle](https://github.com/themaplelab/swan/workflows/Java%20CI%20with%20Gradle/badge.svg?branch=spds)](https://github.com/themaplelab/swan/actions)

# :new: SWAN

This branch contains the new generation of the SWAN framework. **It is still WIP and not ready for use. ​**:construction: 

SWAN ESEC/FSE 2020 is located on [this](https://github.com/themaplelab/swan/tree/master) branch.

###  Summary

We have redesigned SWAN entirely. It now parses plain-text SIL that our wrappers for `xcodebuild` and `swiftc` can dump for Xcode projects and single Swift files. Previously, we hooked into the Swift compiler, which created many build problems and added complexity. We've developed a new IR, called *SWIRL*, that is simple, well documented, and easy to understand. Any analysis engine should be able to analyze SWIRL without having to handle complex semantics. We are currently working on integrating SWIRL into [SPDS](https://github.com/CodeShield-Security/SPDS).

### Relevant Wiki pages

- [SWIRL](https://github.com/themaplelab/swan/wiki/SWIRL)
- [SIL To SWIRL Spec](https://github.com/themaplelab/swan/wiki/SIL-To-SWIRL-Spec)

## Developing

```
git clone git@github.com:themaplelab/swan.git -b spds
```

Add your GitHub username and [personal access token](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) (with read:packages) to `jvm/gradle.properties`. The SPDS dependency requires this. Do **not** push these credentials.

Run `./build.sh` in the repo root. You can also run nested `build.sh` scripts from root to build separate toolchain components.

All toolchain executables should now be available in `lib/`.
### IDE

Open `jvm/` in IntelliJ. Be sure to select *Import as Gradle Project*.

Install the Scala plugin (*Preferences -> Plugins*, Search for *Scala*).

Run the *General Tests* configuration to test everything works.

## Toolchain Usage

### Dump SIL using either `swan-swiftc` or `swan-xcodebuild`

`swan-xcodebuild` is used for building and dumping SIL for Xcode projects. Use it as you usually would use `xcodebuild`, but put your arguments after `--`. If your project uses the Swift Package Manager (SPM), you will need to generate a `.xcodeproj` for it if you haven't done so already, which you can do with `swift package generate-xcodeproj`. If you use CocoaPods, make sure to use `-workspace` instead of `-project`. If you are unsure what schemes or targets you can build, you can use `-list` with `xcodebuild`.
```
swan-xcodebuild -- -project MyProject.xcodeproj -scheme MyScheme
```
You can optionally specify the output directory with `--swan-dir`.
```
swan-xcodebuild --swan-dir custom-dir -- -project []...]`
```
The same idea applies for `swan-swiftc`, but you need to specify the Swift file you want to dump the SIL for.
```
swan-swiftc -- MyFile.swift
```

### Client

There is currently no ready dataflow client for SWAN. However, you can use the `default-driver`, which will parse, translate, and combine the dumped SIL modules.

```
java -jar default-driver.jar swan-dir/
```
