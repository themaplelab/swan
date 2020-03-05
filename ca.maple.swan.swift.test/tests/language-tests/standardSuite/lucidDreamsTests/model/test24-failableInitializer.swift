/* Failable initializers
 *
 * This allows us to check a condition in the intializer and return
 * nil in case something doesn't check out. This is super useful compared to
 * other languages like C++ that force you to create an object once the
 * constructor is called.
 */


struct Person{
  var name:String
  init?(_ name:String){
    if name.isEmpty{
      return nil
    }
    self.name=name;
  }
}


if let p = Person("Yaser"){
  print("\(p.name) was initialized!")
}
else{
  print("The person was not initialized properly.")
}

if let p = Person(""){
  print("\(p.name) was initialized!")
}
else{
  print("The person was not initialized properly.")
}
