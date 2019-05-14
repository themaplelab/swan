/* typealias keyword
 * Here we use typealias in two distinct ways.
 * 1- Type alising a struct
 * 2- Type aliasing a closure type to make it easier to read.
 */


struct Person{
  var name: String
  var age: Int
  init(name:String, age:Int) {
    self.name = name
    self.age = age
  }
}

var p1:Person = Person(name: "Yaser", age:19)

typealias Human = Person

var p2:Human = Human(name:"Ehab", age:4)

print("Person 1 is \(p1.name)")
print("Person 2 is \(p2.name)")

typealias modifier = (_ n1:Int, _ n2:Int)->Int

func f(_ n1:Int,_ n2:Int, mod:modifier)->Int{
  return mod(n1,n2)
}

var adder = {( _ n1:Int, _ n2:Int)->Int in return n1+n2}

print(f(5,3, mod:adder))