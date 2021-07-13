class Datacontainer {
    var secret: String = "";
    var description: String = "";

    init() { }

    func getSecret() -> String {
        return self.secret;
    }

    func setSecret(secret: String) {
        self.secret = secret;
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
d1.setSecret(secret: source());
d2.setDescription(description: "abc");
d2.setSecret(secret: source());
sink(sunk: d1.getSecret());
sink(sunk: d1.getDescription()); // Should not be detected
sink(sunk: d2.getSecret());
sink(sunk: d2.getDescription()); // Should not be detected