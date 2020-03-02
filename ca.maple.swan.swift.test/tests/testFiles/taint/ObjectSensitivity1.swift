//#SWAN#sources: "ObjectSensitivity1.source() -> Swift.String"
//#SWAN#sinks: "ObjectSensitivity1.sink(sunk: Swift.String) -> ()"

class Datacontainer {
    var secret: String = "";
    var description: String = "";

    init() { }

    func getSecret() -> String { //intermediate
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

func sink(sunk: String) { //sink
    print(sunk);
}

let d1 = Datacontainer();
let d2 = Datacontainer();
d1.setDescription(description: "abc");
d1.setSecret(secret: source()); //source
d2.setDescription(description: "abc");
d2.setSecret(secret: source()); //source
sink(sunk: d1.getSecret()); //intermediate
sink(sunk: d1.getDescription()); // Should not be detected
sink(sunk: d2.getSecret()); //intermediate
sink(sunk: d2.getDescription()); // Should not be detected