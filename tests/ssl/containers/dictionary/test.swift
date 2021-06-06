// https://developer.apple.com/documentation/swift/dictionary

// SWAN treats dictionaries as just an object with a single field.
// Assumes key taintedness is irrelevant.

func source() -> String {
  return "I'm bad";
}

func sink(sunk: String) {
  print(sunk);
}

// ------- Creating -------

func test_init() {
  let src = source();
  let dict = ["key": src]; //!testing!source
  sink(sunk: dict["key"]!); //!testing!sink
}

// ------- Accessing -------

func test_subscript_default1() {
  let src = source();
  let dict = ["key": src]; //!testing!source!fn //SWAN-24
  sink(sunk: dict["key", default: "a"]); //!testing!sink!fn //SWAN-24
}

func test_subscript_default2() {
  let src = source();
  let dict = ["key": "a"];
  let taintedValue = dict["key", default: src]; //!testing!source!fn //SWAN-24
  sink(sunk: taintedValue); //!testing!sink!fn //SWAN-24
}

// ...