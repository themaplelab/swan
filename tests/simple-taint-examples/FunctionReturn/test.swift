func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}


func testFunc(toSink: String) -> Int {
    sink(sunk: toSink); //!testing!sink
    return 1;
}

func getFunc() -> (String) -> Int {
    return testFunc;
}

func testMain() {
  let f = getFunc();
  let x = f(source()); //!testing!source
}
