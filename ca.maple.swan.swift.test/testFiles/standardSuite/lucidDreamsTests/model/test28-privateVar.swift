/* private and public variables in structs
 *
 *
 */


struct Person{
  public var name: String
  private var weight: Int 
  init(name:String, weight:Int){
    self.name = name
    self.weight = weight
  }
}

var p:Person = Person(name : "Yaser", weight :200)

print("Person's name: \(p.name)")
