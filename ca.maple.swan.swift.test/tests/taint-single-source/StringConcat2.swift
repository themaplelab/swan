//SWAN:sources: "StringConcat2.source() -> Swift.String"
//SWAN:sinks: "StringConcat2.sink(sunk: Swift.String) -> ()"

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func randomString() -> String {
    return "whatever, ";
}

let whatever = randomString();
let src = source(); //source:17
let combined = whatever + src; //intermediate:17
sink(sunk: combined); //sink:17