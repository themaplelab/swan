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

package ca.ualberta.maple.swan.drivers

import java.io.{File, FileInputStream, FileOutputStream, FileReader}
import java.nio.file.{Files, Paths}

import ca.ualberta.maple.swan.ir.ModuleGroup
import ca.ualberta.maple.swan.parser.SILModuleMetadata
import ca.ualberta.maple.swan.utils.Logging
import com.google.common.hash.Hashing
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.twitter.chill.{KryoPool, ScalaKryoInstantiator}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.jgrapht.graph.{DefaultEdge, SimpleGraph}

import scala.collection.mutable.ArrayBuffer

// TODO: Have separate checksum cache to save space and not have
//  to copy SIL files

/** Processes a swan-dir by reading its SIL files. Caching is experimental. */
class SwanDirProcessor(swanDir: File, options: Driver.Options, clear: Boolean = false, forceRead: Boolean = false) {

  private val cacheDir: File = Paths.get(swanDir.getPath, "cache").toFile
  private val cacheFile: File = Paths.get(cacheDir.getPath, "cache.swan").toFile

  var files: ArrayBuffer[File] = _
  val changedFiles = new ArrayBuffer[File]()
  var hadExistingCache: Boolean = false
  var cachedGroup: ModuleGroup = _
  private var silFilesInSwanDir: ArrayBuffer[File] = _
  var changeDetected = false

  val pool: KryoPool = ScalaKryoInstantiator.defaultPool

  init()

  private def init(): Unit = {
    // Clear if no cache because when the user later turns on
    // caching, the cache can be outdated
    if ((clear || !options.cache) && cacheDir.exists()) { FileUtils.forceDelete(cacheDir) }
    hadExistingCache = cacheDir.exists()
    changeDetected = !cacheDir.exists()
    this.files = {
      silFilesInSwanDir = getFilesFromDir
      if (options.cache && cacheDir.exists()) {
        val startTime = System.nanoTime()
        val files = compare(silFilesInSwanDir)
        Logging.printTimeStampNoRate(0, startTime, "comparing", silFilesInSwanDir.length, "files")
        files
      } else {
        silFilesInSwanDir
      }
    }
    if ((changeDetected && options.cache && cacheFile.exists()) || (cacheFile.exists() && forceRead)) {
      this.cachedGroup = readCache
    }
  }

  // TODO: If non-function information is detected, such as witness tables
  //  or globals, a full parsing needs to be requested.
  //  affected: DDG, fake main
  private def compare(silFiles: ArrayBuffer[File]): ArrayBuffer[File] = {
    val comparedFiles = new ArrayBuffer[File]()
    silFiles.foreach(revised => {
      val comparedFile = {
        val original = Paths.get(cacheDir.getPath, revised.getName).toFile
        if (original.exists()) {
          // First check hash
          if (getChecksum(original) == getChecksum(revised)) {
            null
          } else {
            changedFiles.append(revised)
            changeDetected = true
            revised
          }
        } else {
          revised
        }
      }
      if (comparedFile != null) {
        comparedFiles.append(comparedFile)
      }
    })
    comparedFiles
  }

  // TODO: custom serialization

  /** Writes a module group to the cache in the swan-dir. */
  def writeCache(group: ModuleGroup): Unit = {
    val startTime = System.nanoTime()
    if (!cacheDir.exists()) Files.createDirectories(cacheDir.toPath)
    FileUtils.cleanDirectory(cacheDir)
    group.ddgs.clear()
    group.functions.foreach(f => f.cfg = null)
    val fileOut = new FileOutputStream(cacheFile)
    fileOut.write(pool.toBytesWithoutClass(group))
    fileOut.close()
    silFilesInSwanDir.foreach(f => {
      val copyPath = Paths.get(cacheDir.getPath, f.getName)
      Files.copy(f.toPath, copyPath)
    })
    Logging.printTimeStampNoRate(0, startTime, "writing cache", group.functions.length, "functions")
  }

  /** Reads the cache from the swan-dir. Assumes a cache exists. */
  private def readCache: ModuleGroup = {
    val startTime = System.nanoTime()
    val fileIn = new FileInputStream(cacheFile)
    val group = pool.fromBytes(IOUtils.toByteArray(cacheFile.toURI), classOf[ModuleGroup])
    fileIn.close()
    Logging.printTimeStampNoRate(0, startTime, "reading cache", group.functions.length, "functions")
    group.functions.foreach(f => f.cfg = new SimpleGraph(classOf[DefaultEdge]))
    group
  }

  /** Returns all files inside of the swan-dir. */
  private def getFilesFromDir: ArrayBuffer[File] = {
    if (swanDir.exists() && swanDir.isDirectory) {
      // val metadataFile = new File(swanDir, "sil-metadata.json")
      // TODO: metafile support
      if (/*(metadataFile.exists() && metadataFile.isFile) || */true) {
        // val metadata = processMetadata(metadataFile)
        val silFiles = new ArrayBuffer[File]
        swanDir.listFiles().foreach(f => {
          if (f.getName.endsWith(".sil")) {
            silFiles.append(f)
          }
        })
        silFiles
      } else {
        throw new RuntimeException("Could not get sil-metadata.json in dir")
      }
    } else {
      throw new RuntimeException("Invalid dir given")
    }
  }

  private def processMetadata(file: File): Array[SILModuleMetadata] = {
    val obj = new JsonParser().parse(new JsonReader(new FileReader(file))).getAsJsonObject
    Array.empty // TODO
  }

  override def toString: String = {
    val sb = new StringBuilder()
    if (changedFiles.isEmpty) {
      sb.append("No changed SIL files")
    } else {
      sb.append("\nChanged SIL files : ")
      sb.append(changedFiles.length.toString)
      changedFiles.foreach(f => {
        sb.append("\n  ")
        sb.append(f.getName)
      })
    }
    sb.append("\n")
    sb.toString()
  }

  /** Returns the checksum of a file. */
  private def getChecksum(file: File): String = {
    com.google.common.io.Files.asByteSource(file).hash(Hashing.crc32()).toString
  }

}
