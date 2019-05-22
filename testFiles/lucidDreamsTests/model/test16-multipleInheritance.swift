/* Multiple inheritance
 * 
 * inheriting equitable and customStringConvertible
 *
 */


class Person: CustomStringConvertible, Equatable{
  var name:String
  init(_ name:String){
    self.name = name
  }
  public var description:String{
    return self.name
  }
}
func ==(_ p1:Person, _ p2: Person)->Bool{
  return p1.name == p2.name
}

let yaser:Person = Person("Yaser")
let ehab:Person  = Person("Ehab")

if yaser == ehab{
  print("\(yaser) and \(ehab) are the same")
}
else{
  print("\(yaser) and \(ehab) are not the same")
}

