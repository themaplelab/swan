/*
 * creating structure
 * initialize / constructor
 * 
 * Simple test case that creates a Person struct and sets their name.
 * It then later prints the name of this person to sho the values store in the struct.
 */


struct Person{
  var name: String
  init(_ name:String){
    self.name = name
  }
}

var p1 = Person("Yaser")

print(p1.name)

