/*
 * Copyright (c) 2021 the SWAN project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This software has dependencies with other licenses.
 * See https://github.com/themaplelab/swan/doc/LICENSE.md.
 */

package ca.ualberta.maple.swan.ir

// https://stackoverflow.com/questions/38243530/custom-exception-in-scala
//
// Dumb thing (arguably) about Scala is there are no checked exceptions.
// Just try to annotate as best as possible. We want interoperability
// with Java. Annoyingly, also need to annotate callers if they don't
// catch callee exceptions. TODO: Find a better way to handle exceptions.
object Exceptions {

  /** Deeper (dataflow semantic) problems such as value never allocated. */
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

  /** Fault of the parsed plain text */
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

  /** Incorrect SWIRL data structure construction */
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

  /** Failures that are not surprising, usually in experimental code. */
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
