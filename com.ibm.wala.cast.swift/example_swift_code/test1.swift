func add_1(x: Int) -> Int {
	var y = x + 1
	return y
}

var x = 0
var i = 0

for i in 1..<5 {
	x = add_1(x:x)
}

print(x)
