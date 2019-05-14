/* switch statements
 * enums
 * 
 * Here we are using the explicit types of the enums to switch.
 */
enum Colour{
  case yellow, red, blue, green, black
}
let c = Colour.red 
var colourName: String
switch(c){
  case Colour.yellow: colourName = "yellow"
  case Colour.red:    colourName = "red"
  case Colour.blue:   colourName = "blue"
  case Colour.green:   colourName = "green"
  case Colour.black:  colourName = "black"
  default: colourName = "Invalid Colour"
}
print("The colour is: \(colourName)")