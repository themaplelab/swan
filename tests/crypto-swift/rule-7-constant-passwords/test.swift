import Foundation

// ********************* CODEQL-BASED TESTS *********************

// These tests are modified from https://github.com/github/codeql
// https://github.com/github/codeql/blob/main/swift/ql/test/query-tests/Security/CWE-259/test.swift
// Swift's analysis for CWE-259
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

class AES: Cryptors { }

class HMAC: Cryptors
{
  enum Variant {
    case md5
    case sha1
    case sha2(SHA2.Variant)
    case sha3(SHA3.Variant)
  }
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

enum PKCS5 { }
extension PKCS5 {
  struct PBKDF1 {
	  init(password: Array<UInt8>, salt: Array<UInt8>, variant: Variant = .sha1, iterations: Int = 4096, keyLength: Int? = nil) { }
    enum Variant {
      case md5
      case sha1
      case sha2(SHA2.Variant)
      case sha3(SHA3.Variant)
    }
  }

  struct PBKDF2 {
	  init(password: Array<UInt8>, salt: Array<UInt8>, iterations: Int = 4096, keyLength: Int? = nil, variant: HMAC.Variant = HMAC.Variant.sha2(.sha256)) { }
  }
}

struct HKDF {
	init(password: Array<UInt8>, salt: Array<UInt8>? = nil, info: Array<UInt8>? = nil, keyLength: Int? = nil, variant: HMAC.Variant = HMAC.Variant.sha2(.sha256)) { }
}

final class Scrypt {
	init(password: Array<UInt8>, salt: Array<UInt8>, dkLen: Int, N: Int, r: Int, p: Int) { }
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

func unknownCondition() -> Bool {
  Bool.random()
}

func getLowIterationCount() -> Int { return 99999 }

func getEnoughIterationCount() -> Int { return 120120 }

// --- tests ---

// Excluding added SWAN annotations and modifications to types (to more
// closely match the real API), this function has not been modified
// from CodeQL's tests.
func codeql_test() {
	let constantPassword: Array<UInt8> = [0x2a, 0x3a, 0x80, 0x05, 0xaf, 0x46, 0x58, 0x2d, 0x66, 0x52, 0x10, 0xae, 0x86, 0xd3, 0x8e, 0x8f]
	let constantStringPassword = getConstantArray()
	let randomPassword = getRandomArray()
	let randomArray = getRandomArray()
	let variant = HMAC.Variant.sha2(.sha256)
	let iterations = 120120
	
	// HKDF test cases
	let hkdfb1 = HKDF(password: constantPassword, salt: randomArray, info: randomArray, keyLength: 0, variant: variant) // BAD //$PASSWORD$error
	let hkdfb2 = HKDF(password: constantStringPassword, salt: randomArray, info: randomArray, keyLength: 0, variant: variant) // BAD [NOT DETECTED] //$PASSWORD$error
	let hkdfg1 = HKDF(password: randomPassword, salt: randomArray, info: randomArray, keyLength: 0, variant: variant) // GOOD
	
  // PBKDF1 test cases
	let pbkdf1b1 = PKCS5.PBKDF1(password: constantPassword, salt: randomArray, iterations: iterations, keyLength: 0) // BAD //$PASSWORD$error
	let pbkdf1b2 = PKCS5.PBKDF1(password: constantStringPassword, salt: randomArray, iterations: iterations, keyLength: 0) // BAD [NOT DETECTED] //$PASSWORD$error
	let pbkdf1g1 = PKCS5.PBKDF1(password: randomPassword, salt: randomArray, iterations: iterations, keyLength: 0) // GOOD

	// PBKDF2 test cases
	let pbkdf2b1 = PKCS5.PBKDF2(password: constantPassword, salt: randomArray, iterations: iterations, keyLength: 0) // BAD //$PASSWORD$error
	let pbkdf2b2 = PKCS5.PBKDF2(password: constantStringPassword, salt: randomArray, iterations: iterations, keyLength: 0) // BAD [NOT DETECTED] //$PASSWORD$error
	let pbkdf2g1 = PKCS5.PBKDF2(password: randomPassword, salt: randomArray, iterations: iterations, keyLength: 0) // GOOD
	
  // Scrypt test cases
	let scryptb1 = Scrypt(password: constantPassword, salt: randomArray, dkLen: 64, N: 16384, r: 8, p: 1) // BAD //$PASSWORD$error
	let scryptb2 = Scrypt(password: constantStringPassword, salt: randomArray, dkLen: 64, N: 16384, r: 8, p: 1) // BAD [NOT DETECTED] //$PASSWORD$error
	let scryptg1 = Scrypt(password: randomPassword, salt: randomArray, dkLen: 64, N: 16384, r: 8, p: 1) // GOOD
}

// ********************* SWAN TESTS *********************

func test_r7_simple_violation() throws {
  let password = getConstantArray()
  let iterations = getEnoughIterationCount()
  let randomArray = getRandomArray()

  // Violation SHOULD be detected only for password argument
  
  _ = try HKDF(password: password, salt: randomArray, info: randomArray, keyLength: 0, variant: HMAC.Variant.sha2(.sha256)) //$PASSWORD$error
  _ = try PKCS5.PBKDF1(password: password, salt: randomArray, iterations: iterations, keyLength: 0) //$PASSWORD$error
  _ = try PKCS5.PBKDF2(password: password, salt: randomArray, iterations: iterations, keyLength: 0) //$PASSWORD$error
  _ = try Scrypt(password: password, salt: randomArray, dkLen: 64, N: 16384, r: 8, p: 1) //$PASSWORD$error
}

func test_r7_from_string_violation() throws {
  let constantString = getConstantString()
  let password = getConstantArray()
  let randomArray = getRandomArray()

  // Violation SHOULD be detected only for password argument
  _ = try HKDF(password: password, salt: randomArray, info: randomArray, keyLength: 0, variant: HMAC.Variant.sha2(.sha256)) //$PASSWORD$error
}

func test_r7_multiple_values_violation() throws {
  let constantString = getConstantString()
  let password = unknownCondition() ? getConstantArray() : getRandomArray()
  let randomArray = getRandomArray()

  // Violation SHOULD be detected only for password argument
  _ = try HKDF(password: password, salt: randomArray, info: randomArray, keyLength: 0, variant: HMAC.Variant.sha2(.sha256)) //$PASSWORD$error
}

func test_r7_simple_no_violation() throws {
  let password = getRandomArray()
  let iterations = getEnoughIterationCount()
  let randomArray = getRandomArray()
  
  // Violation SHOULD NOT be detected
  _ = try HKDF(password: password, salt: randomArray, info: randomArray, keyLength: 0, variant: HMAC.Variant.sha2(.sha256))
  _ = try PKCS5.PBKDF1(password: password, salt: randomArray, iterations: iterations, keyLength: 0)
  _ = try PKCS5.PBKDF2(password: password, salt: randomArray, iterations: iterations, keyLength: 0)
  _ = try Scrypt(password: password, salt: randomArray, dkLen: 64, N: 16384, r: 8, p: 1)
}
