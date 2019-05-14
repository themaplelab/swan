/*logical AND/OR
 *
 * Testing short circuit evaluation with logical ANDs and ORs
 */
 

let c0 = true
let c1 = false
let c2 = true

//c2 will not be evaluated since c1 is the first false in a series of and conditions
if c0 && c1 && c2 {
    print("c0 and c1 and c2 are true")
}

//c1 and c2 will not be evaluated since c0 is the first true in a series of or 
//conditions
if c0 || c1 || c2 {
    print("c0 or c1 or c2 is true")
}