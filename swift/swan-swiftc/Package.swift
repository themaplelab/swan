// swift-tools-version:6.0

import PackageDescription

let package = Package(
  name: "swan-swiftc",
  products: [
    .executable(name: "swan-swiftc", targets: ["swan-swiftc"])
  ],
  dependencies: [
    .package(url: "https://github.com/mtynior/ColorizeSwift.git", from: "1.5.0"),
    .package(url: "https://github.com/apple/swift-argument-parser", from: "1.5.0"),
  ],
  targets: [
    .executableTarget(
      name: "swan-swiftc",
      dependencies: [
        .product(name: "ArgumentParser", package: "swift-argument-parser"),
        .product(name: "ColorizeSwift", package: "ColorizeSwift"),
      ]
    )
  ]
)
