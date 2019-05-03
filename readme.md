# Contribute to Swift-WALA

### Swift 5 fix changes
- Swift compiler has removed the virtual method `performedSILGeneration` that was used to hook in perviously
- They added another virtual method called `configuredCompiler(CompilerInstance &CompilerInstance)`, from which we can get the SIL Module. However, the `SILModule`s are no longer passed by reference, but instead use `unique_ptr<SILModule>`, which makes things annoying. Using `CompilerInstance.takeSILModule()` we can take the `SILModule`, read it, but then we must give it back to the compiler. Therefore, we now have to `move` the `unique_ptr` around, just as the compiler does.
- LLVM API has [changed](https://reviews.llvm.org/D45641), which is problematic for how we take in arguments. This has been fixed, although not for certain.
- Some paths from `build.gradle` were [removed](https://github.com/themaplelab/swan/commit/f718f5e335eaeb019e4cd9130fbd30b7fe42e031). These have been added back and it appears the build issues that were arising due to that are solved.
- When cloning SWAN, I renamed `swan/` to `swift-wala/` but I don't think this is neccessary.
- Do **NOT** use quotation marks in `gradle.properties`
- **WHAT DOESN"T WORK:** [linker issue](https://termbin.com/sqe3) 
- The build behaves the same on Mac as on Linux.
- Current linker issues are preventing us from confirming that the two build issues are indeed fixed, and functionality still works at runtime.
- [Added](https://github.com/themaplelab/swan/commit/1f18a1b63d11896067e52f783f2b2ee6917600a2) new case for [new](https://github.com/apple/swift/commit/425c190086e2c534f016ee3c8efa577b17d1d2c9) instruction mark type in `SILWalaInstructionVisitor.cpp`

## Download Projects

```
mkdir swift-source
cd swift-source
git clone https://github.com/apple/swift
git clone https://github.com/wala/WALA
git clone https://github.com/themaplelab/swan
```

## Build Dependencies


### WALA

```
cd ./WALA
./gradlew assemble
cd ..
```

### Swift

```
cd ./swift
./utils/update-checkout --clone
./utils/build-script -d
cd ..
```


### Edit Swift-WALA Configurations

```
cd swift-wala/com.ibm.wala.cast.swift
cp gradle.properties.example gradle.properties
```

Edit `gradle.properties` and provide proper paths.


### Build Swift-WALA

```
./gradlew assemble
```


### Run The Program


- First you need to setup environment variables.

```
export WALA_PATH_TO_SWIFT_BUILD={path/to/your/swift/build/dir}
export WALA_DIR={path/to/your/wala/dir}
export SWIFT_WALA_DIR={path/to/your/swift-wala/dir}
```


- To run the Java code:

`./gradlew run --args THE_ARGS_YOU_WANT`


- Otherwise, you can run the standalone c++ code in `{swift-wala/dir/}/com.ibm.wala.cast.swift/swift-wala-translator/build/external-build/swiftWala/linux_x86-64/bin`.

```
./swift-wala-translator-standalone example.swift
```
