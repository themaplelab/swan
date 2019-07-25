
class UniversityClass{
  var term: String? = "Fall 2017"
  var size: UInt = 4
  var name: String = "UCOSP"
}


let ucosp = UniversityClass()

print("The class \(ucosp.name) has \(ucosp.size) students and is taught in \(ucosp.term as Optional)")



