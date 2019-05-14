class Person{
  var name = "Yaser"
  var age = 15
  var catchPhrase: String?
}

let yaser = Person()

print("The person is \(yaser.name as String) and their age is \(yaser.age)")

let notYaser = Person()

notYaser.name = "Not Yaser"

print("The new person is \(notYaser.name as String) and their age is \(notYaser.age)")

