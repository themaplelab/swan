/* Uppercase/substring
 *
 * Capitalize the first character of a string using substrings
 */
 
import Foundation

let str = "hello world"

let secondIndex = str.index(after: str.startIndex)
let capitalized = str.substring(to: secondIndex).uppercased() + str.substring(from: secondIndex)

print(capitalized)
