## Testing

The testing scripts are WIP and we are adding features as we need them.

### Adding tests

If you want to add a new set of tests:

1. Create a new directory in a fitting spot with a good name that justifies having a separate directory for the tests
2. Create a `test.bash` in the new directory with the following contents (replace `<relative_path_to_tester.bash>` and make sure it is executable)

```
#!/usr/bin/env bash
source <relative_path_to_tester.bash>
test_directories
```

If you want to add a new test to an existing group

1. Create a new directory with a descriptive name under the appropriate directory
2. Add a `test.bash` with the following contents (replace `<relative_path_to_tester.bash>`, and make sure it is executable

```
#!/usr/bin/env bash
source <relative_path_to_tester.bash>
test_directory
```

Add your test. Choose from the following test styles.

- `test.swift`
- Swift Package Manager with `Sources/main.swift` (single source file only) and `Package.swift`. You must configure Package.swift to have the following target options.

```
swiftSettings: [
  .unsafeFlags([
    "-Xfrontend",
    "-gsil",
    "-Xllvm",
    "-sil-print-debuginfo",
    "-Xllvm",
    "-sil-print-before=SerializeSILPass"
    ])
]
```

The `swan-spm.py` script will only dump SIL for the source file (not dependencies). Therefore, if you need a dependency, use `import "<your dependency>"` in `test.bash`. If the dependency does not already exist in `tests/sil-packages/`, build an app that uses the dependency with `swan-xcodebuild` and copy the SIL file from `swan-dir/` to `tests/sil-packages/` and rename it. If you don't have macOS, request someone who does to add the dependency for you.

```
source ../../tester.bash
import "ColorizeSwift.sil"
test_directory
```

### Using a custom specification

If you want to use a custom taint analysis specification, add it to the test file and set `CUSTOM_SPEC` in `test.bash`.

```
source ../../tester.bash
CUSTOM_SPEC=custom-spec.json
test_directory
```

If you want to do typestate analysis, you must provide a specification with `TYPESTATE_SPEC` in `test.bash`. This will disable taint analysis for the test.

```
source ../../tester.bash
TYPESTATE_SPEC=spec.json
test_directory
```

### Skipping tests

You can skip tests by adding their name to `skip.txt`. Currently, you need to specify just the atomic directory, not the relative path (ideally it would be relative path to have non-unique atomic test names). Add a comment, prefixed with `#`, to describe why that test is skipped.

You can also set tests to be macOS-only by setting `MACOS_ONLY=1` in `test.bash`.

### Options

You can set environment variables to modify the test behavior. e.g.,

```
OUTPUT_DIR=out ./test.bash
```

Variables:

- `OUTPUT_DIR`: Set test directory. This is normally inside of a temporary directory and is immediately deleted after the test completes. Useful for debugging.
- `DRIVER_OPTIONS`: Specifies **additional** options to the driver.
- `ADDR`: Specifies debugger port and enables debugger attachment.

### Annotations

Annotation format for taint analysis:

```
'//!' name '!' 'source' or 'sink' ( '!' 'fn' or 'fp' )?

//!testing!source
//!testing!sink
//!testing!source!fn
//!testing!sink!fp
```

Annotation format for typestate analysis:

```
'//?' name '?' 'error' ( '!' 'fn' or 'fp' )?

//?FileOpenClose?error
//?FileOpenClose?error?fn
//?FileOpenClose?error?fp
```

Add Jira bug numbers for FNs and FPs.

### Writing models

It's quicker to add a `.swirl` file inside of the test case you want to write and test a new model for, than to recompile SWAN with the new `models.swirl` resource file. Once you know the model works, you can copy it to `models.swirl`.
