//#SWAN#sources: "DictionaryAccess1.source() -> Swift.String"
//#SWAN#sinks: "DictionaryAccess1.sink(sunk: Swift.String) -> ()"

// Note: This relies on treating a dictionary "index" string as a field name.
// In reality, this doesn't work for dynamic dictionary accesses.

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

var dict = [String: String]();
dict["notTainted"] = "neutral text";
dict["tainted"] = source(); //source
sink(sunk: dict["notTainted"]!);
sink(sunk: dict["tainted"]!); //sink