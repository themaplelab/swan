func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}

func test_closure_simple() {
  let closure = {
    let sourced = source(); 
    sink(sunk: sourced); //!testing!source//!testing!sink
  }
  closure();
}

func test_closure_parameter() {
  let closure:(String) -> () = { s in
    sink(sunk: s); //!testing!sink
  }
  let sourced = source();
  closure(sourced); //!testing!source
}

func test_closure_return() {
  let closure:(String) -> String = { s in 
    return s;
  }
  let sourced = source();
  let throughClosure = closure(sourced); //!testing!source
  sink(sunk: throughClosure); //!testing!sink
}

func test_closure_function_parameter() {
  func foo(closure:()->()) {
    closure();
  }
  foo(closure: {
    let sourced = source(); 
    sink(sunk: sourced); //!testing!source//!testing!sink
  });
}

func test_closure_trailing() {
  func foo(s: String, closure:()->()) {
    sink(sunk: s); //!testing!sink
    closure();
  }
  let sourced1 = source(); 
  foo(s: sourced1) { //!testing!source
    let sourced2 = source(); 
    sink(sunk: sourced2); //!testing!source//!testing!sink
  };
}

func test_closure_autoclosure() {
  func foo(closure: @autoclosure ()->()) {
    closure();
  }
  let sourced = source();
  foo(closure: (sink(sunk: sourced))); //!testing!source//!testing!sink
}

func test_closure_autoclosure_return() {
  func foo(_ closure: @autoclosure ()->(String)) {
    let res = closure();
    sink(sunk: res); //!testing!sink!fn //SWAN-34
  }
  let sourced = source();
  foo(sourced); //!testing!source!fn //SWAN-34
}

func test_closure_array() {
  var closureArr:[()->()] = []
  let closure = {
    let sourced = source(); 
    sink(sunk: sourced); //!testing!source//!testing!sink
  }
  closureArr.append(closure);
  closureArr[0]()
}

func test_closure_array_escaping() {
  var closureArr:[()->()] = [];
  func foo(closure: @escaping ()->()) {
    closureArr.append(closure);
  }
  foo(closure: {
    let sourced = source(); 
    sink(sunk: sourced); //!testing!source!fn//!testing!sink!fn //SWAN-34
  });
  closureArr[0]();
}

func test_closure_capturing() {
  func makeFoo(s: String) -> () -> String {
    var str = "";
    func foo() -> String {
      str = s;
      return str;
    }
    return foo;
  }
  let sourced = source();
  let foo = makeFoo(s: sourced); //!testing!source!fn //SWAN-34
  sink(sunk: foo()); //!testing!sink!fn //SWAN-34
}

func test_closure_currying() {
  func foo(_ s: String) -> (String) -> String {
    return {s + $0}
  }
  let curriedFoo = foo("something");
  let sourced = source();
  sink(sunk: curriedFoo(sourced)) //!testing!source!fn//!testing!sink!fn //SWAN-34
}
