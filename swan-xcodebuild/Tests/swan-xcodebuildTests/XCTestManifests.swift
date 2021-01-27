import XCTest

#if !canImport(ObjectiveC)
public func allTests() -> [XCTestCaseEntry] {
    return [
        testCase(swan_xcodebuildTests.allTests),
    ]
}
#endif
