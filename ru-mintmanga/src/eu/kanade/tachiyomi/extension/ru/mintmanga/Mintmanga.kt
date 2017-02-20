package eu.kanade.tachiyomi.extension.ru.mintmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class Mintmanga : ParsedHttpSource() {

    override val id: Long = 6

    override val name = "Mintmanga"

    override val baseUrl = "http://mintmanga.com"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/list?sortType=rate&offset=${70 * (page - 1)}&max=70", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/list?sortType=updated&offset=${70 * (page - 1)}&max=70", headers)
    }

    override fun popularMangaSelector() = "div.desc"

    override fun latestUpdatesSelector() = "div.desc"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "a.nextLink"

    override fun latestUpdatesNextPageSelector() = "a.nextLink"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = filters.filterIsInstance<Genre>().map { it.id + arrayOf("=", "=in", "=ex")[it.state] }.joinToString("&")
        return GET("$baseUrl/search?q=$query&$genres", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    // max 200 results
    override fun searchMangaNextPageSelector() = null

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.leftContent").first()

        val manga = SManga.create()
        manga.author = infoElement.select("span.elem_author").first()?.text()
        manga.genre = infoElement.select("span.elem_genre").text().replace(" ,", ",")
        manga.description = infoElement.select("div.manga-description").text()
        manga.status = parseStatus(infoElement.html())
        manga.thumbnail_url = infoElement.select("img").attr("data-full")
        return manga
    }

    private fun parseStatus(element: String): Int {
        when {
            element.contains("<h3>Запрещена публикация произведения по копирайту</h3>") -> return SManga.LICENSED
            element.contains("<h1 class=\"names\"> Сингл") || element.contains("<b>Перевод:</b> завершен") -> return SManga.COMPLETED
            element.contains("<b>Перевод:</b> продолжается") -> return SManga.ONGOING
            else -> return SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = "div.chapters-link tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href") + "?mature=1")
        chapter.name = urlElement.text().replace(" новое", "")
        chapter.date_upload = element.select("td:eq(1)").first()?.text()?.let {
            SimpleDateFormat("dd/MM/yy", Locale.US).parse(it).time
        } ?: 0
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        chapter.chapter_number = -2f
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body().string()
        val beginIndex = html.indexOf("rm_h.init( [")
        val endIndex = html.indexOf("], 0, false);", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)

        val p = Pattern.compile("'.+?','.+?',\".+?\"")
        val m = p.matcher(trimmedHtml)

        val pages = mutableListOf<Page>()

        var i = 0
        while (m.find()) {
            val urlParts = m.group().replace("[\"\']+".toRegex(), "").split(',')
            pages.add(Page(i++, "", urlParts[1] + urlParts[0] + urlParts[2]))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlParse(document: Document) = ""

    private class Genre(name: String, val id: String) : Filter.TriState(name)

    /* [...document.querySelectorAll("tr.advanced_option:nth-child(1) > td:nth-child(3) span.js-link")].map((el,i) => {
    *  const onClick=el.getAttribute('onclick');const id=onClick.substr(31,onClick.length-33);
    *  return `Genre("${el.textContent.trim()}", "${id}")` }).join(',\n')
    *  on http://mintmanga.com/search
    */
    override fun getFilterList() = FilterList(
            Genre("арт", "el_2220"),
            Genre("бара", "el_1353"),
            Genre("боевик", "el_1346"),
            Genre("боевые искусства", "el_1334"),
            Genre("вампиры", "el_1339"),
            Genre("гарем", "el_1333"),
            Genre("гендерная интрига", "el_1347"),
            Genre("героическое фэнтези", "el_1337"),
            Genre("детектив", "el_1343"),
            Genre("дзёсэй", "el_1349"),
            Genre("додзинси", "el_1332"),
            Genre("драма", "el_1310"),
            Genre("игра", "el_5229"),
            Genre("история", "el_1311"),
            Genre("киберпанк", "el_1351"),
            Genre("комедия", "el_1328"),
            Genre("меха", "el_1318"),
            Genre("мистика", "el_1324"),
            Genre("научная фантастика", "el_1325"),
            Genre("повседневность", "el_1327"),
            Genre("постапокалиптика", "el_1342"),
            Genre("приключения", "el_1322"),
            Genre("психология", "el_1335"),
            Genre("романтика", "el_1313"),
            Genre("самурайский боевик", "el_1316"),
            Genre("сверхъестественное", "el_1350"),
            Genre("сёдзё", "el_1314"),
            Genre("сёдзё-ай", "el_1320"),
            Genre("сёнэн", "el_1326"),
            Genre("сёнэн-ай", "el_1330"),
            Genre("спорт", "el_1321"),
            Genre("сэйнэн", "el_1329"),
            Genre("трагедия", "el_1344"),
            Genre("триллер", "el_1341"),
            Genre("ужасы", "el_1317"),
            Genre("фантастика", "el_1331"),
            Genre("фэнтези", "el_1323"),
            Genre("школа", "el_1319"),
            Genre("эротика", "el_1340"),
            Genre("этти", "el_1354"),
            Genre("юри", "el_1315"),
            Genre("яой", "el_1336")
    )
}