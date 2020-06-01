### Running the shim

./xcodebuild-shim -xconfig-path /path_to_config/Config.xcconfig -project-path /path_to_config/Memory.xcodeproj -xcode-options='CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED="NO"'

### Running without shim

xcodebuild -xcconfig /path_to_config/Config.xcconfig -project /path_to_config/Memory.xcodeproj
