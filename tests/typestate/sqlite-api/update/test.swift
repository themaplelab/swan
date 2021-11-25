import Foundation
import SQLite3

class DBHelper {
    
  init() {
    db = openDatabase()
  }
    
  let dbPath: String = "myDb.sqlite"
  var db:OpaquePointer?
    
  func openDatabase() -> OpaquePointer? {
    let fileURL = try! FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false)
      .appendingPathComponent(dbPath)
    var db: OpaquePointer? = nil
    if sqlite3_open(fileURL.path, &db) != SQLITE_OK {
      print("error opening database")
      return nil
    } else {
      print("Successfully opened connection to database at \(dbPath)")
      return db
    }
  }
  
  func insert(insertStatementString:String) {
    var insertStatement: OpaquePointer? = nil
    if sqlite3_prepare_v2(db, insertStatementString, -1, &insertStatement, nil) == SQLITE_OK { 
      if sqlite3_step(insertStatement) == SQLITE_DONE {
        print(" Successfully inserted row.")
      } else {
        print("Could not insert row.")
      }
    } else {
      print("INSERT statement could not be prepared.")
    }
    sqlite3_finalize(insertStatement)
  }
  
  func update(updateStatementString:String) {
    var updateStatement: OpaquePointer?
    if sqlite3_prepare_v2(db, updateStatementString, -1, &updateStatement, nil) == SQLITE_OK {
      if sqlite3_step(updateStatement) == SQLITE_DONE {
        print(" Successfully updated row.")
      } else {
        print("\nCould not update row.")
      }
    } else {
      print("\nUPDATE statement is not prepared")
    }
    sqlite3_finalize(updateStatement)
  }

  func read(readStatementString:String) -> [String] {
    var queryStatement: OpaquePointer? = nil
    var psns : [String] = []
    if sqlite3_prepare_v2(db, readStatementString, -1, &queryStatement, nil) == SQLITE_OK {
      while sqlite3_step(queryStatement) == SQLITE_ROW {
        let person = String(describing: String(cString: sqlite3_column_text(queryStatement, 1)))
        psns.append(person)
        print("Query Result:")
        print(person)
      }
    } else {
      print("SELECT statement could not be prepared")
    }
    sqlite3_finalize(queryStatement)
    return psns
  }
}

func test_simple_update() {
  let db:DBHelper = DBHelper()
  db.update(updateStatementString: "SQL_QUERY"); //?DBHelperCounter?UPDATE1
}

func test_interprocedural_update() {

  func foo1(updateStatementString:String, db: DBHelper) {
    foo2(updateStatementString: updateStatementString,db: db);
  }

  func foo2(updateStatementString:String, db: DBHelper) {
    db.update(updateStatementString: updateStatementString); //?DBHelperCounter?UPDATE1
  }

  let db:DBHelper = DBHelper()
  foo1(updateStatementString: "SQL_QUERY", db: db);
}

func test_simple_unused_update() {
  let db:DBHelper = DBHelper()
  db.read(readStatementString: "SQL_QUERY");
}

func test_interprocedural_unsused_update() {

  func foo1(updateStatementString:String, db: DBHelper) {
    foo2(updateStatementString: updateStatementString,db: db);
  }

  func foo2(updateStatementString:String, db: DBHelper) {
    db.read(readStatementString: updateStatementString);
  }

  let db:DBHelper = DBHelper()
  foo1(updateStatementString: "SQL_QUERY", db: db);
}

func test_simple_update_frequency() {
  let db:DBHelper = DBHelper()
  db.update(updateStatementString: "SQL_QUERY");
  db.update(updateStatementString: "SQL_QUERY");
  db.update(updateStatementString: "SQL_QUERY");
  db.update(updateStatementString: "SQL_QUERY"); //?DBHelperCounter?UPDATE4
}

func test_interprocedural_update_frequency() {

  func foo1(updateStatementString:String, db: DBHelper) {
    foo2(updateStatementString: updateStatementString,db: db);
    foo2(updateStatementString: updateStatementString,db: db);
    foo2(updateStatementString: updateStatementString,db: db);
    foo2(updateStatementString: updateStatementString,db: db);
  }

  func foo2(updateStatementString:String, db: DBHelper) {
    db.update(updateStatementString: updateStatementString); //?DBHelperCounter?UPDATE4
  }

  let db:DBHelper = DBHelper()
  foo1(updateStatementString: "SQL_QUERY", db: db);
}
