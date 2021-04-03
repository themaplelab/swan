/*
 * This source file is part fo the SWAN open-source project.
 *
 * Copyright (c) 2021 the SWAN project authors.
 * Licensed under Apache License v2.0
 *
 * See https://github.com/themaplelab/swan/LICENSE.txt for license information.
 *
 */

package ca.ualberta.maple.swan.drivers

import java.io._
import java.nio.file.Paths

import ca.ualberta.maple.swan.parser.{SILFunction, SILModule}
import com.google.common.hash.Hashing
import com.google.common.io.Files

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Cache(val silFileChecksums: mutable.HashMap[String, String],
            val functionChecksums: mutable.HashMap[String, mutable.HashMap[String, Int]]) extends Serializable

object Cache {
  def createEmpty(): Cache = {
    new Cache(new mutable.HashMap[String, String],
      new mutable.HashMap[String, mutable.HashMap[String, Int]]())
  }
}

class Persistence(val swanDir: File, val invalidate: Boolean = false) {

  def cacheFile: File = Paths.get(swanDir.getPath, "cache.swan").toFile

  var cache: Cache = _

  val changedSILFiles: ArrayBuffer[File] = ArrayBuffer.empty[File]

  var createdNewCache = false

  init()

  private def init(): Unit = {
    if (invalidate && cacheFile.exists()) {
      cacheFile.delete()
    }
    if (cacheFile.exists()) {
      val fileIn = new FileInputStream(cacheFile)
      val in = new ObjectInputStream(fileIn)
      this.cache = in.readObject().asInstanceOf[Cache]
      in.close()
      fileIn.close()
    }
    new DirProcessor(swanDir.toPath).process().foreach(f => {
      val checksum = getChecksum(f)
      if (cache == null) {
        changedSILFiles.append(f)
      } else if (!cache.silFileChecksums.contains(f.getName) ||
                  cache.silFileChecksums(f.getName) != checksum) {
        changedSILFiles.append(f)
      }
      if (cache == null) {
        cache = Cache.createEmpty()
        createdNewCache = true
      }
      cache.silFileChecksums.put(f.getName, checksum)
    })
  }

  def writeCache(): Unit = {
    val fileOut = new FileOutputStream(cacheFile)
    val out = new ObjectOutputStream(fileOut)
    out.writeObject(cache)
    out.close()
    fileOut.close()
  }

  def checkFunctionParity(f: SILFunction, m: SILModule): Boolean = {
    val moduleName = m.toString
    val functionName = f.name.mangled
    if (cache.functionChecksums.contains(moduleName)) {
      val map = cache.functionChecksums(moduleName)
      if (map.contains(functionName)) {
        return map(functionName) == f.hashCode()
      }
    }
    false
  }

  def updateSILModules(modules: ArrayBuffer[SILModule]): Unit = {
    modules.foreach(m => {
      val map = new mutable.HashMap[String, Int]()
      m.functions.foreach(f => {
        map.put(f.name.mangled, f.hashCode())
      })
      cache.functionChecksums.put(m.toString, map)
    })
  }

  override def toString: String = {
    val sb = new StringBuilder()
    sb.append("\nChanged SIL files : ")
    sb.append(changedSILFiles.length.toString)
    changedSILFiles.foreach(f => {
      sb.append("\n  ")
      sb.append(f.getName)
    })
    sb.append("\n")
    sb.toString()
  }

  private def getChecksum(file: File): String = {
    Files.asByteSource(file).hash(Hashing.crc32()).toString
  }
}
