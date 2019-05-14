

/*
 * Here we use localized enums in structs and use switch statements with implecit type decuction within functions.
 */

struct Person: Equatable{
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
let areSame = p1==p2
if(p1==p2){
  print("The two people are the sam.")
}
else if(p1 != p2){
  print("The two people are not the same.")
}