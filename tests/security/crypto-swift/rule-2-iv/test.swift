import Foundation

// ********************* CODEQL-BASED TESTS *********************

// These tests are modified from https://github.com/github/codeql
// https://github.com/github/codeql/blob/main/swift/ql/test/query-tests/Security/CWE-1204/test.swift
// Swift's analysis for CWE-1204
// GOOD / BAD annotations are CodeQL's annotations

// SWAN is more strict than CodeQL because SWAN insists the user to use
// CryptoSwift.Cryptors.randomIV() to create an IV instead of using
// a custom random array. CodeQL only complains if the IV is
// created statically, but SWAN complains if it is not created randomly.
// Therefore, SWAN would report an error for every
// CodeQL GOOD annotation, but we modified getRandomArray() to
// return AES.randomIV(10). Furthermore, the original implementation of
// getRandomArray() was the same as CryptoSwift's randomIV().

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

protocol BlockMode { }

struct ECB: BlockMode {
  init() {}
}

struct CBC: BlockMode {
  init() { } // doesn't exist in real API
	init(iv: Array<UInt8>) { }
}

struct CFB: BlockMode {
	enum SegmentSize: Int {
		case cfb8 = 1
		case cfb128 = 16
	}

	init(iv: Array<UInt8>, segmentSize: SegmentSize = .cfb128) { }
}

final class GCM: BlockMode {
	enum Mode { case combined, detached }
	init(iv: Array<UInt8>, additionalAuthenticatedData: Array<UInt8>? = nil, tagLength: Int = 16, mode: Mode = .detached) { }
	convenience init(iv: Array<UInt8>, authenticationTag: Array<UInt8>, additionalAuthenticatedData: Array<UInt8>? = nil, mode: Mode = .detached) { 
		self.init(iv: iv, additionalAuthenticatedData: additionalAuthenticatedData, tagLength: authenticationTag.count, mode: mode)
	}
}

struct OFB: BlockMode {
	init(iv: Array<UInt8>) { }
}

struct PCBC: BlockMode {
	init(iv: Array<UInt8>) { }
}

typealias StreamMode = BlockMode

struct CCM: StreamMode {
	init(iv: Array<UInt8>, tagLength: Int, messageLength: Int, additionalAuthenticatedData: Array<UInt8>? = nil) { }
	init(iv: Array<UInt8>, tagLength: Int, messageLength: Int, authenticationTag: Array<UInt8>, additionalAuthenticatedData: Array<UInt8>? = nil) { }
}

struct CTR: StreamMode {
	init(iv: Array<UInt8>, counter: Int = 0) { }
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

func getUnknownArray() -> Array<UInt8> {
  [0x2a, 0x3a, 0x80, 0x05, 0xaf, 0x46, 0x58, 0x2d, 0x66, 0x52, 0x10, 0xae, 0x86, 0xd3, 0x8e, 0x8f]
}

// --- tests ---

// Excluding added SWAN annotations, this function has not been modified
// from CodeQL's tests
func codeql_test() {
	let iv: Array<UInt8> = [0x2a, 0x3a, 0x80, 0x05, 0xaf, 0x46, 0x58, 0x2d, 0x66, 0x52, 0x10, 0xae, 0x86, 0xd3, 0x8e, 0x8f]
	let iv2 = getConstantArray()
	let ivString = getConstantString()

	let randomArray = getRandomArray()
	let randomIv = getRandomArray()
	let randomIvString = String(cString: getRandomArray())

	let padding = Padding.noPadding
	let key = getRandomArray()
	let keyString = String(cString: key)

	// AES test cases
	let ab1 = AES(key: keyString, iv: ivString) // BAD //$IV$error
	let ab2 = AES(key: keyString, iv: ivString, padding: padding) // BAD //$IV$error
	let ag1 = AES(key: keyString, iv: randomIvString) // GOOD 
	let ag2 = AES(key: keyString, iv: randomIvString, padding: padding) // GOOD 

	// ChaCha20 test cases
	let cb1 = ChaCha20(key: keyString, iv: ivString) // BAD //$IV$error
	let cg1 = ChaCha20(key: keyString, iv: randomIvString) // GOOD 

	// Blowfish test cases
	let bb1 = Blowfish(key: keyString, iv: ivString) // BAD //$IV$error
	let bb2 = Blowfish(key: keyString, iv: ivString, padding: padding) // BAD //$IV$error
	let bg1 = Blowfish(key: keyString, iv: randomIvString) // GOOD 
	let bg2 = Blowfish(key: keyString, iv: randomIvString, padding: padding) // GOOD 

	// Rabbit
	let rb1 = Rabbit(key: key, iv: iv) // BAD //$IV$error
	let rb2 = Rabbit(key: key, iv: iv2) // BAD [NOT DETECTED] //$IV$error
	let rb3 = Rabbit(key: keyString, iv: ivString) // BAD //$IV$error
	let rg1 = Rabbit(key: key, iv: randomIv) // GOOD 
	let rg2 = Rabbit(key: keyString, iv: randomIvString) // GOOD 

	// CBC
	let cbcb1 = CBC(iv: iv) // BAD //$IV$error
	let cbcg1 = CBC(iv: randomIv) // GOOD 

	// CFB
	let cfbb1 = CFB(iv: iv) // BAD //$IV$error
	let cfbb2 = CFB(iv: iv, segmentSize: CFB.SegmentSize.cfb8) // BAD //$IV$error
	let cfbg1 = CFB(iv: randomIv) // GOOD 
	let cfbg2 = CFB(iv: randomIv, segmentSize: CFB.SegmentSize.cfb8) // GOOD 

	// GCM
	let cgmb1 = GCM(iv: iv) // BAD //$IV$error
	let cgmb2 = GCM(iv: iv, additionalAuthenticatedData: randomArray, tagLength: 8, mode: GCM.Mode.combined) // BAD //$IV$error
	let cgmb3 = GCM(iv: iv, authenticationTag: randomArray, additionalAuthenticatedData: randomArray, mode: GCM.Mode.combined) // BAD //$IV$error
	let cgmg1 = GCM(iv: randomIv) // GOOD 
	let cgmg2 = GCM(iv: randomIv, additionalAuthenticatedData: randomArray, tagLength: 8, mode: GCM.Mode.combined) // GOOD 
	let cgmg3 = GCM(iv: randomIv, authenticationTag: randomArray, additionalAuthenticatedData: randomArray, mode: GCM.Mode.combined) // GOOD 

	// OFB
	let ofbb1 = OFB(iv: iv) // BAD //$IV$error
	let ofbg1 = OFB(iv: randomIv) // GOOD 

	// PCBC
	let pcbcb1 = PCBC(iv: iv) // BAD //$IV$error
	let pcbcg1 = PCBC(iv: randomIv) // GOOD 

	// CCM
	let ccmb1 = CCM(iv: iv, tagLength: 0, messageLength: 0, additionalAuthenticatedData: randomArray) // BAD //$IV$error
	let ccmb2 = CCM(iv: iv, tagLength: 0, messageLength: 0, authenticationTag: randomArray, additionalAuthenticatedData: randomArray) // BAD //$IV$error
	let ccmg1 = CCM(iv: randomIv, tagLength: 0, messageLength: 0, additionalAuthenticatedData: randomArray) // GOOD 
	let ccmg2 = CCM(iv: randomIv, tagLength: 0, messageLength: 0, authenticationTag: randomArray, additionalAuthenticatedData: randomArray) // GOOD 

	// CTR
	let ctrb1 = CTR(iv: iv) // BAD //$IV$error
	let ctrb2 = CTR(iv: iv, counter: 0) // BAD //$IV$error
	let ctrg1 = CTR(iv: randomIv) // GOOD 
	let ctrg2 = CTR(iv: randomIv, counter: 0) // GOOD 
}

// ********************* SWAN TESTS *********************

func test_r2_simple_violation() throws {
  let iv = getConstantArray()
  let ivString = arrayToString(iv)
  let key = getRandomArray()
  let keyString = arrayToString(key)
  let randomArray = getRandomArray()
  
  // Violation SHOULD be detected only for iv argument
  
  // BlockModes
  _ = CBC(iv: iv) //$IV$error
  _ = CFB(iv: iv) //$IV$error
  _ = CFB(iv: iv, segmentSize: CFB.SegmentSize.cfb8) //$IV$error
  _ = CCM(iv: iv, tagLength: 0, messageLength: 0, additionalAuthenticatedData: randomArray) //$IV$error
  _ = CCM(iv: iv, tagLength: 0, messageLength: 0, authenticationTag: randomArray, additionalAuthenticatedData: randomArray) //$IV$error
  _ = OFB(iv: iv) //$IV$error
  _ = CTR(iv: iv) //$IV$error
  _ = CTR(iv: iv, counter: 0) //$IV$error
  _ = PCBC(iv: iv) //$IV$error
  
  // Cryptors
  _ = try AES(key: keyString, iv: ivString) //$IV$error
  _ = try ChaCha20(key: key, iv: iv) //$IV$error
  _ = try ChaCha20(key: keyString, iv: ivString) //$IV$error
  _ = try Blowfish(key: keyString, iv: ivString) //$IV$error
  _ = try Blowfish(key: keyString, iv: ivString, padding: Padding.noPadding) //$IV$error
  _ = try Rabbit(key: key, iv: iv) //$IV$error
  _ = try Rabbit(key: keyString, iv: ivString) //$IV$error
}

func test_r2_simple_no_violation() throws {
  let iv = AES.randomIV(10)
  let ivString = arrayToString(iv)
  let key = getRandomArray()
  let keyString = arrayToString(key)
  let randomArray = getRandomArray()
  
  // Violation SHOULD NOT be detected
  
  // BlockModes
  _ = CBC(iv: iv)
  _ = CFB(iv: iv)
  _ = CFB(iv: iv, segmentSize: CFB.SegmentSize.cfb8)
  _ = CCM(iv: iv, tagLength: 0, messageLength: 0, additionalAuthenticatedData: randomArray)
  _ = CCM(iv: iv, tagLength: 0, messageLength: 0, authenticationTag: randomArray, additionalAuthenticatedData: randomArray)
  _ = OFB(iv: iv)
  _ = CTR(iv: iv)
  _ = CTR(iv: iv, counter: 0)
  _ = PCBC(iv: iv)
  
  // Cryptors, also test the different *.randomIV options
  let aesIV = AES.randomIV(10)
  _ = try AES(key: keyString, iv: arrayToString(aesIV))
  let chachaIV = ChaCha20.randomIV(10)
  _ = try ChaCha20(key: key, iv: chachaIV)
  _ = try ChaCha20(key: keyString, iv: arrayToString(chachaIV))
  _ = try Blowfish(key: keyString, iv: ivString)
  _ = try Blowfish(key: keyString, iv: ivString, padding: Padding.noPadding)
  _ = try Rabbit(key: key, iv: iv)
  _ = try Rabbit(key: keyString, iv: ivString)
}

func test_r2_non_random_but_not_constant_violation() {
  let iv = getUnknownArray()
  // Violation SHOULD be detected only for iv argument
  _ = CBC(iv: iv) //$IV$error
}

func test_r2_mixed() {
  let randomIv = AES.randomIV(10)
  let randomIvString = arrayToString(randomIv)
  let constantIv = getConstantArray()
  let constantIvString = arrayToString(constantIv)
  let randomKey = getRandomArray()
  let randomKeyString = arrayToString(randomKey)

  // Violation SHOULD be detected only for iv argument
  _ = try AES(key: randomKeyString, iv: constantIvString) //$IV$error
  _ = try AES(key: randomKeyString, iv: String(bytes: [UInt8]("".utf8), encoding: String.Encoding.utf8)!) //$IV$error
  _ = try AES(key: randomKeyString, iv: getConstantString()) //$IV$error
  // Violation SHOULD NOT be detected
  _ = try AES(key: randomKeyString, iv: randomIvString)
  _ = try ChaCha20(key: randomKey, iv: randomIv)
}
