package es.calma.tools

import app.morphe.patcher.apk.ApkMerger
import java.io.File

/** Joins a stock APK's signed resource splits using a generic APK library. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: MergeSplits <split-directory> <output.apk>" }
    val input = File(args[0]).canonicalFile
    val output = File(args[1]).canonicalFile
    require(input.isDirectory) { "Input must be a directory containing the original APK splits" }
    require(!output.toPath().startsWith(input.toPath())) { "Output must be outside the split directory" }
    require(input.resolve("base.apk").isFile) { "The stock base.apk is required" }
    output.parentFile.mkdirs()
    ApkMerger().merge(input, output)
    println("Merged APK: $output")
}
