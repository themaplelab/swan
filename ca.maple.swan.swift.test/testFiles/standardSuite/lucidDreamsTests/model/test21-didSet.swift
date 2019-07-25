/* didSet & willSet
 *
 * These two keywords help us run pieces of code right before a variable
 * is initialized and right after it is initialized.
 */

func f()->Int{
  print("Calling function f.")
  return 25
}
var dream: Int?{
  willSet{
    print("The dream is about to be set.")
  }
  didSet{
    print("The dream was just set.")
  }
}
dream = f()
print("Done")



