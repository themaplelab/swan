//SWAN:sources: "ArrayAccess2.source() -> Swift.String"
//SWAN:sinks: "ArrayAccess2.sink(sunk: Swift.String) -> ()"

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

var strings = [String]();

strings.append(source()); //source:14 //source:14
strings.append("whatever");

sink(sunk : strings[0]); //sink:14
sink(sunk : strings[1]); //sink:14 (FP)

strings.removeAll();

strings.append("whatever");
sink(sunk : strings[0]);

strings.append(source()); //source:25

sink(sunk : strings[1]); //sink:25