/*
 * switch statements
 * 
 * Here we are switching on multiple values at the same time. 
 */
enum Colour{
  case yellow, red, blue, green, black
}
let c1 = Colour.red 
let c2 = Colour.red
var areSame: Bool

switch(c1,c2){
  case (.yellow, .yellow):areSame = true
  case (.red,    .red):   areSame = true 
  case (.blue,   .blue):  areSame = true 
  case (.green,  .green): areSame = true  
  case (.black,  .black): areSame = true
  default:                areSame = false
}

if(areSame){
  print("The two colours are the sam.")
}
else{
  print("The two colours are not the same.")
}
