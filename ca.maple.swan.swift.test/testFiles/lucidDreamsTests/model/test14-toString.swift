/*
 * "toString" or in Swift the description property
 * 
 * Printing out a struct.
 */

struct Person: CustomStringConvertible{
  var name: String

  init(_ name:String){
    self.name = name
  }
  var description: String {
    return name
  }
}

let p:Person = Person("Saurabh")

print("The person is \(p)")

