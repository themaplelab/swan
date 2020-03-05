// UNSUPPORTED

/* where clause
 * 1- where clause in for loop
 * 2- generics with type constraints using where
 *
 */

let people = ["a","b","a","a","c","d"]

var count:Int =  0
for i in people where i=="a"{
  count+=1
}
print("We have \(count) a's.")




struct Human<NameType>{
  var name:NameType

  init(_ name: NameType){
    self.name = name
  }
}

extension Human where NameType == String{
  mutating func resetName(){
    self.name = "Nobody"
  }
}
extension Human where NameType == Int{
  mutating func resetName(){
    self.name = 0
  }
}

var p1:Human = Human("Yaser")
var p2:Human = Human(101)

print("Before p1 has name \(p1.name) and p2 has name \(p2.name)")
p1.resetName()
p2.resetName()
print("After resetting name p1 has name \(p1.name) and p2 has name \(p2.name)")
