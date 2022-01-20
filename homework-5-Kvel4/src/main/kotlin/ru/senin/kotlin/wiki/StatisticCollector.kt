package ru.senin.kotlin.wiki

import java.io.File
import java.io.FileInputStream
import java.time.ZonedDateTime
import java.util.concurrent.*
import javax.xml.parsers.SAXParserFactory
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.lang.Integer.sum

class StatisticCollector(private val inputs: List<File>, private val threads: Int) : AutoCloseable {
    private val executor: ExecutorService = Executors.newFixedThreadPool(threads)

    private val titleStatistic = ConcurrentHashMap<String, Int>()
    private val textStatistic = ConcurrentHashMap<String, Int>()
    private val timeStatistic = ConcurrentHashMap<Int, Int>()
    private val bytesStatistic = ConcurrentHashMap<Int, Int>()

    private val wordRegex = Regex("""[а-я]{3,}""", RegexOption.IGNORE_CASE)
    private val top = 300

    fun collect(): String {
        try {
            val tasks = mutableListOf<Future<out Any>>()

            for (parameter in inputs) {
                val task = {
                    BZip2CompressorInputStream(FileInputStream(parameter)).use {
                        val factory = SAXParserFactory.newInstance()
                        val parser = factory.newSAXParser()

                        parser.parse(it, XMLHandler())
                    }
                }

                tasks += executor.submit(task)
            }

            // invokeAll не прокатит потому что не пробрасывает ошибки
            tasks.await()

            val formatTasks =
                listOf(
                    executor.submit(Callable { formatWordsStatistic(titleStatistic) }),
                    executor.submit(Callable { formatWordsStatistic(textStatistic) }),
                    executor.submit(Callable { formatRangeStatistic(timeStatistic) }),
                    executor.submit(Callable { formatRangeStatistic(bytesStatistic) })
                )

            return formatStatistics(Statistic(formatTasks.await()))
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun <T> List<Future<out T>>.await() = this.map { it.get() }

    private fun formatStatistics(statistic: Statistic) = buildString {
        addLine("Топ-$top слов в заголовках статей:")
        addLine(statistic.topTitles)

        addLine("Топ-$top слов в статьях:")
        addLine(statistic.topTexts)

        addLine("Распределение статей по размеру:")
        addLine(statistic.sizeDistribution)

        addLine("Распределение статей по времени:")
        append(statistic.timeDistribution)
    }

    private fun formatRangeStatistic(rangeStatistic: ConcurrentHashMap<Int, Int>) = buildString {
        if (rangeStatistic.isNotEmpty()) {
            val minElement = rangeStatistic.minOf { it.key }
            val maxElement = rangeStatistic.maxOf { it.key }

            for (key in minElement..maxElement) {
                addLine("$key ${rangeStatistic.getOrDefault(key, 0)}")
            }
        }
    }

    private fun formatWordsStatistic(wordsStatistic: ConcurrentHashMap<String, Int>) = buildString {
        val top = wordsStatistic.top(top, compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        // имхо выглядит хуже чем compareBy ({ -it.second }, { it.first })

        for ((key, value) in top) {
            addLine("$value $key")
        }
    }

    private fun StringBuilder.addLine() = append(System.lineSeparator())

    private fun StringBuilder.addLine(string: String) = append(string).addLine()

    private fun ConcurrentHashMap<String, Int>.top(n: Int, comparator: Comparator<Pair<String, Int>>) =
        this.toList().sortedWith(comparator).take(n)

    private fun addStatistics(page: Page) {
        addWordsStatistic(page.title, titleStatistic)
        addWordsStatistic(page.text, textStatistic)

        timeStatistic.merge(page.time.toInt())

        var bytes = page.bytes
        var key = 0

        while (bytes / 10 > 0) {
            bytes /= 10
            key++
        }

        bytesStatistic.merge(key)
    }

    private fun addWordsStatistic(text: String, wordsStatistic: ConcurrentHashMap<String, Int>) {
        for (word in wordRegex.findAll(text.lowercase())) {
            wordsStatistic.merge(word.value)
        }
    }

    private fun <T> ConcurrentHashMap<T, Int>.merge(key: T) {
        this.merge(key, 1, ::sum)
    }

    override fun close() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    throw IllegalStateException("Thread pool didn't shutdown after timeout")
                }
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            throw e
        }
    }

    private inner class XMLHandler : DefaultHandler() {
        private var isRevision = false
        private var isPage = false
        private var depth = 0

        var title: StringBuilder? = null
        var text: StringBuilder? = null
        var time: String? = null
        var bytes: Int? = null

        private val pageTag = "page"
        private val revisionTag = "revision"
        private val titleTag = "title"
        private val textTag = "text"
        private val timeTag = "timestamp"
        private val bytesTag = "bytes"

        private lateinit var lastElement: String

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (Thread.interrupted()) throw InterruptedException()

            if (isPage && isRevision && depth == 3 && qName == textTag) {
                runCatching {
                    bytes = attributes.getValue(bytesTag)?.toInt()
                }
            }

            lastElement = qName

            when (qName) {
                pageTag -> {
                    isPage = true

                    title = null
                    text = null
                    time = null
                    bytes = null
                }
                revisionTag -> isRevision = true
            }

            depth++
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (Thread.interrupted()) throw InterruptedException()

            if (isPage && depth == 3 && lastElement == titleTag) {
                val tmp = String(ch, start, length)

                if (title == null) title = StringBuilder()
                title?.append(tmp)
            }

            if (isPage && isRevision && depth == 4) {
                if (lastElement == textTag) {
                    val tmp = String(ch, start, length)

                    if (text == null) text = StringBuilder()
                    text?.append(tmp)
                }

                if (lastElement == timeTag) {
                    runCatching {
                        time = ZonedDateTime.parse(String(ch, start, length)).year.toString()
                    }
                }
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (Thread.interrupted()) throw InterruptedException()

            when (qName) {
                pageTag -> {
                    isPage = false

                    if (listOf(title, text, time, bytes).none { it == null }) {
                        addStatistics(Page(title!!.toString(), text!!.toString(), time!!, bytes!!))
                    }
                }
                revisionTag -> isRevision = false
            }
            depth--
        }
    }

    private data class Page(var title: String, var text: String, var time: String, var bytes: Int)

    private data class Statistic(val statistic: List<String>) {
        val topTitles = statistic[0]
        val topTexts = statistic[1]
        val timeDistribution = statistic[2]
        val sizeDistribution = statistic[3]
    }
}
