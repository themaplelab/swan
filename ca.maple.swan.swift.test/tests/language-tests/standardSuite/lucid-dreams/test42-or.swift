/*logical OR
 *
 * Testing logical OR statements under various conditions using integers
 */

var a:Int = 5
var b:Int = 17

if (a > 10 || b > 10){
	print("a or b is larger than 10")
}

//Test what happens when there are unecessary preceeding OR conditions
if (a > 10 || a > 0){
	print("a is greater than 10 or a is greater than 0")
}

//Test what happens when there are unecessary following OR conditions
if (a > 0 || b > 0){
	print("a is greater than 0 or b is greater than 0")
}

//Test when all conditions fail
if (a < 0 || b < 0 || a > b){
	print("a is less than 0 or b is less than 0 or a is greater than b")
}