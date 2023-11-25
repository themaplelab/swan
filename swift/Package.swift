// swift-tools-version:5.1

import PackageDescription

let package = Package(
    name: "swan",
    products: [
        .executable(name: "swan-swiftc", targets: ["swan-swiftc"]),
        .executable(name: "swan-xcodebuild", targets: ["swan-xcodebuild"]),
        .executable(name: "swan-xcodebuildlog2sil", targets: ["swan-xcodebuildlog2sil"]),
        .library(name: "SwanSwiftBuildLogParser", type: .static, targets: ["SwanSwiftBuildLogParser"])
    ],
    dependencies: [
        .package(url: "https://github.com/mtynior/ColorizeSwift.git", from: "1.5.0"),
        .package(url: "https://github.com/apple/swift-argument-parser", from: "0.0.1")
    ],
    targets: [
        .target(
            name: "swan-swiftc",
            dependencies: ["ArgumentParser", "ColorizeSwift"]),
        .target(
            name: "swan-xcodebuild",
            dependencies: ["ArgumentParser", "ColorizeSwift", "SwanSwiftBuildLogParser"]),
        .target(
            name: "swan-xcodebuildlog2sil",
            dependencies: ["ArgumentParser", "ColorizeSwift", "SwanSwiftBuildLogParser"]),
        .target(
            name: "SwanSwiftBuildLogParser")
    ]
)
