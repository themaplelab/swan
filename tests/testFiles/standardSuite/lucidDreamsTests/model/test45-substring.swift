/* Substrings
 *
 * Testing basic substring opperation
 */
 
import Foundation

var str = "Hello World"

var index = str.index(str.startIndex, offsetBy:5)
var substring = str.substring(to:index)

print(substring)