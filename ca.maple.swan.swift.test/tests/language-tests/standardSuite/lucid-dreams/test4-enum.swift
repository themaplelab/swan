/*enum types
 *
 * Here we simply create an enum, enstantiate it and print out different values.
 */

enum Colour{
  case yellow, red, blue, green, black
}
var c:Colour = Colour.red
print("The colour the enum is \(c)")

c = Colour.blue
print("The colour the enum is \(c)")
