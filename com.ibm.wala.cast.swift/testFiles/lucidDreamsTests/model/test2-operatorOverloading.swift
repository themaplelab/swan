/*
 * operator overloading
 *
 * Helps us compare multiple structures of the same type.
 */

struct Person{
  var name: String
  init(_ name:String){
    self.name = name
  }
}
func ==(_ p1:Person, _ p2:Person)->Bool{
  return p1.name == p2.name 
}

var p1 = Person("Yaser")
var p2 = Person("Amir")
var p3 = Person("Yaser")


//Two people that are the same
var same:String = "not"
if (p1 == p3){
  same = ""
}
print("\(p1.name) and \(p3.name) are \(same) the same!")


