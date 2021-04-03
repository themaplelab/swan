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

import ca.ualberta.maple.swan.ir.ModuleGroup
import ca.ualberta.maple.swan.parser.{SILFunction, SILModule}
import ca.ualberta.maple.swan.utils.Logging
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.twitter.chill.ScalaKryoInstantiator
import org.apache.commons.io.IOUtils

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Cache(val silFileChecksums: mutable.HashMap[String, String],
            val functionChecksums: mutable.HashMap[String, mutable.HashMap[String, Int]],
            var group: ModuleGroup) extends Serializable

object Cache {
  def createEmpty(): Cache = {
    new Cache(
      new mutable.HashMap[String, String],
      new mutable.HashMap[String, mutable.HashMap[String, Int]](),
      null)
  }
}

class Persistence(val swanDir: File, val invalidate: Boolean = false) {

  def cacheFile: File = Paths.get(swanDir.getPath, "cache.swan").toFile

  val pool = ScalaKryoInstantiator.defaultPool

  var cache: Cache = _

  val changedSILFiles: ArrayBuffer[File] = ArrayBuffer.empty[File]

  var createdNewCache = false

  init()

  private def init(): Unit = {
    if (invalidate && cacheFile.exists()) {
      cacheFile.delete()
    }
    if (cacheFile.exists()) {
      val startTime = System.nanoTime()
      val fileIn = new FileInputStream(cacheFile)
      this.cache = pool.fromBytes(IOUtils.toByteArray(cacheFile.toURI), classOf[Cache])
      fileIn.close()
      Logging.printTimeStamp(0, startTime, "reading cache", cache.group.functions.length, "functions")
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
    val startTime = System.nanoTime()
    // TODO: Find way to serialize these
    cache.group.ddgs.clear()
    cache.group.functions.foreach(f => f.cfg = null)
    val fileOut = new FileOutputStream(cacheFile)
    fileOut.write(pool.toBytesWithoutClass(cache))
    fileOut.close()
    Logging.printTimeStamp(0, startTime, "writing cache", cache.group.functions.length, "functions")
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

  def updateSILModule(m: SILModule): Unit = {
    val map = new mutable.HashMap[String, Int]()
    m.functions.foreach(f => {
      map.put(f.name.mangled, f.hashCode())
    })
    cache.functionChecksums.put(m.toString, map)
  }

  def updateGroup(group: ModuleGroup): Unit = {
    cache.group = group
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
