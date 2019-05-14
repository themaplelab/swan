/*
 * localized enum with switch statements
 * 
 * Here we add a switch statement that implicitly knows the type of the switch.
 * There is two layers of nesting here with the struct and the enum to deduce the type.
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

var prof:String
switch (p1.profession){
  case .student: prof = "student"
  case .teacher: prof = "teacher"
  default:       prof = "ERROR- Unknown person profession"
}

print("The person is \(p1.name) and their profession is\(prof)")