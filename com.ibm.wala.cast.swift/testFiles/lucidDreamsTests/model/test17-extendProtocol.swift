/*
 * Extending a struct with a protocol
 *
 * Here we are forcing a struct to be of a given type so that we can pass it
 * into a different function that requires certain functionality
 */

struct Human{
  init(_ name:String, age:Int = -1){
    self.name = name
    self.age = age
  }
  var age:Int
  var name:String
}

protocol Person{
  var name:String{get set}
}

extension Human:Person{}

func printName(_ p:Person){
  print(p.name)
}

let y:Human=Human("Yaser")
printName(y)
