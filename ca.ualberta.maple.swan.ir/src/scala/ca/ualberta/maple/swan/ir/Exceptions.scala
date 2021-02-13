/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2020 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.ir

// https://stackoverflow.com/questions/38243530/custom-exception-in-scala
//
// Dumb thing about Scala is there are no checked exceptions.
// Just try to annotate as best as possible. We want interoperability
// with Java. Annoyingly, also need to annotate callers if they don't
// catch callee exceptions. TODO: Find a better way to handle exceptions.
object Exceptions {

  // Deeper (dataflow semantic) problems such as value never allocated.
  class IncompleteRawSWIRLException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() = {
      this(null: String)
    }
  }

  class IncorrectRawSWIRLException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() = {
      this(null: String)
    }
  }

  // Fault of the parsed plain text
  class UnexpectedSILFormatException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() = {
      this(null: String)
    }
  }

  // Incorrect SWIRL data structure construction
  class IncorrectSWIRLStructureException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() = {
      this(null: String)
    }
  }

  // Failures that are not surprising, usually in experimental code.
  class ExperimentalException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() = {
      this(null: String)
    }
  }

  // SIL types are complicated. Throw this exception when we see something
  // unexpected with SIL types. These can be used as "interesting guards".
  // These guards should be removed for production once enough testing
  // has been done.
  class UnexpectedSILTypeBehaviourException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() = {
      this(null: String)
    }
  }

  class SymbolTableException(message: String) extends Exception(message) {
    def this(message: String, cause: Throwable) = {
      this(message)
      initCause(cause)
    }

    def this(cause: Throwable) = {
      this(Option(cause).map(_.toString).orNull, cause)
    }

    def this() = {
      this(null: String)
    }
  }

}
