
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

First, you should consider that the final build may be as large as ~70GB.

### Release support
Supported Swift (incl. dependencies) and WALA releases on SWAN's `master` branch.


| OS | Swift Release Tag | WALA Release Tag* | 
| -----------|:-------:|:-----:|
| macOS Mojave | [master](https://github.com/apple/swift/tree/master) | [v1.5.3](https://github.com/wala/WALA/releases/tag/v1.5.3) |
| Linux (Ubuntu 18.04) | [master](https://github.com/apple/swift/tree/master) | [v1.5.3](https://github.com/wala/WALA/releases/tag/v1.5.3) |

**\*You must be using Java 8 in order to compile WALA.**

### Switching to Java 8

If you are not using Java 8 and wish to retain your current Java version, you can do the following after installing Java 8. [Credit.](https://stackoverflow.com/a/40754792)
Add the following to your `~/.bash_profile` (macOS) or `~/.bashrc` (Linux).
##### macOS
```
alias j<YOUR_VERSION>="export JAVA_HOME=`/usr/libexec/java_home -v <YOUR_VERSION>`; java -version"
alias j8="export JAVA_HOME=`/usr/libexec/java_home -v 8`; java -version"
```
##### Linux
You will need to check `/usr/lib/jvm` for the directory name of your current version. 
```
alias j<YOUR_VERSION>="export JAVA_HOME=`/usr/lib/jvm/YOUR_JAVA_DIR/bin/java`; java -version"
alias j8="export JAVA_HOME=`/usr/lib/jvm/java-8-oracle/bin/java`; java -version"
```
Where `<YOUR_VERSION>` is your current Java version you wish to revert back to afterwards. Your can find your current Java version by typing `java -version`. Make sure to `source` after.

Then, you can switch Java version by using the alias. e.g.
```
$ j8
java version "1.8.0_201"
Java(TM) SE Runtime Environment (build 1.8.0_201-b09)
Java HotSpot(TM) 64-Bit Server VM (build 25.201-b09, mixed mode)
```

Note that this only temporarily sets your Java version. If you have downloaded Java 8 but do not want it to be your default, you can add the `export ...` part of the command to your `~/.bashrc` or `~/.bash_profile`.

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

You must run the analysis by running the following in the **root** directory. However, the test files must lie inside of `ca.maple.swan.swift.test/`. You may use the files under `testFiles/` there.
```
./gradlew run --args="YOUR_SWIFT_FILE"
```

You should expect exceptions on this branch as it is WIP and not fully functional.
