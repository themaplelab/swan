/*
 * Source: "SwiftBench.source() -> Swift.String"
 * Sink:   "SwiftBench.sink(sunk: Swift.String) -> ()"
 */

import Foundation
import CoreLocation
import os.log

protocol Test {
    func run();
}

class A {
    var b : B?;
    init() {}
}

class B {
    var c : C?;
    init() {}
}

class C {
    var s : String = "not tainted";
    init() {}
}

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

FieldSensitivity1().run();
FieldSensitivity2().run();
ObjectSensitivity1().run();
