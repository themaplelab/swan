protocol Parent {
    func doSomething(s: String);
}

class Child1 : Parent {

    func doSomething(s : String) {
        // one of these is an FP, but that's expected because instantiatedTypes = {Child1, Child2}
        sink(sunk: s); //!testing!sink//!testing!sink//!testing!sink 
    }
}

class Child2 : Parent {
    
    func doSomething(s : String) {
        // Nothing
    }
}

func testSomething(p : Parent, s : String) {
    p.doSomething(s: s);
}

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let child1 = Child1();
let child2 = Child2();
child1.doSomething(s: source()); //!testing!source
child2.doSomething(s: source());

testSomething(p: child1, s: source()); //!testing!source
testSomething(p: child2, s: source()); //!testing!source
