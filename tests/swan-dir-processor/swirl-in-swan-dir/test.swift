func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func foo() {
  // test.swirl will overwrite this so there should be no path detected
  let sourced = source();
  sink(sunk: sourced);
}

foo() 
