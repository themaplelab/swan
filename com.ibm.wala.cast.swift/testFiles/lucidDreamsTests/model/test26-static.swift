/* static keyword
 *
 * testing the static keyword for both function and variables
 */

struct Person{
  static var numPeople:Int = 0
  var name:String
  
  init(_ name: String){
    self.name=name
    Person.numPeople+=1
  }
}

var p1:Person = Person("A")
var p2:Person = Person("B")

print("Num people = \(Person.numPeople)")
