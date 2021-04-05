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

import java.io.{File, FileInputStream, FileOutputStream, FileReader, FileWriter}
import java.nio.file.{Files, Paths}

import ca.ualberta.maple.swan.ir.ModuleGroup
import ca.ualberta.maple.swan.parser.SILModuleMetadata
import ca.ualberta.maple.swan.utils.Logging
import com.github.difflib.DiffUtils
import com.google.common.hash.Hashing
import org.apache.commons.io.FileUtils
import java.nio.file.Files

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.twitter.chill.{KryoPool, ScalaKryoInstantiator}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.jgrapht.graph.{DefaultEdge, SimpleGraph}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class SwanDirProcessor(swanDir: File, options: Driver.Options, clear: Boolean = false) {

  private val cacheDir: File = Paths.get(swanDir.getPath, "cache").toFile
  private val cacheFile: File = Paths.get(cacheDir.getPath, "cache.swan").toFile
  private val debugDir: File = Paths.get(swanDir.getPath, "debug-dir").toFile

  var files: ArrayBuffer[File] = _
  private val changedFiles = new ArrayBuffer[File]()
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
    if (changeDetected && options.cache && cacheFile.exists()) {
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
            val originalContent = Files.readAllLines(original.toPath)
            val revisedContent = Files.readAllLines(revised.toPath)
            val patch = DiffUtils.diff(originalContent, revisedContent)
            // Get all changes lines
            val processed = new mutable.HashSet[Int]()
            val changedLines = new mutable.Stack[Int]
            patch.getDeltas.forEach(delta => {
              // The goal here is just to get every line involved
              // in the difference. Note that pos is line index (starts at 0).
              val srcPos = delta.getSource.getPosition
              val tgtPos = delta.getSource.getPosition
              changedLines.push(srcPos)
              if (srcPos != tgtPos) changedLines.push(tgtPos)
              for ( _ <- Range(1, delta.getTarget.getLines.size())) {
                changedLines.push(tgtPos + 1)
              }
              for ( _ <- Range(1, delta.getSource.getLines.size())) {
                changedLines.push(srcPos + 1)
              }
            })
            if (changedLines.nonEmpty) {
              val sb = new StringBuilder
              while (changedLines.nonEmpty) {
                val line = changedLines.pop()
                if (!processed.contains(line)) {
                  sb.append(processLine(line, revisedContent, processed, revisedContent.size()))
                }
              }
              if (sb.nonEmpty) {
                sb.insert(0, "sil_stage canonical\n\n")
                val changedFile = Paths.get(swanDir.getPath, revised.getName + ".changed").toFile
                val fw = new FileWriter(changedFile)
                fw.write(sb.toString())
                fw.close()
                if (options.debug) {
                  val savedChangedFile = Paths.get(debugDir.getPath, revised.getName + ".changed").toFile
                  val fw = new FileWriter(savedChangedFile)
                  fw.write(sb.toString())
                  fw.close()
                }
                changeDetected = true
                changedFiles.append(changedFile)
                changedFile
              } else { null }
            } else { null }
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

  // TODO: Add complete re-parse triggers for witness tables, globals, etc.
  //   Gets even trickier for deletions. e.g., global deleted.
  private def processLine(lineIdx: Int, content: java.util.List[String], processed: mutable.HashSet[Int], len: Int): String = {
    if (processed.contains(lineIdx)) return ""
    def goUp(i: Int): String = {
      processed.add(i-1)
      content.get(i-1)
    }
    def goDown(i: Int): String = {
      processed.add(i+1)
      content.get(i+1)
    }
    var currIdx = lineIdx
    processed.add(currIdx)
    val sb = new StringBuilder()
    def append(s: String): Unit = { sb.append(s); sb.append("\n") }
    // Need to be at ^sil. Go down, and get init above.
    def grabFunctionBody(): Unit = {
      append(goUp(currIdx))
      append(content.get(currIdx))
      while(!{val s = goDown(currIdx); append(s); s}.startsWith("}")) { currIdx = currIdx + 1}
    }
    var continue = true
    while (continue && currIdx > 0) {
      val str = content.get(currIdx)
      if (str.startsWith("sil ") && str.endsWith("{")) {
        grabFunctionBody()
        continue = false
      } else if (str.startsWith("}")) {
        continue = false
      }
      currIdx = currIdx - 1
    }
    sb.toString()
  }

  def cleanup(): Unit = {
    // Use the debug option if you want to see these after
    changedFiles.foreach(f => f.delete())
  }

  def writeCache(group: ModuleGroup): Unit = {
    val startTime = System.nanoTime()
    if (!cacheDir.exists()) Files.createDirectories(cacheDir.toPath)
    FileUtils.cleanDirectory(cacheDir)
    // TODO: Find way to serialize these graphs
    group.ddgs.clear()
    group.functions.foreach(f => f.cfg = null)
    val fileOut = new FileOutputStream(cacheFile)
    fileOut.write(pool.toBytesWithoutClass(group))
    fileOut.close()
    // Include copying the files in the timer interval because this should be really quick
    silFilesInSwanDir.foreach(f => {
      val copyPath = Paths.get(cacheDir.getPath, f.getName)
      Files.copy(f.toPath, copyPath)
    })
    Logging.printTimeStampNoRate(0, startTime, "writing cache", group.functions.length, "functions")
  }

  private def readCache: ModuleGroup = {
    val startTime = System.nanoTime()
    val fileIn = new FileInputStream(cacheFile)
    val group = pool.fromBytes(IOUtils.toByteArray(cacheFile.toURI), classOf[ModuleGroup])
    fileIn.close()
    Logging.printTimeStampNoRate(0, startTime, "reading cache", group.functions.length, "functions")
    group.functions.foreach(f => f.cfg = new SimpleGraph(classOf[DefaultEdge]))
    group
  }

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

  private def getChecksum(file: File): String = {
    com.google.common.io.Files.asByteSource(file).hash(Hashing.crc32()).toString
  }

}
