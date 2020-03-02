

/*localized variable calculation through switch statement
 *
 * Here we are using a switch statement to directly assign the value of a variable within a class.
 * This is basically syntactic sugar as we are able to return a value within brackets which will be assigned
 * to the variable.
 */

struct Person: Equatable{
  var name: String
  var profession: Profession
  enum Profession{
    case student, teacher
  }
  var professionName:String{
      switch(self.profession){
          case .student: return "student"
          case .teacher: return "teacher"
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

print("\(p1.name) is a \(p1.professionName)")
print("\(p2.name) is a \(p2.professionName)")
