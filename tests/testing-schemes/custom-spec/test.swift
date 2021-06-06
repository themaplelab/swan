func custom_source() -> String {
    return "I'm bad";
}

func custom_sink(sunk: String) {
    print(sunk);
}

let sourced = custom_source(); //!testing!source
custom_sink(sunk: sourced); //!testing!sink
