<img src="https://karimali.ca/resources/images/projects/swan.png" width="150">

# :new: SWAN

This branch contains the new generation of the SWAN framework. **It is still WIP and not ready for use. ​**:construction:

###  Summary

We have completely redesigned SWAN. It now parses plain-text that can either be dumped with `xcodebuild` or `swiftc`. Previously, we hooked into the Swift compiler which created many build problems and added complexity. We've developed a new IR, called *SWIRL*, that is simple, well documented, and easy to understand. Any analysis engine should be able to analyze SWIRL without having to handle complex semantics. We are currently working on integrating SWIRL into [SPDS](https://github.com/CodeShield-Security/SPDS).

### Relevant Wiki pages

- [SWIRL](https://github.com/themaplelab/swan/wiki/SWIRL)
- [SWIRLGen: SIL to SWIRL Compilation](https://github.com/themaplelab/swan/wiki/SWIRLGen:-SIL-to-SWIRL-Compilation)

## Developing

```
git clone git@github.com:themaplelab/swan.git -b spds --recurse-submodules
```

Open the cloned repo in IntelliJ. Be sure to select *Import as Gradle Project*.

Add your github username and [personal access token](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token) (with read:packages) to ca.ualberta.maple.swan.spds/build.gradle. This is required for adding SPDS as a dependency. Do **not** push this token.

Run the *SWIRL Tests* and *SIL Tests* configurations to test everything works.

### Repository layout

- ca.ualberta.maple.swan.client
  - Empty driver
- ca.ualberta.maple.swan.ir
  - Contains SWIRL
- ca.ualberta.maple.swan.parser
  - Contains SIL printer/parser
- ca.ualberta.maple.swan.spds
  - Contains SPDS/Boomerang data structures for SWIRL
- ca.ualberta.maple.swan.viewer
  - JVM-side driver for SwanViewer
- SwanViewer
  - Swift tool that displays Swift, SIL, and SWIRL side-by-side
- swan-xcodebuild
  - Swift CLI wrapper for `xcodebuild` that extracts SIL from build output
- utils
  - SIL dumping scripts, only used for testing
