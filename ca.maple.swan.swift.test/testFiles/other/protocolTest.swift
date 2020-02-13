protocol testingProtocol {
    func testProtocolFunc();
    func testProtocolFunc2()
}

class testingImpl : testingProtocol {
    func testProtocolFunc() {
        print("hello");
    }

    func testProtocolFunc2() {
        print("hello2");
    }
}

class testingImpl2 : testingProtocol {
    func testProtocolFunc() {
        print("hello");
    }

    func testProtocolFunc2() {
            print("hello2");
    }
}

func callProtocolFunc(obj: testingProtocol) {
    obj.testProtocolFunc();
}

callProtocolFunc(obj: testingImpl());