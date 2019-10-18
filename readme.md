
<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# SWAN
A static program analysis framework for analyzing Swift (incl. iOS/macOS) applications using [WALA](https://github.com/wala/WALA) as the analysis core. 

## Introduction

SWAN is being developed for detecting security vulnerabilities in Swift applications using taint analysis. A custom translator converts Swift Intermediate Language ([SIL](https://github.com/apple/swift/blob/master/docs/SIL.rst)) to WALA AST ([CAst](http://wala.sourceforge.net/javadocs/trunk/com/ibm/wala/cast/tree/package-summary.html)). The SIL is retrieved by hooking into the Swift compiler and grabbing the SIL module during compilation. Since many languages can be translated to similar looking CAst, we are able to use WALA's JavaScript translators and analysis (with some modifcations) on the resulting CAst.

The translator aims to support every SIL instruction seen in practice.

## Current work
- Finishing translation
- Extending taint analysis capabilities

## Future plans
- Lifecycle awareness for iOS and macOS applications (custom call graph building)
- Sources, sinks, and sanitizers identified for iOS and macOS libraries

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

First you need to set some environment variables. You should add the following to your `~/.bashrc` or `~/.bash_profile`, but make sure to `source` after. The first variable is the same as in `gradle.properties`. The second variable is just the path to the directory containing this cloned repository.

```
export WALA_PATH_TO_SWIFT_BUILD={path/to/your/swift/build/dir}
export PATH_TO_SWAN={path/to/swan/dir}
```

SWAN uses its own [VSCode extension](https://github.com/themaplelab/swan-vscode) as the main driver. The extension has not been published yet, so you will need to run it yourself. See the [README](https://github.com/themaplelab/swan-vscode/blob/master/README.md) of the `swan-vscode` repo for instructions.

The extension can launch the JVM itself. Alternatively, you can first start the JVM via the Intellij run configuration called _Server_ before using the extension. This is useful for debugging.


We hope to switch over to LSP in the future if the protocol is further developed.

## Contributing
Please see the [page](https://github.com/themaplelab/swan/wiki/Contributing) on contributing.

--------------------

**You should expect errors as SWAN is WIP and not fully functional.**
