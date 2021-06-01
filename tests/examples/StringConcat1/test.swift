func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let whatever = "whatever, ";
let src = source(); //!testing!source
let combined = whatever + src;
sink(sunk: combined); //!testing!sink
