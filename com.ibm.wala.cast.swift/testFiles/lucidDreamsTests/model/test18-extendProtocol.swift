/*
 * Extending multiple structs/classes with a protocol to pass them into
 * functions
 *
 * Two different structs start off by being of different types. However,
 * we are able to pass them into a function and use a shared functionality
 * because we extend them both with the person protocol.
 */

struct Human{
  init(_ name:String, age:Int = -1){
    self.name = name
    self.age = age
  }
  var age:Int
  var name:String
}

class Pet{
  init(_ name:String, ownerName:String="Unknown"){
    self.name = name
    self.ownerName = ownerName
  }
  var name:String
  var ownerName:String
}

protocol Person{
  var name:String{get set}
}

extension Human:Person{}
extension Pet:Person{}

func printName(_ p:Person){
  print(p.name)
}

let y:Human=Human("Yaser")
let p:Pet = Pet("Snowflake")
printName(y)
printName(p)

