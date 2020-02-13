class Data {
    var f : String;
    init() {
        self.f = "I'm not tainted (yet)";
    }
}

func taintIt(in1: String, out1: Data) {
    let x = out1;
    x.f = in1;
    sink(sunk: out1.f);
}

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let p = Data();
let p2 = Data();
taintIt(in1: source(), out1: p);
sink(sunk: p.f);
taintIt(in1: "public", out1: p2);
sink(sunk: p2.f);