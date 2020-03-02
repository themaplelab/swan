/* "inout" keyword for modifying function parameters.
 *
 * The "inout" keyword allows us to modify parameters within a functions
 * and keep the modifications persist outside of the function.
 *
 * Here we also rely on another operator "&" to get the reference of a variable.
 */

 func change(_ s: inout String){
   s += " ,not!" 
 }

 var s:String = "Beautiful"

 print(s)
 change(&s)
 print(s)




