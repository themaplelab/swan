/* Numeric Operations
 * Testing more complication numeric operations to see how they are evauated
 * after being converted to SIL.
 */
func add(_ a:Int,_ b: Int)->Int{
  return a + b;
}
func calc()->Int{
  return 17
}
var a:Int = 17

a += 1
a = a + 1

a += -1
a = a + (-1)

a -= 1
a = a - 1

a = a - (-1)
a -= (-1)

var b:Int = 3

var c: Int = a + b 
c = a+b
c = calc()
c = calc() + a
c = calc() - b
c = add(a,b)

print("a: \(a), b:\(b), c:\(c)")
 