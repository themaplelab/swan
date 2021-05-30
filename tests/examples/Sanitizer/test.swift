func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func sanitizer(tainted: String) -> String {
    return tainted;
}

let sourced = source();
let sanitized = sanitizer(tainted: sourced);
sink(sunk: sanitized);
