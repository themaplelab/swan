//SWAN:sources: "ObjectSensitivity1.source() -> Swift.String"
//SWAN:sinks: "ObjectSensitivity1.sink(sunk: Swift.String) -> ()"

class Datacontainer {
    var secret: String = "";
    var description: String = "";

    init() { }

    func getSecret() -> String {
        return self.secret; //intermediate:38 //intermediate:40
    }

    func setSecret(secret: String) { //intermediate:38 //intermediate:40
        self.secret = secret; //intermediate:38 //intermediate:40
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
let d2 = Datacontainer();
d1.setDescription(description: "abc");
d1.setSecret(secret: source()); //source:38
d2.setDescription(description: "abc");
d2.setSecret(secret: source()); //source:40
sink(sunk: d1.getSecret()); //sink:38
sink(sunk: d1.getDescription()); // Should not be detected
sink(sunk: d2.getSecret()); //sink:40
sink(sunk: d2.getDescription()); // Should not be detected