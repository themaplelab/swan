//#SWAN#sources: "StringConcat1.source() -> Swift.String"
//#SWAN#sinks: "StringConcat1.sink(sunk: Swift.String) -> ()"

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let whatever = "whatever, ";
let src = source(); //source:13
let combined = whatever + src; //intermediate:13
sink(sunk: combined); //sink:13