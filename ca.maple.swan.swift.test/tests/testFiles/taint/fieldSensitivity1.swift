//#SWAN#sources: "fieldSensitivity1.source() -> Swift.String"
//#SWAN#sinks: "fieldSensitivity1.sink(sunk: Swift.String) -> ()"

class Datacontainer {
    var secret: String = "";
    var description: String = "";

    init() { }

    func getSecret() -> String {
        return self.secret; //intermediate
    }

    func setSecret(secret: String) { //intermediate
        self.secret = secret; //intermediate
    }

    func getDescription() -> String {
        return self.description;
    }

    func setDescription(description: String) {
        self.description = description;
    }
}

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let d1 = Datacontainer();
d1.setDescription(description: "abc");
d1.setSecret(secret: source()); //source
sink(sunk: d1.getSecret()); //sink
sink(sunk: d1.getDescription()); // Should not be detected