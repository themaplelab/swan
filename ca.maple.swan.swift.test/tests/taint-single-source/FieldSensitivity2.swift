//#SWAN#sources: "FieldSensitivity2.source() -> Swift.String"
//#SWAN#sinks: "FieldSensitivity2.sink(sunk: Swift.String) -> ()"

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

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let a = A();
a.b = B();
let b = a.b;
b!.c = C();
a.b!.c!.s = source(); //source:31
sink(sunk: b!.c!.s); //sink:31