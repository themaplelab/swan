/* nil coalescing operator & anonymous function
 *
 * The ?? operator sets the variable to the first option if it is not 
 * nil, and otherwise sets it to the second option.
 * This might be helpful to guarantee that a var has a value.
 */

var v:String? = nil

v = v ?? {
    return "No"
}()

print(v!)