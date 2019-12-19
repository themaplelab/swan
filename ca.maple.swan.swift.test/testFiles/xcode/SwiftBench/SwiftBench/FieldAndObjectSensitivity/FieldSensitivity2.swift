import Foundation

/*
* Challenge:
* Must be able to handle strong updates.
*
* Note: top-level strong update testing is not necessary due to SSA conversion.
*/
class FieldSensitivity2: Test {
    func run() {
        let a = A();
        a.b = B();
        let b = a.b;
        b!.c = C();
        a.b!.c!.s = source();
        sink(sunk: b!.c!.s);
    }
}
