/*
 * generic function
 * 
 */


func swapTwoVals<T> (_ n1: inout T, _ n2: inout T){
  let temp:T = n1
  n1=n2
  n2=temp
}

var n1:Int = 10
var n2:Int = 15

print("Before: n1=\(n1), n2=\(n2)")
swapTwoVals(&n1,&n2)
print("Before: n1=\(n1), n2=\(n2)")


var f1:Double = 4.5
var f2:Double = 5.5
print("Before: f1=\(f1), f2=\(f2)")
swapTwoVals(&f1,&f2)
print("Before: f1=\(f1), f2=\(f2)")



var w1:String = "Hello"
var w2:String = "World"

print("Before: w1=\(w1), w2=\(w2)")
swapTwoVals(&w1,&w2)
print("Before: w1=\(w1), w2=\(w2)")

