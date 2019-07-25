func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print("security risk!");
}

let sourced = source();
sink(sunk: sourced);