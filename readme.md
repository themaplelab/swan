
<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# SWAN
A static program analysis framework for analyzing Swift (incl. iOS/macOS) applications using [WALA](https://github.com/wala/WALA) as the analysis core. SWAN is being developed for detecting security vulnerabilities in Swift applications using taint analysis.

![](https://github.com/themaplelab/swan/blob/master/docs/readmeContent/exampleUse.gif)

## Getting Started

First, you should consider that the final build **may be as large as ~70GB**. It is recommended for your system to have **at least 16GB of RAM**, as compiling Swift is known to not compile for some systems with less.

### Release support
Supported Swift (incl. dependencies) and WALA releases on SWAN's `master` branch.

| OS | Swift Release Tag | WALA Release Tag |
| -----------|:-------:|:-----:|
| macOS Catalina | NOT SUPPORTED | |
| macOS Mojave | \*[swift-DEVELOPMENT-SNAPSHOT-2019-09-15-a](https://github.com/apple/swift/releases/tag/swift-DEVELOPMENT-SNAPSHOT-2019-09-15-a) | [v1.5.3](https://github.com/wala/WALA/releases/tag/v1.5.3) |
| Linux (Ubuntu 18.04) | [swift-DEVELOPMENT-SNAPSHOT-2019-09-15-a](https://github.com/apple/swift/releases/tag/swift-DEVELOPMENT-SNAPSHOT-2019-09-15-a) | [v1.5.3](https://github.com/wala/WALA/releases/tag/v1.5.3) |

Note: SWAN currently uses Xcode 11.2 Beta 2.

\* **We cannot gaurantee you can build the Swift tag anymore. We can no longer build that tag on macOS 10.14.6.** 

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

#### Scripts

Runs SWAN on a given file and prints the analysis results (paths).
```
./utils/run-swan-single -sdk <path_to_macosx_sdk> -path <path_to_test_file>
```

Runs SWAN on a given file and verifies its annotations are correct.
```
./utils/run-swan-single-annotation -sdk <path_to_macosx_sdk> -path <path_to_test_file>
```

Tests all files under the given dir and verifies their annotations are correct. Test files can be found under `ca.maple.swan.swift.test/tests/`.
```
./utils/run-tests -sdk <path_to_macosx_sdk> -dir <path_to_swift_file>
```

## Contributing
Please see the [page](https://github.com/themaplelab/swan/wiki/Contributing) on contributing.

--------------------

:construction: **You should expect errors as SWAN is WIP and not fully functional.**
