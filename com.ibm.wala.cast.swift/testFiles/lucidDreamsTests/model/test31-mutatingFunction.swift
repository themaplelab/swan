/* Mutating and non-mutating functions in structs
 *
 *
 * We test the behaviour between mutating and non-mutating functions. 
 */


struct Person{
  var educationLevel:String
  init(el:String){
    educationLevel=el
  }
  mutating func setEducationLevel(el:String){
    self.educationLevel = el
  }
 func tryToChangeEducationLevel(el:String){
   print("Error. Can't mutate an instance variable within non-mutating func")
 }
}

var p:Person = Person(el:"HighSchool")
print(p.educationLevel)
p.setEducationLevel(el:"Bachelors")
print(p.educationLevel)
p.tryToChangeEducationLevel(el:"Masters")


