/*
 * Switch statements
 * 
 * Testing both regular cases and the degault case
 */
 
var c: Character = "b";

switch(c){
  case "a": print("letter a")
  case "b": print("letter b")
  case "c": print("letter c")
  default: print("unrecognizable letter")
}

c="Z"

switch(c){
  case "a": print("letter a")
  case "b": print("letter b")
  case "c": print("letter c")
  default: print("unrecognizable letter")
}