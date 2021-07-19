func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func test_recursion_simple() {
  let sourced = source();
  sink(sunk: sourced); //!testing!source//!testing!sink
  test_recursion_simple();
}

func test_recursion_arguments(str: String) {
  sink(sunk: str); //!testing!sink
  test_recursion_arguments(str: source()); //!testing!source
}
