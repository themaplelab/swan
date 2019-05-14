/*
 * extension
 * 
 * Testing a simple extension of the person class.
 * The extension adds two functions, one that returns nothing and another that
 * returns a string.
 */

struct Person{
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


//new code specific to this testcase.
extension Person{
  func say(phrase: String){
    print(phrase)
  }
  func isStudent()->Bool{
    return self.profession == Person.Profession.student
  }
}

var p1: Person = Person("Ehab", Person.Profession.teacher)

p1.say(phrase: "I love Swift.")
if(p1.isStudent()){
  print("The person is a student")
}
else{
  print("The person is not a student")
}