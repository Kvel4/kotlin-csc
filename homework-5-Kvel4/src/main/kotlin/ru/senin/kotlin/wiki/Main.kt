package ru.senin.kotlin.wiki

import com.apurebase.arkenv.Arkenv
import com.apurebase.arkenv.argument
import com.apurebase.arkenv.parse
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class Parameters : Arkenv() {
    val inputs: List<File> by
        argument("--inputs") {
            description =
                "Path(s) to bzip2 archived XML file(s) with WikiMedia dump. Comma separated."
            mapping = { it.split(",").map { name -> File(name) } }

            validate("File does not exist or cannot be read") {
                it.all { file -> file.exists() && file.isFile && file.canRead() }
            }
        }

    val output: String by
        argument("--output") {
            description = "Report output file"
            defaultValue = { "statistics.txt" }
        }

    val threads: Int by
        argument("--threads") {
            description = "Number of threads"
            defaultValue = { 4 }
            validate("Number of threads must be in 1..32") { it in 1..32 }
        }
}

@OptIn(ExperimentalTime::class)
fun main(args: Array<String>) {
    try {
        val parameters = Parameters().parse(args)

        if (parameters.help) {
            println(parameters.toString())
            return
        }

        val duration = measureTime {
            collectStatistic(parameters.inputs, parameters.output, parameters.threads)
        }

        println("Time: ${duration.inWholeMilliseconds} ms")
    } catch (e: Exception) {
        println("Error! ${e.message}")
        throw e
    }
}

fun collectStatistic(inputs: List<File>, output: String, threads: Int) {
    StatisticCollector(inputs, threads).use {
//        runCatching {
            val statistic = it.collect()
            File(output).writeText(statistic)
//        }.onFailure {
//            println("Statistic collecting was interrupted")
//        }
    }
}
