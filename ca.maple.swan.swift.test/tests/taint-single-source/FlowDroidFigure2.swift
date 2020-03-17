//SWAN:sources: "FlowDroidFigure2.source() -> Swift.String"
//SWAN:sinks: "FlowDroidFigure2.sink(sunk: Swift.String) -> ()"

class A {
    var g : B;

    init() {
        self.g = B();
    }
}

class B {
    var f : String;

    init() {
        self.f = "I'm not tainted (yet)"
    }
}

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func foo(z : A) {
    let x = z.g;
    let w = source(); //source:30
    x.f = w; //intermediate:30
}

let a = A();
let b = a.g;
foo(z: a);
sink(sunk: b.f); //sink:30