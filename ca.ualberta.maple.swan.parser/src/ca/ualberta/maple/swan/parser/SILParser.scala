package ca.ualberta.maple.swan.parser

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util

import ca.ualberta.maple.swan.utils.ExceptionReporter

import scala.collection.mutable.ArrayBuffer

class SILParser {

  /********** THIS CODE IS ORIGINALLY PART OF "Parser" **********************/

  // Default constructor should not be called.
  // I can't think of an elegant way to address the exclusive or
  // of Path/String parameters.

  private[parser] var path: String = _
  private[parser] var chars: Array[Char] = _
  private[parser] var cursor: Int = 0
  def position(): Int = { cursor }

  def this(path: Path) {
    this()
    this.path = path.toString
    val data : Array[Byte] = if (Files.exists(path)) {
      Files.readAllBytes(path: Path)
    } else {
      ExceptionReporter.report(new Error(this.path, "file not found"))
      null
    }: Array[Byte]
    val text = new String(data, StandardCharsets.UTF_8)
    this.chars = text.toCharArray
    skipTrivia()
  }

  def this(s: String) {
    this()
    this.path = "<memory>"
    this.chars = s.toCharArray
    skipTrivia()
  }

  // ***** Token level *****

  protected def peek(query: String): Boolean = {
    assert(!query.isEmpty)
    util.Arrays.equals(
      util.Arrays.copyOfRange(chars, cursor, cursor + query.length),
      query.toCharArray)
  }

  @throws[Error]
  protected def take(query: String): Unit = {
    if (!peek(query)) {
      ExceptionReporter.report(new Error(path, query + " expected"))
    }
    cursor += query.length
    skipTrivia()
  }

  protected def skip(query: String): Boolean = {
    if (!peek(query)) return false
    cursor += query.length
    skipTrivia()
    true
  }

  // TODO finish
  protected def take(/* while fn: (Character) -> Bool */): String = {
    val result : Array[Char] = null  //chars.suffix(from: cursor).prefix(while: fn)
    cursor += result.length
    skipTrivia()
    new String(result)
  }

  // TODO finish
  protected def skip(/* while fn: (Character) -> Bool */): Boolean = {
    val result : String = "" // = take(fn)
    !result.isEmpty
  }

  protected def skipTrivia(): Unit = {
    if (cursor < chars.length) return
    if (Character.isWhitespace(chars(cursor))) {
      cursor += 1
      skipTrivia()
    } else if (skip("//")) {
      while (cursor < chars.length && chars(cursor) != "\n") { // Optimize?
        cursor += 1
      }
      skipTrivia()
    }
  }

  // ***** Tree level *****

  @throws[Error]
  protected def maybeParse[T](f: () => Option[T]) : Option[T] = {
    val savedCursor = cursor
    try {
      val result = f()
      if (result.isEmpty) {
        cursor = savedCursor
      }
      result
    } catch {
      case e : Error => throw e
    }
  }

  @throws[Error]
  protected def parseNullOrMany[T](pre: String, sep: String, suf: String, parseOne: () => T): Option[Array[T]] = {
    if (!peek(pre)) {
      return None
    }
    Some(parseMany(pre, sep, suf, parseOne))
  }

  @throws[Error]
  protected def parseMany[T](pre: String, sep: String, suf: String, parseOne: () => T): Array[T] = {
    take(pre)
    var result = new ArrayBuffer[T]
    if (!peek(suf)) {
      var break = false
      while (!break) {
        val element = parseOne()
        result.append(element)
        if (peek(suf)) {
          break = true
        } else {
          if (!sep.isEmpty) {
            take(sep)
          }
        }
      }
    }
    take(suf)
    result.toArray
  }

  // ***** Error reporting *****

  protected def parseError(message: String, at: Option[Int]): Error = {
    val position = if (at.isDefined) at.get else cursor
    val newlines = null // TODO Swift: chars.enumerated().prefix(position).filter({ $0.element == "\n" })
    val line = 0 // TODO Swift: newlines.count + 1
    val column = 0 // TODO Swift: position - (newlines.last?.offset ?? 0) + 1
    new Error(path, line, column, message)
  }

  class Error(path : String, message : String) extends Exception {
    private[parser] var line : Option[Int] = None
    private[parser] var column : Option[Int] = None

    def this(path : String, line: Int, column : Int, message : String) {
      this(path, message)
      this.line = Some(line)
      this.column = Some(column)
    }

    override def getMessage: String = {
      if (line == null) {
        return path + ": " + message
      }
      if (column == null) {
        return path + ":" + line + ": " + message
      }
      path + ":" + line + ":" + column + ": " + message
    }
  }

  /********** THIS CODE IS ORIGINALLY PART OF "SILParser" *******************/

  // TODO

  @throws[Error]
  def parseModule(): Module = {
    null
  }

  @throws[Error]
  def parseType(): Type = {
    null
  }

}
