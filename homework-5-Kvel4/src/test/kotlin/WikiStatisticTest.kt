package ru.senin.kotlin.wiki

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@Disabled
class WikiStatisticTest {
    companion object {
        private const val WIKI_TEST_PATH = "src/test/resources/wikiTestData/20211101"
        private const val WORDS_STATISTIC_PATH = "wiki-word-statistic.txt"
        private const val DURATION_STATISTIC_PATH = "wiki-duration-statistic.txt"

        private val files =
            listOf(
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current1.xml-p1p224167.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current2.xml-p224168p1042043.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current3.xml-p1042044p2198269.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current4.xml-p2198270p3698269.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current4.xml-p3698270p3835772.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current5.xml-p3835773p5335772.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current5.xml-p5335773p6585765.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current6.xml-p6585766p8085765.bz2"),
                File(WIKI_TEST_PATH, "ruwiki-20211101-pages-meta-current6.xml-p8085766p9047290.bz2")
            )
    }

    @Test
    @OptIn(ExperimentalTime::class)
    fun main() {
        val file = File(WIKI_TEST_PATH, DURATION_STATISTIC_PATH)

        for (threads in listOf(4, 2, 1)) {
            val duration = measureTime {
                collectStatistic(files, File(WIKI_TEST_PATH, WORDS_STATISTIC_PATH).path, threads)
            }

            println("$threads threads execution processed")
            file.appendText("Threads = $threads, Seconds = ${duration.inWholeSeconds}\n")
        }
    }
}
