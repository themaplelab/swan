// swift-tools-version:5.1

import PackageDescription

let package = Package(
    name: "swan-xcodebuild",
    products: [
        .executable(name: "swan-xcodebuild", targets: ["swan-xcodebuild"])
    ],
    dependencies: [
        .package(url: "https://github.com/mtynior/ColorizeSwift.git", from: "1.5.0"),
        .package(url: "https://github.com/apple/swift-argument-parser", from: "0.0.1")
    ],
    targets: [
        .target(
            name: "swan-xcodebuild",
            dependencies: ["ArgumentParser", "ColorizeSwift"])
    ]
)
