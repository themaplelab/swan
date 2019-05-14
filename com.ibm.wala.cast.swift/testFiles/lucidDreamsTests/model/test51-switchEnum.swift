/* switch statements
 * enums
 * 
 * using implicit values of enum in a switch statement.
 * Here we are not specifying the type of enum in the cases.
 * The type is derived from the type of enum passed.
 * One problem with this method is that if two enums have the same enumerations,
 * then it might not be safe as the statement will switch on either of them.
 * This could also be considered a feature.
 */
enum Colour{
  case yellow, red, blue, green, black
}
let c = Colour.red 
var colourName: String
switch(c){
  case .yellow: colourName = "yellow"
  case .red:    colourName = "red"
  case .blue:   colourName = "blue"
  case .green:   colourName = "green"
  case .black:  colourName = "black"
  default: colourName = "Invalid Colour"
}
print("The colour is: \(colourName)")

