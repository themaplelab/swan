import Foundation

/*
* Challenge:
* Must be able to distinguish between fields of an object.
*/
class FieldSensitivity1: Test {
    func run() {
        let d1 = Datacontainer();
        d1.setDescription(description: "abc");
        d1.setSecret(secret: source());
        sink(sunk: d1.getSecret()); // Should be detected
        sink(sunk: d1.getDescription()); // Should not be detected
    }
}
