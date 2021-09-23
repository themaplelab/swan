# Testing

The testing scripts are WIP and we are adding features as we need them.

If you want to test sanitizers, you must enable path-tracking (`-p`). Note that path-tracking can be *very* slow.

## Adding tests

If you want to add a new set of tests:

1. Create a new directory in a fitting spot with a good name that justifies having a separate directory for the tests
2. Create a `test.bash` in the new directory with the following contents (replace `<relative_path_to_tester.bash>` and make sure it is executable)

```
#!/usr/bin/env bash

# describe what this set of tests is for here

source <relative_path_to_tester.bash>
test_directories
```

If you want to add a new test to an existing group

1. Create a new directory with a descriptive name under the appropriate directory
2. Add a `test.bash` with the following contents (replace `<relative_path_to_tester.bash>`, and make sure it is executable

```
#!/usr/bin/env bash

# describe what this test is for here

source <relative_path_to_tester.bash>
test_directory
```

Add your test.

### Regular Tests

Put your code into `test.swift`. 

**Taint Analysis**

If you want to use the default taint configuration, add `TAINT_SPEC=DEFAULT` to `test.bash`. Otherwise, create a new JSON file with your spec and set `TAINT_SPEC=<my_spec_file>.json`. If you want to use one of the specifications in `specifications/` (from SWAN root), you can use `TAINT_SPEC_ROOT=<relative_path_from_specifications/>`.

**Typestate Analysis**

Specify `TYPESTATE_SPEC=<my_spec_file>.json`. If you want to use one of the specifications in `specifications/` (from SWAN root), you can use `TYPESTATE_SPEC_ROOT=<relative_path_from_specifications/>`.

You can use taint analysis and typestate analysis simultaneously.

### Swift Package Manager Tests

Add your code to `Sources/main.swift` (single source file only) and `Package.swift`. You must configure Package.swift to have the following target options. This only works for Swift 5+.

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
TAINT_SPEC=DEFAULT
import "ColorizeSwift.sil"
test_directory
```

### Skipping tests

You can skip tests by adding their name to `skip.txt`. Currently, you need to specify just the atomic directory, not the relative path (ideally it would be relative path to have non-unique atomic test names). Add a comment, prefixed with `#`, to describe why that test is skipped.

You can also set tests to be macOS-only by setting `MACOS_ONLY=1` in `test.bash`.

### Intentional Failure Testing

You can create tests that you expect to fail by setting the `EXPECTED_ERROR_MESSAGE="..."` in `test.bash`. This string is directly given to `grep` (against the error output), so it's somewhat limited. 

### Environment Variables

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
