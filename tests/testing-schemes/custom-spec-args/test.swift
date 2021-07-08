func source() -> String {
    return "I'm bad";
}

func sink0(_ sensitive: String) {
    print(sensitive);
}

func sink1(_ nonsensitive: String, _ sensitive: String) {
    print(sensitive);
}

func test_sink0() {
  let sourced = source(); 
  sink0(sourced); //!testing!source//!testing!sink
}

func test_sink1() {
  let sourced = source(); 
  sink1("", sourced); //!testing!source//!testing!sink
}

func test_sink1_noerror() {
  let sourced = source();
  sink1(sourced, "");
}
