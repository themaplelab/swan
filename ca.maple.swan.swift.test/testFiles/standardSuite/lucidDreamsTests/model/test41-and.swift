/*logical AND
 *
 * Testing logical AND statements under various conditions
 */

var a:Int = 5

if (a > 3 && a < 8){
	print("a is greater than 3 and less than 8")
}

//Test what happens when unnesscary AND conditions are included
if (a > 3 && a < 8 && a < 10){
	print("a is greater than 3 and less than 8 and less than 10")
}

//Test logical AND where the result is never true
if (a > 3 && a < 3){
	print("a is greater than and less than 3")
}