
<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# SWAN
A static program analysis framework for analyzing Swift (incl. iOS/macOS) applications using [WALA](https://github.com/wala/WALA) as the analysis core. 

## Introduction

SWAN is being developed for detecting security vulnerabilities in Swift applications using taint analysis. A custom translator converts Swift Intermediate Language ([SIL](https://github.com/apple/swift/blob/master/docs/SIL.rst)) to WALA IR (called [CAst](http://wala.sourceforge.net/javadocs/trunk/com/ibm/wala/cast/tree/package-summary.html)). The SIL is retrieved by hooking into the Swift compiler and grabbing the SIL module during compilation. Since many languages can be translated to similar looking CAst, we are able to use WALA's JavaScript translators and analysis (with some modifcations) on the resulting CAst.

The translator aims to support every SIL instruction seen in practice.

## Current work
- Finishing the new translator.
- Taint Analysis.
- Multi-file support for iOS and macOS apps.

## Future plans
- Lifecycle awareness for iOS and macOS applications (custom call graph building)
- Sources, cleaners, and sinks identified for iOS and macOS libraries

## Getting Started

First, you should consider that the final build **may be as large as ~70GB**. It is recommended for your system to have **at least 16GB of RAM**, as compiling Swift is known to not compile for some systems with less. Also, please read Swift's [README](https://github.com/apple/swift/blob/master/README.md) for the latest dependencies (e.g. Xcode beta version).

### Release support
Supported Swift (incl. dependencies) and WALA releases on SWAN's `master` branch. 

| OS | Swift Release Tag | WALA Release Tag | 
| -----------|:-------:|:-----:|
| macOS Mojave | [master](https://github.com/apple/swift/tree/master) | [master](https://github.com/wala/WALA/tree/master/) |
| Linux (Ubuntu 18.04) | [master](https://github.com/apple/swift/tree/master) | [master](https://github.com/wala/WALA/tree/master/) |

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
See compatibility table above for supported release tags according to your OS. Please open up an issue if you are experiencing build issues or difficulties. You can ignore the `(--tag) SUPPORTED_TAG` part if `master` is supported.

**These must be built in the exact order as below.** 

#### Swift

```
cd ./swift
./utils/update-checkout --clone --tag SUPPORTED_TAG
./utils/build-script 
cd ..
```

#### WALA

You must be using **Java 8** in order to compile WALA. If you need help switching to Java 8, please see our [guide](https://github.com/themaplelab/swan/wiki/Switching-to-Java-8) on it.

```
cd ./WALA
git checkout SUPPORTED_TAG
./gradlew assemble
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

First you need to set some environment variables. You can also add the following to your `~/.bashrc` or `~/.bash_profile`, but make sure to `source` after. **Restart IDEA if you have it open.** The first variable is the same as in `gradle.properties`. The second variable is just the path to the directory containing this cloned repository.

```
export WALA_PATH_TO_SWIFT_BUILD={path/to/your/swift/build/dir}
export PATH_TO_SWAN={path/to/swan/dir}
```

The current analysis driver generates WALA IR, creates a call graph, and runs a taint analysis. Sources and sinks are hardcoded only for `testTaint.swift` currently.

**IDEA**

There are some run configs ready to use for single Swift test files.
 
**From the terminal**

You must run the analysis by running the following in the **root** directory. There are some test files in `ca.maple.swan.swift.test/testFiles`.

It is recommended you use the full path of files as our pathing code is fairly volatile.
```
./gradlew run --args='<MODE> <ARGS>'
```
`MODE`: `SINGLE` or `iOS`

`ARGS`: file for `SINGLE` mode, or arguments to `performFrontend()` for `iOS` mode. These should come from the shim script.

Single quotes are important here.

**Compiling iOS/macOS apps**

Obviously, this is only for those running macOS.

SWAN intercepts arguments going to `swiftc` by using the `SWIFT_EXEC` argument to `xcodebuild`. Therefore, your application must be compiled using `xcodebuild`. The shim script that needs to be given to `SWIFT_EXEC` is in `$PATH_TO_SWAN/ca.maple.swan.translator/shim.py`. 

An example call to `xcodebuild` might look like this.
```
xcodebuild clean build -project swift-2048.xcodeproj -scheme swift-2048 CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED="NO" SWIFT_COMPILATION_MODE=wholemodule SWIFT_OPTIMIZATION_LEVEL=-Onone SWIFT_EXEC=$PATH_TO_SWAN/ca.maple.swan.translator/shim.py
```
Note that you might need to set `shim.py` to be executable as it runs like a regular bash script. The three necessary arguments for SWAN from the above example are..
1. `SWIFT_COMPILATION_MODE=wholemodule`
2. `SWIFT_OPTIMIZATION_LEVEL=-Onone`
3. `SWIFT_EXEC=$PATH_TO_SWAN/ca.maple.swan.translator/shim.py`
                                        
The rest are regular arguments or arguments needed to compile without code signing.

## Contributing
Please see the [page](https://github.com/themaplelab/swan/wiki/Contributing) on contributing.

--------------------

**You should expect errors as SWAN is WIP and not fully functional.**
