func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func sanitizer(tainted: String) -> String {
    return tainted;
}

func test_taint_simple() {
  let sourced = source();
  sink(sunk: sourced);
}

class File {
  init() {}
  func open() {}
  func close() {}
}

func test_typestate_simple() {
  let f = File();
  f.open();
}

