import Foundation

// ********************* CODEQL-BASED TESTS *********************

// These tests are modified from https://github.com/github/codeql
// https://github.com/github/codeql/blob/main/swift/ql/test/query-tests/Security/CWE-327/test.swift
// Swift's analysis for CWE-327
// GOOD / BAD annotations are CodeQL's annotations

// --- stubs ---

// These stubs roughly follows the same structure as classes from CryptoSwift
// Most of these are from CodeQL's tests, but may be slightly modified to
// more accuratly match the real API

protocol Cryptors: AnyObject {
  static func randomIV(_ blockSize: Int) -> Array<UInt8>
}

extension Cryptors {
  static func randomIV(_ count: Int) -> Array<UInt8> {
    (0..<count).map({ _ in UInt8.random(in: 0...UInt8.max) })
  }
}

class AES: Cryptors
{
	init(key: Array<UInt8>, blockMode: BlockMode, padding: Padding) { }
	init(key: Array<UInt8>, blockMode: BlockMode) { }
	init(key: String, iv: String) { }
	init(key: String, iv: String, padding: Padding) { }
}

class Blowfish: Cryptors
{
	init(key: Array<UInt8>, blockMode: BlockMode, padding: Padding) { }
	init(key: Array<UInt8>, blockMode: BlockMode) { }
	init(key: String, iv: String) { }
	init(key: String, iv: String, padding: Padding) { }
}

protocol BlockMode { }

struct ECB: BlockMode {
  init() {}
}

struct CBC: BlockMode {
  init() {}
  init(iv: Array<UInt8>) {}
}

protocol PaddingProtocol { }

enum Padding: PaddingProtocol {
  case noPadding, zeroPadding, pkcs7, pkcs5, eme_pkcs1v15, emsa_pkcs1v15, iso78164, iso10126
}

// Helper functions

func getRandomArray() -> Array<UInt8> {
	AES.randomIV(10)
}

func getECBBlockMode() -> BlockMode {
	return ECB()
}

func getCBCBlockMode() ->  BlockMode {
	return CBC()
}

// --- tests ---

// Excluding added SWAN annotations, this function has not been modified
// from CodeQL's tests
func codeql_test() {
	let key: Array<UInt8> = getRandomArray() // changed from static array to avoid constant key errors
	let ecb = ECB()
	let cbc = CBC()
	let padding = Padding.noPadding

	// AES test cases
	let ab1 = AES(key: key, blockMode: ecb, padding: padding) // BAD //$ECB$error
	let ab2 = AES(key: key, blockMode: ecb) // BAD //$ECB$error
	let ab3 = AES(key: key, blockMode: ECB(), padding: padding) // BAD //$ECB$error
	let ab4 = AES(key: key, blockMode: ECB()) // BAD //$ECB$error
	let ab5 = AES(key: key, blockMode: getECBBlockMode(), padding: padding) // BAD //$ECB$error
	let ab6 = AES(key: key, blockMode: getECBBlockMode()) // BAD //$ECB$error

	let ag1 = AES(key: key, blockMode: cbc, padding: padding) // GOOD
	let ag2 = AES(key: key, blockMode: cbc) // GOOD
	let ag3 = AES(key: key, blockMode: CBC(), padding: padding) // GOOD
	let ag4 = AES(key: key, blockMode: CBC()) // GOOD
	let ag5 = AES(key: key, blockMode: getCBCBlockMode(), padding: padding) // GOOD
	let ag6 = AES(key: key, blockMode: getCBCBlockMode()) // GOOD

	// Blowfish test cases
	let bb1 = Blowfish(key: key, blockMode: ecb, padding: padding) // BAD //$ECB$error
	let bb2 = Blowfish(key: key, blockMode: ECB(), padding: padding) // BAD //$ECB$error
	let bb3 = Blowfish(key: key, blockMode: getECBBlockMode(), padding: padding) // BAD //$ECB$error

	let bg1 = Blowfish(key: key, blockMode: cbc, padding: padding) // GOOD
	let bg2 = Blowfish(key: key, blockMode: CBC(), padding: padding) // GOOD
	let bg3 = Blowfish(key: key, blockMode: getCBCBlockMode(), padding: padding) // GOOD
}

// ********************* SWAN TESTS *********************

func test_r1_simple_violation() throws {
  let key = getRandomArray()
  let padding = Padding.noPadding
  let blockMode = ECB()
  
  // Violation SHOULD be detected only for blockMode argument
  _ = try AES(key: key, blockMode: blockMode, padding: padding) //$ECB$error
  _ = try AES(key: key, blockMode: blockMode) //$ECB$error
  _ = try Blowfish(key: key, blockMode: blockMode, padding: padding) //$ECB$error
}

func test_r1_simple_no_violation() throws {
  let key = getRandomArray()
  let padding = Padding.noPadding
  let blockMode = CBC(iv: getRandomArray())
  
  // Violation SHOULD NOT be detected
  _ = try AES(key: key, blockMode: blockMode, padding: padding)
  _ = try AES(key: key, blockMode: blockMode)
  _ = try Blowfish(key: key, blockMode: blockMode, padding: padding)
}
