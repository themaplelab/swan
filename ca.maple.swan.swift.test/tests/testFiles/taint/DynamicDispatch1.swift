//#SWAN#sources: "DynamicDispatch1.source() -> Swift.String"
//#SWAN#sinks: "DynamicDispatch1.sink(sunk: Swift.String) -> ()"

protocol Parent {
    func doSomething(s: String);
}

class Child1 : Parent {

    func doSomething(s : String) { //intermediate:36 //intermediate:39 //intermediate:40
        sink(sunk: s); //sink:36 //sink:39 //sink:40
    }
}

class Child2 : Parent {
    
    func doSomething(s : String) {
        // Nothing
    }
}

func testSomething(p : Parent, s : String) { //intermediate:39 //intermediate:40
    p.doSomething(s: s); //intermediate:39 //intermediate:40
}

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let child1 = Child1();
let child2 = Child2();
child1.doSomething(s: source()); //source:36
child2.doSomething(s: source());

testSomething(p: child1, s: source()); //source:39
testSomething(p: child2, s: source()); //source:40 causes FP