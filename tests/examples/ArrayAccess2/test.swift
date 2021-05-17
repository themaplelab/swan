func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

var strings = [String]();

strings.append(source());
strings.append("whatever");

sink(sunk : strings[0]);
sink(sunk : strings[1]);
strings.removeAll();

strings.append("whatever");
sink(sunk : strings[0]);

strings.append(source());
sink(sunk : strings[1]);
