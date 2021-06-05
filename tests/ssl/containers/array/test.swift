// https://developer.apple.com/documentation/swift/array

// SWAN treats arrays as just an object with a single field.

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

func test_subcript_write() {
  let src = source();
  var arr = [String]();
  arr[0] = src; //!testing!source
  sink(sunk : arr[0]); //!testing!sink
}

func test_subscript_range() {
  let src = source();
  let arr = ["a", src, "b", "c"]; //!testing!source
  let arrSlice = arr[1 ..< arr.endIndex]
  sink(sunk : arrSlice[0]); //!testing!sink
}

func test_random_element() {
  let src = source();
  let arr = ["a", src, "b", "c"]; //!testing!source
  sink(sunk : arr.randomElement()!); //!testing!sink
}

// ------- Adding -------

func test_append() {
  let src = source();
  var arr = [String]();
  arr.append(src); //!testing!source
  sink(sunk : arr[0]); //!testing!sink
}

func test_insert() {
  let src = source();
  var arr = [String]();
  arr.insert(src, at: 0); //!testing!source
  sink(sunk : arr[0]); //!testing!sink
}

func test_insert_contentsof() {
  let src = source();
  var arr1 = [src]; //!testing!source
  var arr2 = [String]();
  arr2.insert(contentsOf: arr1, at: 0);
  sink(sunk : arr2[0]); //!testing!sink
}

func test_replace_subrange() {
  let src = source();
  var arr1 = [src]; //!testing!source
  var arr2 = ["a", "b", "c"];
  arr2.replaceSubrange(1...2, with: arr1);
  sink(sunk : arr2[0]); //!testing!sink
}

func test_append_combine() {
  let src = source();
  var arr1 = [src]; //!testing!source
  var arr2 = ["a", "b", "c"];
  arr2.append(contentsOf: arr1);
  sink(sunk : arr2[0]); //!testing!sink
}

// ...