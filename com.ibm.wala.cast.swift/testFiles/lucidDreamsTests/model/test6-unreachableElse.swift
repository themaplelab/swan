/*
 * Unreachable else clause.
 * This is useful to check what kind of optimiuzation LLVM is doing to the code
 *
 */

var num1 = 10
var num2 = 15

if(num1==num2){
  print("The two numbers are the same")
}
else if(num1!=num2){
  print("The two numbers are not the same")
}
else{
  print("OOPS. This should never be printed")
}

