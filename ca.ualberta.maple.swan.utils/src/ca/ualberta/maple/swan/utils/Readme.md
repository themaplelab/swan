### Running the shim

./xcodebuild-shim -xconfig-path /path_to_config/Config.xcconfig -project-path /path_to_config/Memory.xcodeproj -xcode-options='CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED="NO"'  -output-path ~/check/

### Running without shim

xcodebuild -xcconfig /path_to_config/Config.xcconfig -project /path_to_config/Memory.xcodeproj

### Important Note
xcconfig file produces two SIL versions in -project-path build folder for Arm64 and Arm7
The -output-path copies the SIL files from Arm64 directory and not Arm7

### How to create an Xcconfig file
