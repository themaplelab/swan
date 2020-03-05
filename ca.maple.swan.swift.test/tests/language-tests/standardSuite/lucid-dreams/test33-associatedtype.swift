/* associatedtype keyword
 * 
 * This allows us to use generics in protocols to define our own type. 
 */

protocol Person{
  associatedtype Item
  init(_ item: Item)
}

struct Human:Person{
  
  typealias Item = String
  var name: Item
  init(_ name:String){
    self.name = name
  }
}

var h1 = Human("Yaser")

print("The human \(h1.name) has been created.")
