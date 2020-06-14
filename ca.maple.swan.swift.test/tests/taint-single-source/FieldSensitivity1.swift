//SWAN:sources: "FieldSensitivity1.source() -> Swift.String"
//SWAN:sinks: "FieldSensitivity1.sink(sunk: Swift.String) -> ()"

class Datacontainer {
    var secret: String = "";
    var description: String = "";

    init() { }

    func getSecret() -> String {
        return self.secret; //intermediate:37
    }

    func setSecret(secret: String) { //intermediate:37
        self.secret = secret; //intermediate:37
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
d1.setSecret(secret: source()); //source:37
sink(sunk: d1.getSecret()); //sink:37
sink(sunk: d1.getDescription()); // Should not be detected