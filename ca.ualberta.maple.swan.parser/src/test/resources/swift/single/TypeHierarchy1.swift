protocol Base {
  func foo()
}

protocol AnotherBase: Base {
  func bar()
}

class A: Base {
  func foo() {}
}

class B: Base {
  func foo() {}
  func baz() {}
}

class C: B { }

class C2: B { }

class D: AnotherBase {
  func foo() {}
  func bar() {}
}

func getBase(x: Int) -> Base {
  if (x > 0) {
    return A();
  } else if (x < 0) {
    return B();
  } else {
    return C();
  }
}

func getC(x: Int) -> B {
  if (x > 0) {
    return C();
  } else {
    return C2();
  }
}

let ab = getBase(x: 5);
let cc2 = getC(x: 5);
ab.foo();
cc2.baz();