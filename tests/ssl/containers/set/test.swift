// https://developer.apple.com/documentation/swift/set

func source() -> String {
    return "I'm bad";
}

func sink(sunk: String) {
    print(sunk);
}
func test_randomElement1() {
    let src = source();
    let names = ["Zoey", "Chloe", src, "Amani", "Amaia"]; //!testing!source
    let randomName = names.randomElement()!
    sink(sunk : randomName); //!testing!sink
}

// ---------- Transforming ---------

func test_sorted() {
    let src = source();
    let students: Set = ["Kofi", "Abena", src, "Peter", "Kweku", "Akosua"]; //!testing!source
    let sortedStudents = students.sorted();
    sink(sunk : sortedStudents[0]); //!testing!sink
}



//----------- Transforming ------------

//func test_map() {
//    let src = source();
//    let aSet: Set = ["Kofi", "Abena", src, "Peter"]; !testing!source
//    let new_Set: Set = aSet.map { $0.lowercased() };
//    sink(sunk : new_Set.first!); !testing!sink
//}


// ---------- iterating case ----------

// func test_forLoop() {
//    let src = source();
//    let initials : Set = ["a", "b", src,"d"]; !testing!source
//    for elem in initials {
//    sink(sunk : elem); !testing!sink
//    }
//}

//func test_forSorted() {
//    let src = source();
//    let initials : Set = ["a", "b", src,"d"]; !testing!source
//    for elem in initials.sorted() {
//    sink(sunk : elem); !testing!sink
//    }
//}



// ------- Creating -------

func test_init() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", src];
  sink(sunk : aSet.first!); //!testing!sink
}

// ------- Adding ---------

func test_insert() {
  let src = source(); //!testing!source
  var aSet = Set<String>();
  aSet.insert(src);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_update() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c"];
  aSet.update(with: src); 
  sink(sunk : aSet.first!); //!testing!sink
}

//-------------- Removing ----------------

func test_filter() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", "b", src];
  let Set2 = aSet.filter{ $0 != "b" };
  sink(sunk : Set2.first!); //!testing!sink
}

func test_remove() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src]; 
  let removed = aSet.remove("a");
  sink(sunk : removed!); //!testing!sink
}

func test_removeFirst() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src];
  let removed = aSet.removeFirst();
  sink(sunk : removed); //!testing!sink
}

func test_removeAt() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src];
  let removed = aSet.remove( at : aSet.startIndex);
  sink(sunk : removed); //!testing!sink
}

func test_removeAll() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src];
  aSet.removeAll();
  sink(sunk : aSet.first!); //!testing!sink!fp // SWAN-27
}

//--------------Combining----------------

// You can combine sets with arrays
// Test tainted data in array/set combinations
// (not exhaustive)

func test_union() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let aSet1 : Set = ["e", "f", "g"];
  let newSet = aSet.union(aSet1);
  sink(sunk : newSet.first!); //!testing!sink
}

func test_union2() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c"];
  let aSet1 : Set = ["e", "f", "g", src];
  let newSet = aSet.union(aSet1);
  sink(sunk : newSet.first!); //!testing!sink
}

func test_union3() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let arr = ["e", "f", "g"];
  let newSet = aSet.union(arr);
  sink(sunk : newSet.first!); //!testing!sink
}

// FAILING
func test_union4() {
  let src = source(); //!testing!source!fn
  let aSet : Set = ["a", "b", "c"];
  let arr = ["e", "f", "g", src];
  let newSet = aSet.union(arr);
  sink(sunk : newSet.first!); //!testing!sink!fn
}

func test_formUnion1() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src];
  let aSet1 : Set = ["a", "b", "g"];
  aSet.formUnion(aSet1);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formUnion2() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c"];
  let aSet1 : Set = ["a", "b", "g", src];
  aSet.formUnion(aSet1);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formUnion3() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src];
  let arr = ["a", "b", "g"];
  aSet.formUnion(arr);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formUnion4() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c"];
  let arr = ["a", "b", "g", src];
  aSet.formUnion(arr);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_intersection1() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let aSet1 : Set = ["a", "b", "d"];
  let newSet = aSet.intersection(aSet1);
  sink(sunk : newSet.first!); //!testing!sink
}

func test_intersection2() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c"];
  let aSet1 : Set = ["a", "b", "d", src];
  let newSet = aSet.intersection(aSet1);
  sink(sunk : newSet.first!); //!testing!sink
}

func test_intersection3() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let arr = ["a", "b", "d"];
  let newSet = aSet.intersection(arr);
  sink(sunk : newSet.first!); //!testing!sink
}

// FAILING
func test_intersection4() {
  let src = source(); //!testing!source!fn
  let aSet : Set = ["a", "b", "c"];
  let arr = ["a", "b", "d", src];
  let newSet = aSet.intersection(arr);
  sink(sunk : newSet.first!); //!testing!sink!fn
}

func test_formIntersection1() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src]; 
  let aSet1 : Set = ["a", "b", "d"];
  aSet.formIntersection(aSet1);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formIntersection2() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c"]; 
  let aSet1 : Set = ["a", "b", "d", src];
  aSet.formIntersection(aSet1);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formIntersection3() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src]; 
  let arr = ["a", "b", "d"];
  aSet.formIntersection(arr);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formIntersection4() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c"]; 
  let arr = ["a", "b", "d", src];
  aSet.formIntersection(arr);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_symmetricDifference1() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let aSet1 : Set = ["a", "b", "d"];
  let newSet = aSet.symmetricDifference(aSet1);
  sink(sunk : newSet.first!); //!testing!sink
}

func test_symmetricDifference2() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c"];
  let aSet1 : Set = ["a", "b", "d", src];
  let newSet = aSet.symmetricDifference(aSet1);
  sink(sunk : newSet.first!); //!testing!sink
}

func test_symmetricDifference3() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let arr = ["a", "b", "d"];
  let newSet = aSet.symmetricDifference(arr);
  sink(sunk : newSet.first!); //!testing!sink
}

// FAILING
func test_symmetricDifference4() {
  let src = source(); //!testing!source!fn
  let aSet : Set = ["a", "b", "c"];
  let arr = ["a", "b", "d", src];
  let newSet = aSet.symmetricDifference(arr);
  sink(sunk : newSet.first!); //!testing!sink!fn
}

func test_formSymmetricDifference1() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src]; 
  let aSet1 : Set = ["a", "b", "d"];
  aSet.formSymmetricDifference(aSet1);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formSymmetricDifference2() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c"]; 
  let aSet1 : Set = ["a", "b", "d", src];
  aSet.formSymmetricDifference(aSet1);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formSymmetricDifference3() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src]; 
  let arr = ["a", "b", "d"];
  aSet.formSymmetricDifference(arr);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_formSymmetricDifference4() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c"]; 
  let arr = ["a", "b", "d", src];
  aSet.formSymmetricDifference(arr);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_subtract1() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src]; 
  let aSet1 : Set = ["a", "b", "d"];
  aSet.subtract(aSet1);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_subtract2() {
  let src = source(); // no source
  var aSet : Set = ["a", "b", "c"];
  let aSet1 : Set = ["a", "b", "d", src];
  aSet.subtract(aSet1);
  sink(sunk : aSet.first!); // no sink
}

func test_subtract3() {
  let src = source(); //!testing!source
  var aSet : Set = ["a", "b", "c", src]; 
  let arr = ["a", "b", "d"];
  aSet.subtract(arr);
  sink(sunk : aSet.first!); //!testing!sink
}

func test_subtract4() {
  let src = source(); // no source
  var aSet : Set = ["a", "b", "c"];
  let arr = ["a", "b", "d", src];
  aSet.subtract(arr);
  sink(sunk : aSet.first!); // no sink
}

func test_subtracting1() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let arr = ["a", "b", "d"];
  let new_set = aSet.subtracting(arr);
  sink(sunk : new_set.first!); //!testing!sink
}

func test_subtracting2() {
  let src = source(); //!testing!source
  let aSet : Set = ["a", "b", "c", src];
  let aSet1 : Set = ["a", "b", "d"];
  let new_set = aSet.subtracting(aSet1);
  sink(sunk : new_set.first!); //!testing!sink
}

// ...
