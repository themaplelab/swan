func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}


func testFunc(toSink: String) -> Int {
    sink(sunk: toSink);
    return 1;
}

func getFunc() -> (String) -> Int {
    return testFunc;
}

var f = getFunc();
let x = f(source());