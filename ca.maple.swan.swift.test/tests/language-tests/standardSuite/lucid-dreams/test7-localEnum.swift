/*
 * localized enum
 * 
 * Here we create a struct and create an enum type within it.
 * This allows us to have specialized enum types for each of the different classes that we have.
 * 
 */


struct Person{
  var name: String
  var profession: Profession
  enum Profession{
    case student, teacher
  }
  init(_ name:String, _ profession:Profession){
    self.name = name
    self.profession = profession
  }
}

var p1: Person = Person("Yaser",Person.Profession.student)

print("The person is \(p1.name) and their profession is \(p1.profession)")