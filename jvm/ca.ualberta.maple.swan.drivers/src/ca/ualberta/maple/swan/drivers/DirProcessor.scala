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

import java.io.{File, FileReader}
import java.nio.file.Path

import ca.ualberta.maple.swan.parser.SILModuleMetadata
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader

import scala.collection.mutable.ArrayBuffer

class DirProcessor(val path: Path) {

  def process(): ArrayBuffer[File] = {
    val dir = path.toFile
    if (dir.exists() && dir.isDirectory) {
      val metadataFile = new File(dir, "sil-metadata.json")
      // TODO: metafile support
      if ((metadataFile.exists() && metadataFile.isFile) || true) {
        // val metadata = processMetadata(metadataFile)
        val silFiles = new ArrayBuffer[File]
        dir.listFiles().foreach(f => {
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
}
