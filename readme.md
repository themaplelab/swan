
<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# SWAN
A static program analysis framework for analyzing Swift (incl. iOS/macOS) applications using [WALA](https://github.com/wala/WALA) as the analysis core. SWAN is being developed for detecting security vulnerabilities in Swift applications using taint analysis.

![](https://github.com/themaplelab/swan/blob/master/docs/readmeContent/exampleUse.gif)

## Current work
- Extending Swift language support
- Extending taint analysis capabilities

## Future plans
- Lifecycle awareness for iOS and macOS applications
- Sources, sinks, and sanitizers identified for iOS and macOS libraries

## Getting Started

First, you should consider that the final build **may be as large as ~70GB**. It is recommended for your system to have **at least 16GB of RAM**, as compiling Swift is known to not compile for some systems with less.

### Release support
Supported Swift (incl. dependencies) and WALA releases on SWAN's `master` branch.

| OS | Swift Release Tag | WALA Release Tag |
| -----------|:-------:|:-----:|
| macOS Catalina | NOT SUPPORTED | |
| macOS Mojave | [swift-DEVELOPMENT-SNAPSHOT-2020-01-24-a](https://github.com/apple/swift/releases/tag/swift-DEVELOPMENT-SNAPSHOT-2020-01-24-a) | [v1.5.4](https://github.com/wala/WALA/releases/tag/v1.5.4) |
| Linux (Ubuntu 18.04) | [swift-DEVELOPMENT-SNAPSHOT-2020-01-24-a](https://github.com/apple/swift/releases/tag/swift-DEVELOPMENT-SNAPSHOT-2020-01-24-a) | [v1.5.4](https://github.com/wala/WALA/releases/tag/v1.5.4) |

Note: SWAN currently uses Xcode 11.3.

### Building SWAN

:warning: You must be using **Java 8**. If you need help switching to Java 8, please see our [guide](https://github.com/themaplelab/swan/wiki/Switching-to-Java-8) on it.

:warning: Before building, see Swift's [readme](https://github.com/apple/swift/tree/4555611f042642dfd97e07d7660d01ee6e2c467f) for required dependencies.
```
git clone https://github.com/themaplelab/swan
cd swan/
./utils/update-packages
./utils/build-packages
./utils/build-swan
```

:warning: Set `PATH_TO_SWAN` environment variable to point to the `swan/` directory.

:mag_right: SWAN must be built for IDE IntelliSense.

### Running SWAN

SWAN uses its own [VSCode extension](https://github.com/themaplelab/swan-vscode) as the main driver. The extension has not been published yet, so you will need to run it yourself. See the [README](https://github.com/themaplelab/swan-vscode/blob/master/README.md) of the `swan-vscode` repo for instructions.

:mag_right: The extension is located in `packages/swan-vscode/`.

The extension can launch the JVM itself. Alternatively, you can first start the JVM via the Intellij run configuration called _Server_ before using the extension. This is useful for debugging.

We hope to switch over to LSP in the future if the protocol is further developed.

## Contributing
Please see the [page](https://github.com/themaplelab/swan/wiki/Contributing) on contributing.

--------------------

:construction: **You should expect errors as SWAN is WIP and not fully functional.**
