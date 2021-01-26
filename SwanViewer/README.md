# SWANViewer

## Usage

SWANViewer is a tool that displays Swift, SIL, and SWIRL side-by-side. To use SWANViewer, open `SwanViewer/SwanViewer.xcodeproj` with Xcode and select *Run*. Then, enter Swift code in the first column editor, and then press the *Build* button. You can also configure the build arguments. There are switches that allow you to hide specific editors. Clicking on a line in any of the three editors will highlight source information in the other editors, when available.

Note: SWANViewer will prompt you for access to your *Documents* because it uses a temporary (hidden) directory stored there. It does not read or write non-temporary files to your *Documents* directory.

## Updating

SWANViewer relies on the jar produced by ca.ualberta.maple.swan.viewer. This jar should be updated when the other packages change.

First, run `./gradlew shadowJar` in the root directory.
Then, copy `ca.ualberta.maple.swan.viewer/build/libs/ca.ualberta.maple.swan.viewer-all.jar` to `SwanViewer/viewer.jar`. 
