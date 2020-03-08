//#SWAN#sources: "DictionaryAccess1.source() -> Swift.String"
//#SWAN#sinks: "DictionaryAccess1.sink(sunk: Swift.String) -> ()"

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

var dict = [String: String]();
dict["notTainted"] = "neutral text";
dict["tainted"] = source(); //source:14
sink(sunk: dict["notTainted"]!);
sink(sunk: dict["tainted"]!); //sink:14