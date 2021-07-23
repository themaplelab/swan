func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

class File {
  init() {}
  func open() {
    let sourced = source(); //!testing!source
    sink(sunk: sourced); //!testing!sink
  }
  func close() {}
}

func test() {
  let f = File()
  f.open() //?FileOpenClose?error
}
