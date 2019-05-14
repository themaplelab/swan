/* required keyword
 *
 * For instances where a class inherets a protocol,
 * we have to use the required keyword to force any 
 * subclasses to call this method.
 */

protocol Person {
  init()
}
class Human: Person {
  required init() {
    print("Created person.")  
  }
}
let b:Human = Human()