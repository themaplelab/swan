func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

let sourced = source(); //!testing!source
sink(sunk: sourced); //!testing!sink
