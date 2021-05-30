func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func sanitizer(v: String) -> String {
    return v;
}

let sourced = source();
let sanitized = sanitizer(v: sourced);
sink(sunk: sanitized);
