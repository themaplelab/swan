class File {
  init() {}
  func open() {}
  func close() {}
}

func test_simple_correct() {
  let f = File()
  f.open()
  f.close()
}

func test_simple_incorrect() {
  let f = File()
  f.open() //?FileOpenClose?error
}

func test_interprocedural_correct() {
  func foo_open(_ f: File) {
    f.open();
  }
  func foo_close(_ f: File) {
    f.close();
  }
  let f = File();
  foo_open(f);
  foo_close(f);
}

func test_interprocedural_incorrect() {
  func foo_open(_ f: File) {
    f.open();
  }
  func foo_close(_ f: File) {
    f.close();
  }
  let f = File();
  foo_open(f); //?FileOpenClose?error
}
