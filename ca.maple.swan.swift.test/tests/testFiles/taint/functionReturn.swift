//#SWAN#sources: "functionReturn.source() -> Swift.String"
//#SWAN#sinks: "functionReturn.sink(sunk: Swift.String) -> ()"

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}


func testFunc(toSink: String) -> Int { //intermediate:23
    sink(sunk: toSink); //sink:23
    return 1;
}

func getFunc() -> (String) -> Int {
    return testFunc;
}

var f = getFunc();
let x = f(source()); //source:23