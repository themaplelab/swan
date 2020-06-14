//SWAN:sources: "StringConcat.source() -> Swift.String"
//SWAN:sinks: "StringConcat.sink(sunk: Swift.String) -> ()"
func source() -> String { return "I'm bad"; }
func sink(sunk: String) { print(sunk); }
func random() -> String { return "whatever"; }
let whatever = random();
let src = source();//source:7
let combined = whatever + src;
sink(sunk: combined);//sink:7