//#SWAN#sources: "ArrayAccess1.source() -> Swift.String"
//#SWAN#sinks: "ArrayAccess1.sink(sunk: Swift.String) -> ()"

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

var strings = [String]();
strings.append(source());
sink(sunk : strings[0]);