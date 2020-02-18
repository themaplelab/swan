/* TODO: Nothing added here, we should add struct within struct.
 * extension
 *
 */
struct Person:CustomStringConvertible{
  var name: String
  init(_ name:String){
    self.name = name
  }
  public var description:String{
    return self.name
  }
}


//new code specific to this testcase.

extension Person{
  struct Diff: CustomStringConvertible{
    var nameChange: (from:String, to:String)?
    fileprivate init(_ from:Person, _ to: Person){
      if from.name == to.name{
        nameChange = nil
      }
      else{
        nameChange = (from: from.name,to:to.name)
      }
    }
    public var description:String{
      let (from, to) = nameChange!
      return "The first person \(from) -> the second person \(to)"
    }
  }
  func diff(_ other:Person)->Diff{
    return Diff(self, other)
  }
}

var p1: Person = Person("Ehab")
var p2: Person = Person("Julian")


print(p1.diff(p2))