

/*
 *localized variable calculation through switch staement
 *
 * Here we do a similar thing to test9, but with a class which has different memory
 * management than a struct. 
 */

class Person: Equatable{
  var name: String
  var profession: Profession
  enum Profession{
    case student, teacher
  }
  var professionName:String {
      switch(self.profession){
          case .student: return "student"
          case .teacher: return "teacher"
          default:       return "ERROR"
      }
  }
  var catchPhrase:String{
      switch(self.profession){
          case .student: return "I am a \(self.professionName) and I am amazing"
          case .teacher: return "I am a \(self.professionName) and I love my students"
          default:       return "ERROR"
      }
  }
  init(_ name:String, _ profession:Profession){
    self.name = name
    self.profession = profession
  }
}

func ==(_ p1:Person, _ p2:Person)->Bool{
  switch(p1.profession, p2.profession){
    case (.student, .student): return true
    case (.teacher, .teacher): return true
    default:                   return false
  }
}
func !=(_ p1:Person, _ p2: Person)->Bool{
  switch(p1.profession, p2.profession){
    case (.student, .student): return false
    case (.teacher, .teacher): return false
    default:                   return true
  }
}

var p1: Person = Person("Yaser",Person.Profession.student)
var p2: Person = Person("Karim", Person.Profession.teacher)

print("\(p1.catchPhrase)")
print("\(p2.catchPhrase)")
