### Running the shim

./xcodebuild-shim -xconfig-path /path_to_config/Config.xcconfig -project-path /path_to_config/Memory.xcodeproj -xcode-options='CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED="NO"'  -output-path ~/output-shim-path/

### Running without shim

xcodebuild -xcconfig /path_to_config/Config.xcconfig -project /path_to_config/Memory.xcodeproj

### Important Note
xcconfig file produces two SIL versions in -project-path build folder for Armv64 and Armv7
The -output-path copies the SIL files from Armv64 directory and not Armv7

### How to create an Xcconfig file?
In your project hierarchy, right-click and select new file. Search for “Configuration Settings File”, select it, and then select next. Name the file “Config”, or anything you desire really. Make sure no targets are selected (as you don’t want to add this file to your application bundle, and its not being compiled). Then select create.

You’ll have an empty file created, albeit with a few comments at the top. This file is used to handle key-value pairs of build settings.

We’ll now want to make sure all of our configurations are using this configuration file at their base, so that all their build settings in the Xcode build settings pane will default to these values.

Select your project in the project hierarchy, then select the project in the left pane again. You should see all of your configurations.

Expand each configuration, and next to where it shows the Xcode project icon, and your project name it will say “None”. Select this drop-down and change it to “Config” (or whatever you named your configuration file).

This will set the configuration file to be used at the project level for each configuration.

More info: https://joshua.codes/manage-your-build-settings-with-xcconfigs/


#### After xcconfig creation, add the following to config file
// MARK: Per-Configuration Properties
OTHER_SWIFT_FLAGS = $(inherited) -Xfrontend -gsil -Xllvm -sil-print-debuginfo
