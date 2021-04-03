//
//  ViewController.swift
//  SwanViewer
//
//  Created by Daniil Tiganov on 2021-01-23.
//

import Sourceful
import Cocoa
import ShellOut
import CoreFoundation

struct SWANEditorTheme: SourceCodeTheme {
  
  public init() {}
  
  private static var lineNumbersColor: Color {
    return Color(red: 100/255, green: 100/255, blue: 100/255, alpha: 1.0)
  }
  
  public let lineNumbersStyle: LineNumbersStyle? = LineNumbersStyle(font: Font(name: "Menlo", size: 12)!, textColor: lineNumbersColor)
  
  public let gutterStyle: GutterStyle = GutterStyle(backgroundColor: Color(red: 21/255.0, green: 22/255, blue: 31/255, alpha: 1.0), minimumWidth: 32)
  
  public let font = Font(name: "Menlo", size: 12)!
  
  public let backgroundColor = Color(red: 31/255.0, green: 32/255, blue: 41/255, alpha: 1.0)
  
  public func color(for syntaxColorType: SourceCodeTokenType) -> Color {
      return .white
  }
}

struct Constants {
  static let defaultText = """
        func source() -> String {
            return "I'm bad";
        }

        func sink(sunk: String) {
            print(sunk);
        }

        var strings = [String]();
        strings.append(source());
        sink(sunk : strings[0]);

        """
  static let swiftFile = "\(NSTemporaryDirectory())test.swift"
  static let silFile = "\(swiftFile).sil"
  static let swirlFile = "\(silFile).swirl"
  static let locFile = "\(silFile).loc"
}

struct Location: Hashable {
  let line: Int
  let col: Int
  
  func hash(into hasher: inout Hasher) {
    hasher.combine(line)
    hasher.combine(col)
  }
}

class ViewController: NSViewController {

  let lexer = SwiftLexer()
  
  @IBOutlet weak var swiftTextView: SWANView!
  @IBOutlet weak var silTextView: SWANView!
  @IBOutlet weak var swirlTextView: SWANView!
  
  @IBOutlet weak var buildOptions: NSTextField!
  
  @IBOutlet weak var enableSIL: NSSwitch!
  @IBOutlet weak var enableSWIRL: NSSwitch!
  
  var swiftLocs = [Int: (Location, Location?)]()
  var silLocs = [Int: (Location?, Location?)]()
  var swirlLocs = [Int: (Location, Location?)]()
  
  var lock: Bool = false
  
  override func viewDidLoad() {
    super.viewDidLoad()

    swiftTextView.theme = DefaultSourceCodeTheme()
    swiftTextView.controller = self
    swiftTextView.type = EditorType.Swift
    swiftTextView.delegate = self
    swiftTextView.text = Constants.defaultText
    
    silTextView.theme = SWANEditorTheme()
    silTextView.contentTextView.isEditable = false
    silTextView.controller = self
    silTextView.type = EditorType.SIL
    silTextView.delegate = self
    
    swirlTextView.theme = SWANEditorTheme()
    swirlTextView.contentTextView.isEditable = false
    swirlTextView.controller = self
    swirlTextView.type = EditorType.SWIRL
    swirlTextView.delegate = self
  }
  
  func setRangeFromLocation(loc: Location?, editor: SWANView) {
    if (loc == nil) {
      return
    }
    let lines = editor.text.components(separatedBy: "\n")
    let subLines = lines[0...loc!.line-2]
    let index = subLines.joined().count + subLines.count
    let range = NSMakeRange(index, lines[loc!.line-1].count)
    editor.contentTextView.setSelectedRange(range)
    editor.contentTextView.centerSelectionInVisibleArea(nil)
  }
  
  func updateLocation(type: EditorType, line: Int) {
    self.lock = true
    switch type {
    case .Swift:
      if (swiftLocs[line] != nil) {
        // setRangeFromLocation(loc: Location(line: line, col: 0), editor: swiftTextView)
        setRangeFromLocation(loc: swiftLocs[line]!.0, editor: silTextView)
        setRangeFromLocation(loc: swiftLocs[line]!.1, editor: swirlTextView)
      }
    case .SIL:
      if (silLocs[line] != nil) {
        setRangeFromLocation(loc: Location(line: line, col: 0), editor: silTextView)
        setRangeFromLocation(loc: silLocs[line]!.0, editor: swiftTextView)
        setRangeFromLocation(loc: silLocs[line]!.1, editor: swirlTextView)
      }
    case .SWIRL:
      if (swirlLocs[line] != nil) {
        setRangeFromLocation(loc: Location(line: line, col: 0), editor: swirlTextView)
        setRangeFromLocation(loc: swirlLocs[line]!.0, editor: silTextView)
        setRangeFromLocation(loc: swirlLocs[line]!.1, editor: swiftTextView)
      }
    }
    self.lock = false
  }
  
  @IBAction func toggleSwift(switchButton: NSSwitch) -> Void {
    swiftTextView!.isHidden = switchButton.state == NSControl.StateValue.off
  }
  
  @IBAction func toggleSIL(switchButton: NSSwitch) -> Void {
    silTextView!.isHidden = switchButton.state == NSControl.StateValue.off
  }

  @IBAction func toggleSWIRL(switchButton: NSSwitch) -> Void {
    swirlTextView!.isHidden = switchButton.state == NSControl.StateValue.off
  }
  
  @IBAction func build(button: NSButton) -> Void {
    
    do {
      try swiftTextView!.text.write(toFile: Constants.swiftFile, atomically: true, encoding: String.Encoding.utf8)
    } catch {
      silTextView.text = "Could not write Swift file" + error.localizedDescription
      return
    }
    
    var savedSIL: String?
    
    do {
      let args = (buildOptions.stringValue + " " + Constants.swiftFile).split(separator: " ").map(String.init)
      do {
        try shellOut(to: "swiftc", arguments: args)
      } catch {
        var output = error.localizedDescription
        if (output.contains("sil_stage canonical")) {
          output = output.components(separatedBy: "sil_stage canonical")[1]
          output = output.components(separatedBy: "<unknown>")[0]
          output = "sil_stage canonical" + output
          try output.write(toFile: Constants.silFile, atomically: true, encoding: String.Encoding.utf8)
          savedSIL = output
        } else {
          silTextView.text = error.localizedDescription
          return
        }
      }
      
      let javaArgs = ["-jar", "\(Bundle.main.resourcePath!)/viewer.jar", Constants.silFile]
      try shellOut(to: "java", arguments: javaArgs)
    } catch {
      silTextView.text = error.localizedDescription
      swirlTextView.text = savedSIL!
      return
    }
    
    do {
      let sil = try String(contentsOf: URL.init(fileURLWithPath: Constants.silFile), encoding: .utf8)
      silTextView.text = sil
      
      let swirl = try String(contentsOf: URL.init(fileURLWithPath: Constants.swirlFile), encoding: .utf8)
      swirlTextView.text = swirl
      
    } catch {
      silTextView.text = error.localizedDescription
      return
    }
    
    do {
      let locString = try String(contentsOf: URL.init(fileURLWithPath: Constants.locFile))
      let lines = locString.components(separatedBy: .newlines)
      for line in lines {
        
        if (line.isEmpty) {
          continue
        }
        
        let locs = line.components(separatedBy: ",")
        
        let swiftLoc: Location? = (!locs[0].isEmpty)
          ? Location(
            line: Int.init(locs[0].components(separatedBy: ":")[0])!,
            col: Int.init(locs[0].components(separatedBy: ":")[1])!)
          : nil
        
        let silLoc = Location(
          line: Int.init(locs[1].components(separatedBy: ":")[0])!,
          col: Int.init(locs[1].components(separatedBy: ":")[1])!)
        
        let swirlLoc: Location? = (!locs[2].isEmpty)
          ? Location(
            line: Int.init(locs[2].components(separatedBy: ":")[0])!,
            col: Int.init(locs[2].components(separatedBy: ":")[1])!)
          : nil
        
        if (swiftLoc != nil) {
          swiftLocs[swiftLoc!.line] = (silLoc, swirlLoc)
        }
        
        silLocs[silLoc.line] = (swiftLoc, swirlLoc)
        
        if (swirlLoc != nil) {
          swirlLocs[swirlLoc!.line] = (silLoc, swiftLoc)
        }
        
      }
    } catch {
      silTextView.text = error.localizedDescription
    }
  }
}

enum EditorType {
  case Swift
  case SIL
  case SWIRL
}

extension ViewController: SyntaxTextViewDelegate {
  
  func didChangeText(_ syntaxTextView: SyntaxTextView) {
    let editor = (syntaxTextView as? SWANView)
    if (editor?.type == EditorType.Swift) {
      self.silLocs.removeAll()
      self.swiftLocs.removeAll()
      self.swirlLocs.removeAll()
    }
  }
  
  func didChangeSelectedRange(_ syntaxTextView: SyntaxTextView, selectedRange: NSRange) {
    
    
  }
  
  func lexerForSource(_ source: String) -> Lexer {
    return lexer
  }
  
}

class SWANView: SyntaxTextView {
  
  var controller: ViewController? = nil
  var type: EditorType?

  override func textViewDidChangeSelection(_ notification: Notification) {
    if (controller == nil || type == nil || controller!.lock) {
      return
    }
    let body = self.text
    let range = Range(self.contentTextView.selectedRange(), in: body)
    let line: Int
    if (body.startIndex == range!.upperBound) {
      line = 1
    } else {
      let endIndex = body.index(before: range!.upperBound)
      let substr = String(body[body.startIndex...endIndex])
      line = substr.components(separatedBy: "\n").count
    }
    controller!.updateLocation(type: type!, line: line)
  }
  
}

