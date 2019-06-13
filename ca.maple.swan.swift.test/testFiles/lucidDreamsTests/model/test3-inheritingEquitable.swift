/*
 * operator overloading
 * inheriting equitable
 * 
 *
 * The reason why inheritance and operator overloading are both in this testcase is because they
 * depend on each other in this case. We are inheriting a protocol that forces us to implement
 * operator overloaded functions for equality.
 *
 */
struct Person: Equatable{
  var name: String
  init(_ name:String){
    self.name = name
  }
}

func ==(_ p1:Person, _ p2:Person)->Bool{
  return p1.name == p2.name 
}
func !=(_ p1:Person, _ p2: Person)->Bool{
  return p1.name != p2.name
}
var p1 = Person("Yaser")
var p2 = Person("Amir")
var p3 = Person("Yaser")


//Two people that are the same
var same:String=""
if (p1 != p2){
  same = "not"
}
print("\(p1.name) and \(p2.name) are \(same) the same!")

//Two people that are the same
same = "not"
if (p1 == p3){
  same = ""
}
print("\(p1.name) and \(p3.name) are \(same) the same!")


