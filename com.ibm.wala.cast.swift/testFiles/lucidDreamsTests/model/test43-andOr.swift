/*logical AND/OR
 *
 * Testing logical AND in conjuntion with logical OR using integers
 */
 
var a:Int = 5
var b:Int = 17
 
if (a > 10 || b > 10 && b < 20){
	print("a is larger than 10 or b is larger than 10 and less than 20")
}
 
//Test how nested logical operations are processed
if (a > 10 || (b > 10 && b < 20)){
	print("a is larger than 10 or b is larger than 10 and less than 20")
}
 
if (a > 10 || b > 10 && b < 15 || a > 0){
	print("a is larger than 10 or b is larger than 10 and less than 15 or a is larger than 0")
}