// https://developer.apple.com/documentation/swift/array

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

// ------- Creating -------

func test_init1() {
  // Slightly incorrect path (SWAN-30)
  let src = source();
  let arr = [src]; //!testing!source
  sink(sunk : arr[0]); //!testing!sink
}

func test_init2() {
  let src = source();
  let arr = Array(repeating: src, count: 2); //!testing!source 
  sink(sunk : arr[0]); //!testing!sink
}

// ------- Accessing -------

// basic subscript already tested above

func test_first() {
  let src = source();
  let arr = [src]; //!testing!source
  sink(sunk : arr.first!); //!testing!sink
}

func test_last() {
  let src = source();
  let arr = [src]; //!testing!source
  sink(sunk : arr.last!); //!testing!sink
}

// ...