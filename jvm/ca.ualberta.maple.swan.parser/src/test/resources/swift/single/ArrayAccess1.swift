func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

var strings = [String]();
strings.append(source());
sink(sunk : strings[0]);