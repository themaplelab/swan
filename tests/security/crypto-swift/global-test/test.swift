import Foundation

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
	init(key: String, iv: String) { }
}

func arrayToString(_ arr: Array<UInt8>) -> String {
  String(bytes: arr, encoding: .utf8)!
}

let keyString = "constant"

func test_global_usage() throws {
  let iv = AES.randomIV(10)
  let ivString = arrayToString(iv)
    
  // AES
  _ = try AES(key: keyString, iv: ivString) //$KEY$error
}