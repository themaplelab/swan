/* super keyword
 *
 */


class Human{
  var name:String
  init(_ name:String){
    self.name=name
  }
}
class Person:Human{
  override init(_ name:String){
    super.init(name)
  }
}


let p = Person("Yaser")

print(p.name)


