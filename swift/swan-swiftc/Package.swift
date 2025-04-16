// swift-tools-version:5.8.1

import PackageDescription

let package = Package(
    name: "swan-swiftc",
    products: [
        .executable(name: "swan-swiftc", targets: ["swan-swiftc"])
    ],
    dependencies: [
        .package(url: "https://github.com/mtynior/ColorizeSwift.git", exact: "1.6.0"),
        .package(url: "https://github.com/apple/swift-argument-parser", exact: "0.1.0")
    ],
    targets: [
        .executableTarget(
            name: "swan-swiftc",
            dependencies: [
                .product(name: "ArgumentParser", package: "swift-argument-parser"),
                "ColorizeSwift"
            ]
        )
    ]
)
