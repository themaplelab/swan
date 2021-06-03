// swift-tools-version:5.1

import PackageDescription

let package = Package(
    name: "swan-swiftc",
    products: [
        .executable(name: "swan-swiftc", targets: ["swan-swiftc"])
    ],
    dependencies: [
        .package(url: "https://github.com/mtynior/ColorizeSwift.git", from: "1.5.0"),
        .package(url: "https://github.com/apple/swift-argument-parser", from: "0.0.1")
    ],
    targets: [
        .target(
            name: "swan-swiftc",
            dependencies: ["ArgumentParser", "ColorizeSwift"])
    ]
)
