/* fatalError
 *
 * Raising a fataError exception should break the program.
 */


struct Person{
  var name:String?
  init? (_ i:Int){
    fatalError("This should never happen.")
  }
}

let p:Person? = Person(1)


