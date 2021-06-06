// swift-tools-version:5.4

import PackageDescription

let package = Package(
    name: "test",
    products: [
        .executable(name: "test", targets: ["test"])
    ],
    dependencies: [
        .package(url: "https://github.com/mtynior/ColorizeSwift.git", from: "1.5.0")
    ],
    targets: [
        .executableTarget(
            name: "test",
            dependencies: ["ColorizeSwift"],
            swiftSettings: [
              .unsafeFlags([
                "-Xfrontend",
                "-gsil",
                "-Xllvm",
                "-sil-print-debuginfo",
                "-Xllvm",
                "-sil-print-before=SerializeSILPass"
                ])
            ])
    ]
)
