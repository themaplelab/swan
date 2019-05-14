/* 
 * Switch statement with special binding within cases.
 */

switch(12,110){
    case let(x,y):
        if x > y { print("X is bigger") };
    default: break
}