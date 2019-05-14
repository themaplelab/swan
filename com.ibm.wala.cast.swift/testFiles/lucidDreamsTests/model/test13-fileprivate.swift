/*
 * fileprivate keyword
 *
 * Simple struct that has a fileprivate constructor so it only allows class to
 * be created only in this file.
 *
 */

struct Person{
  var name: String
  fileprivate init(name: String){
    self.name = name;
  }
}

var p:Person = Person(name : "Amy")

print("The person is \(p.name)")
