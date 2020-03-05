//#SWAN#sources: "FlowDroidListing2.source() -> Swift.String"
//#SWAN#sinks: "FlowDroidListing2.sink(sunk: Swift.String) -> ()"

class Data {
    var f : String;
    init() {
        self.f = "I'm not tainted (yet)";
    }
}

func taintIt(in1: String, out1: Data) { //intermediate:27 //intermediate:27
    let x = out1;
    x.f = in1; //intermediate:27 //intermediate:27
    sink(sunk: out1.f); //sink:27
}

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let p = Data();
let p2 = Data();
taintIt(in1: source(), out1: p); //source:27 //source:27
sink(sunk: p.f); //sink:27
taintIt(in1: "public", out1: p2);
sink(sunk: p2.f); 