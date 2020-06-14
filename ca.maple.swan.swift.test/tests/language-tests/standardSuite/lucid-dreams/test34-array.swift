// UNSUPPORTED

/* arrays
 *
 * Testing out a lot of the standard functions of arrays 
 */


var integers = [Int]()

print("We have \(integers.count) integers.")

integers.append(10)
integers.append(126)
integers.append(1027)
integers.append(-10)
print("We have \(integers.count) integers.")

integers = []

print("We have \(integers.count) integers.")

var names          = ["Yaser", "Karim", "Julian", "Jeff"]
var names2 :[String] = ["Yaser", "Karim", "Julian", "Jeff"]

print("We have \(names.count) names.")

if names.isEmpty {
  print("The names are empty.")
}
else{
  print("The names are not empty.")
}

names += ["Ehab","Muhab"]

print("We have \(names.count) names.")

names[0] = "John"
names.insert("George",at:0)

names[1...3] = ["Brenda", "Michael","happy", "Canada"]
print(names)

names[1...3] = ["Brenda", "Michael","happy", "Canada"]
print(names)

let item = names.remove(at:names.count-1)

print(item)

for item in names {
  print(item)
}

for (i, item) in names.enumerated() {
  print("\(i): \(item)")
}





