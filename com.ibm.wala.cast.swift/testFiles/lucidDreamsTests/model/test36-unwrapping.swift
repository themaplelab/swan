/* Unwrapping using "!" operator
 *
 * This operator guarantees to the compiler that this optional variable has
 * already been assigned a value and using it is fine. This might cause fatal
 * errors if the variable has not been assigned and it is still nil.
 */


var name: String? = nil

name = "Yaser"

print("The name is \(name!)")
