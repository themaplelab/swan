/* final keyword for classes
 * 
 * Using the final keyword allows us to inheret a protocol without
 * running into problems with inheretting this class. 
 * We can overcome this by defining the class as final. This prevents
 * us from inhereting it from any other class.
 *
 * Note: it would be interesting to see how this is different from
 * using the required keyword on the compiler side.
 */

protocol Person {
  init()
}
final class Human: Person {
  init() {
    print("Created person.")  
  }
}
let b:Human = Human()