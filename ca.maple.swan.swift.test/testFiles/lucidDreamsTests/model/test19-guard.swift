/* guard keyword
 *
 * Using the guard keyword to test if a variable exists or not before using it
 * in a struct.
 */


struct Person{
  var name:String?
  func printName() {
    guard let unwrappedName = name else {
      print("There is no name provided")
      return
    }
    print(unwrappedName)
  }
}

var v:Person = Person()

v.printName()

v.name = "Yaser"

v.printName()