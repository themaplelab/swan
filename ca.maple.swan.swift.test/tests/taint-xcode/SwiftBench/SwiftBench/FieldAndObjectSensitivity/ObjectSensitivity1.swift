import Foundation

/*
 * Challenge:
 * Multiple objects of the same type (same constructor) with tainted fields being sunk in the same procedure.
 */
class ObjectSensitivity1: Test {
    func run() {
        let d1 = Datacontainer();
        let d2 = Datacontainer();
        d1.setDescription(description: "abc");
        d1.setSecret(secret: source());
        d2.setDescription(description: "abc");
        d2.setSecret(secret: source());
        sink(sunk: d1.getSecret()); // Should be detected from with source being d1.setSecret(...) line
        sink(sunk: d1.getDescription()); // Should not be detected
        sink(sunk: d2.getSecret()); // Should be detected from with source being d2.setSecret(...) line
        sink(sunk: d2.getDescription()); // Should not be detected
    }
}
