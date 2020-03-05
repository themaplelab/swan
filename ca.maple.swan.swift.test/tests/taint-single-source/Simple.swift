//#SWAN#sources: "SimpleTest.source() -> Swift.String"
//#SWAN#sinks: "SimpleTest.sink(sunk: Swift.String) -> ()"

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let sourced = source(); //source:12
sink(sunk: sourced); //sink:12