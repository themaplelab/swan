import Foundation

// ********************* CODEQL-BASED TESTS *********************

// These tests are modified from https://github.com/github/codeql
// https://github.com/github/codeql/blob/main/swift/ql/test/query-tests/Security/CWE-321/test.swift
// Swift's analysis for CWE-321
// GOOD / BAD annotations are CodeQL's annotations

// We modified getRandomArray() to return AES.randomIV(10).
// The original implementation of getRandomArray() was the same as
// CryptoSwift's randomIV().

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
  init(key: Array<UInt8>, padding: Padding) { }
	init(key: Array<UInt8>, blockMode: BlockMode) { }
	init(key: String, iv: String) { }
	init(key: String, iv: String, padding: Padding) { }
}

class HMAC: Cryptors
{
	init(key: Array<UInt8>) { }
	init(key: Array<UInt8>, variant: Variant) { }
	init(key: String) { }
	init(key: String, variant: Variant) { }
  enum Variant {
    case md5
    case sha1
    case sha2(SHA2.Variant)
    case sha3(SHA3.Variant)
  }
}

class ChaCha20: Cryptors
{
	init(key: Array<UInt8>, iv: Array<UInt8>) { }
	init(key: String, iv: String) { }
}

class Rabbit: Cryptors
{
	init(key: Array<UInt8>) { }
	init(key: String) { }
	init(key: Array<UInt8>, iv: Array<UInt8>?) { }
	init(key: String, iv: String) { }
}

class CBCMAC
{
	init(key: Array<UInt8>) { }
}

class CMAC
{
	init(key: Array<UInt8>) { }
}

class Poly1305
{
	init(key: Array<UInt8>) { }
}

protocol DigestType {}

class SHA2: DigestType {
  enum Variant {
    case sha224, sha256, sha384, sha512
  }
}

class SHA3: DigestType {
  enum Variant {
    case sha224, sha256, sha384, sha512, keccak224, keccak256, keccak384, keccak512
  }
}

protocol BlockMode { }

struct CBC: BlockMode {
  init() { } // doesn't exist in real API
	init(iv: Array<UInt8>) { }
}

protocol PaddingProtocol { }

enum Padding: PaddingProtocol {
  case noPadding, zeroPadding, pkcs7, pkcs5, eme_pkcs1v15, emsa_pkcs1v15, iso78164, iso10126
}

// Helper functions
func getConstantString() -> String {
  "this string is constant"
}

func getConstantArray() -> Array<UInt8> {
	[UInt8](getConstantString().utf8)
}

func getRandomArray() -> Array<UInt8> {
	AES.randomIV(10)
}

func arrayToString(_ arr: Array<UInt8>) -> String {
  String(bytes: arr, encoding: .utf8)!
}

// --- tests ---

// Excluding added SWAN annotations and modifications to types (to more
// closely match the real API), this function has not been modified
// from CodeQL's tests.
func codeql_test() {
	let key: Array<UInt8> = [0x2a, 0x3a, 0x80, 0x05, 0xaf, 0x46, 0x58, 0x2d, 0x66, 0x52, 0x10, 0xae, 0x86, 0xd3, 0x8e, 0x8f]
	let key2 = getConstantArray()
	let keyString = getConstantString()

	let randomArray = getRandomArray()
	let randomKey = getRandomArray()
	let randomKeyString = String(cString: getRandomArray())

	let blockMode = CBC()
	let padding = Padding.noPadding
	let variant = HMAC.Variant.sha2(.sha256)
	
	let iv = getRandomArray()
	let ivString = String(cString: iv)

	// AES test cases
	let ab1 = AES(key: key2, blockMode: blockMode, padding: padding) // BAD [NOT DETECTED] //$KEY$error
	let ab2 = AES(key: key2, blockMode: blockMode) // BAD [NOT DETECTED] //$KEY$error
	let ab3 = AES(key: keyString, iv: ivString) // BAD //$KEY$error
	let ab4 = AES(key: keyString, iv: ivString, padding: padding) // BAD //$KEY$error

	let ag1 = AES(key: randomKey, blockMode: blockMode, padding: padding) // GOOD
	let ag2 = AES(key: randomKey, blockMode: blockMode) // GOOD
	let ag3 = AES(key: randomKeyString, iv: ivString) // GOOD
	let ag4 = AES(key: randomKeyString, iv: ivString, padding: padding) // GOOD

	// HMAC test cases
	let hb1 = HMAC(key: key) // BAD //$KEY$error
	let hb2 = HMAC(key: key, variant: variant) // BAD //$KEY$error
	let hb3 = HMAC(key: keyString) // BAD //$KEY$error
	let hb4 = HMAC(key: keyString, variant: variant) // BAD //$KEY$error

	let hg1 = HMAC(key: randomKey) // GOOD
	let hg2 = HMAC(key: randomKey, variant: variant) // GOOD
	let hg3 = HMAC(key: randomKeyString) // GOOD
	let hg4 = HMAC(key: randomKeyString, variant: variant) // GOOD

	// ChaCha20 test cases
	let cb1 = ChaCha20(key: key, iv: iv) // BAD //$KEY$error
	let cb2 = ChaCha20(key: keyString, iv: ivString) // BAD //$KEY$error

	let cg1 = ChaCha20(key: randomKey, iv: iv) // GOOD
	let cg2 = ChaCha20(key: randomKeyString, iv: ivString) // GOOD

	// CBCMAC test cases
	let cmb1 = CBCMAC(key: key) // BAD //$KEY$error

	let cmg1 = CBCMAC(key: randomKey) // GOOD

	// CMAC test cases
	let cmacb1 = CMAC(key: key) // BAD //$KEY$error

	let cmacg1 = CMAC(key: randomKey) // GOOD

	// Poly1305 test cases
	let pb1 = Poly1305(key: key) // BAD //$KEY$error

	let pg1 = Poly1305(key: randomKey) // GOOD

	// Blowfish test cases
	let bb1 = Blowfish(key: key, blockMode: blockMode, padding: padding) // BAD //$KEY$error
	let bb2 = Blowfish(key: key, blockMode: blockMode) // BAD //$KEY$error
	let bb3 = Blowfish(key: keyString, iv: ivString) // BAD //$KEY$error
	let bb4 = Blowfish(key: keyString, iv: ivString, padding: padding) // BAD //$KEY$error

	let bg1 = Blowfish(key: randomKey, blockMode: blockMode, padding: padding) // GOOD
	let bg2 = Blowfish(key: randomKey, blockMode: blockMode) // GOOD
	let bg3 = Blowfish(key: randomKeyString, iv: ivString) // GOOD
	let bg4 = Blowfish(key: randomKeyString, iv: ivString, padding: padding) // GOOD

	// Rabbit
	let rb1 = Rabbit(key: key) // BAD //$KEY$error
	let rb2 = Rabbit(key: keyString) // BAD //$KEY$error
	let rb3 = Rabbit(key: key, iv: iv) // BAD //$KEY$error
	let rb4 = Rabbit(key: keyString, iv: ivString) // BAD //$KEY$error

	let rg1 = Rabbit(key: randomKey) // GOOD
	let rg2 = Rabbit(key: randomKeyString) // GOOD
	let rg3 = Rabbit(key: randomKey, iv: iv) // GOOD
	let rg4 = Rabbit(key: randomKeyString, iv: ivString) // GOOD
}

// ********************* SWAN TESTS *********************

func test_r3_simple_violation() throws {
  let key = getConstantArray()
  let randomArray = getRandomArray()
  let keyString = getConstantString()
  let blockMode = CBC(iv: randomArray)
  let padding = Padding.noPadding
  let variant = HMAC.Variant.sha2(.sha256)
  let iv = AES.randomIV(10)
  let ivString = arrayToString(iv)
  
  // Violation SHOULD be detected only for key argument
  
  // AES
  _ = try AES(key: key, blockMode: blockMode, padding: padding) //$KEY$error
  _ = try AES(key: key, blockMode: blockMode) //$KEY$error
  _ = try AES(key: keyString, iv: ivString) //$KEY$error
  _ = try AES(key: keyString, iv: ivString, padding: padding) //$KEY$error

  // HMAC
  _ = HMAC(key: key) //$KEY$error
  _ = HMAC(key: key, variant: variant) //$KEY$error
  _ = try HMAC(key: keyString) //$KEY$error
  _ = try HMAC(key: keyString, variant: variant) //$KEY$error
  
  // ChaCha20
  _ = try ChaCha20(key: key, iv: iv) //$KEY$error
  _ = try ChaCha20(key: keyString, iv: ivString) //$KEY$error
  
  // CBCMAC
  _ = try CBCMAC(key: key) //$KEY$error
  
  // CMAC
  _ = try CMAC(key: key) //$KEY$error

  // Poly1305
  _ = Poly1305(key: key) //$KEY$error
  
  // Blowfish
  _ = try Blowfish(key: keyString, iv: ivString) //$KEY$error
  _ = try Blowfish(key: keyString, iv: ivString, padding: padding) //$KEY$error
  _ = try Blowfish(key: key, blockMode: blockMode, padding: padding) //$KEY$error
  _ = try Blowfish(key: key, padding: padding) //$KEY$error
  
  // Rabbit
  _ = try Rabbit(key: key) //$KEY$error
  _ = try Rabbit(key: keyString) //$KEY$error
  _ = try Rabbit(key: key, iv: iv) //$KEY$error
  _ = try Rabbit(key: keyString, iv: ivString) //$KEY$error

  // Concat
  let concatKey = "con"+"cat"+"me"
  _ = try AES(key: concatKey, iv: ivString) //$KEY$error
}

func test_r3_simple_no_violation() throws {
  let key = getRandomArray()
  let randomArray = getRandomArray()
  let keyString = arrayToString(AES.randomIV(10))
  let blockMode = CBC(iv: randomArray)
  let padding = Padding.noPadding
  let variant = HMAC.Variant.sha2(.sha256)
  let iv = AES.randomIV(10)
  let ivString = arrayToString(iv)
  
  // Violation SHOULD NOT be detected
  
  // AES
  _ = try AES(key: key, blockMode: blockMode, padding: padding)
  _ = try AES(key: key, blockMode: blockMode)
  _ = try AES(key: keyString, iv: ivString)
  _ = try AES(key: keyString, iv: ivString, padding: padding)
  
  // HMAC
  _ = HMAC(key: key, variant: variant)
  _ = try HMAC(key: keyString)
  _ = try HMAC(key: keyString, variant: variant)
  _ = HMAC(key: key)
  
  // ChaCha20
  _ = try ChaCha20(key: key, iv: iv)
  _ = try ChaCha20(key: keyString, iv: ivString)
  
  // CBCMAC
  _ = try CBCMAC(key: key)
  
  // CMAC
  _ = try CMAC(key: key)

  // Poly1305
  _ = Poly1305(key: key)
  
  // Blowfish
  _ = try Blowfish(key: keyString, iv: ivString)
  _ = try Blowfish(key: keyString, iv: ivString, padding: padding)
  _ = try Blowfish(key: key, blockMode: blockMode, padding: padding)
  _ = try Blowfish(key: key, padding: padding)
  
  // Rabbit
  _ = try Rabbit(key: key)
  _ = try Rabbit(key: keyString)
  _ = try Rabbit(key: key, iv: iv)
  _ = try Rabbit(key: keyString, iv: ivString)

  // Concat
  let concatKey = "con"+"cat"+keyString+"me"
  _ = try AES(key: concatKey, iv: ivString) //$KEY$error$fp
}
