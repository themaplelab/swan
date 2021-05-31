## Testing

### Adding tests

If you want to add a new group (directory) of tests:

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
3. Add your test code under `test.swift` (you must use this name)

### Skipping tests

You can skip tests by adding their name to `skip.txt`. Currently, you need to specify just the atomic directory, not the relative path (ideally it would be relative path to have non-unique atomic test names). Add a comment, prefixed with `#`, to describe why that test is skipped.

### Future plans

This testing infrastructure is WIP. We plan to add the ability for tests to override settings such as the analysis specification and SWAN options.
