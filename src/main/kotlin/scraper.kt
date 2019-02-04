import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.JsonObject
import com.overzealous.remark.IgnoredHtmlElement
import com.overzealous.remark.Options
import com.overzealous.remark.Remark
import org.jsoup.Jsoup
import java.io.File

data class MainPage(val url: String) {
    private val document = Jsoup.connect(url).get()
    private val title = document.selectFirst("h1.entry-title")
    private val pageQuote = document.selectFirst(".indent em")
    private val pageQuoteSource = document.selectFirst(".indent .indent")
    init {
        document.selectFirst("div#main-article").select(".spoiler").tagName("spoiler").removeAttr("title").removeAttr("class")
        document.setBaseUri("https://tvtropes.org/")
        for (link in document.select(".twikilink")) {
            val absoluteUrl = link.attr("abs:href")
            link.attr("href", absoluteUrl)
        }
    }
    private val article = document.selectFirst("div#main-article")
    private val imageElement = document.select("div.quoteright")
    private val image = if (imageElement.isNotEmpty()) imageElement.select("img")[0] else null
    private val imageCaption = document.select("div.acaptionright")
    private val examplesHeader = article.select("h2:not(.comment-title)")
    private val mainText = article.select("#main-article > p,#main-article > h2,#main-article > ul")
    private val exampleFolderHeaders = article.select("div.folderlabel:not([onclick='toggleAllFolders();'])")
    private var exampleFolders = article.select("div.folder")
    private var examples = mutableListOf<Pair<String, String>>()
    private var examplesText = mutableListOf<Pair<String, String>>()
    init {
        for (label in exampleFolderHeaders) {
            val regex = Regex("togglefolder\\('(.*)'\\)")
            val folderID = regex.find(label.attr("onclick"))!!.destructured.toList()[0]
            val folderName = label.text()
            val folder = exampleFolders.select("div[id=$folderID]")[0]
            examples.add(folderName to folder.outerHtml())
            examplesText.add(folderName to folder.text())
        }
    }
    var minimalHtml = ""
    init {
        minimalHtml += title.outerHtml()
        minimalHtml += imageElement.outerHtml()
        minimalHtml += imageCaption.outerHtml()
        minimalHtml += pageQuote.outerHtml()
        minimalHtml += pageQuoteSource.outerHtml()
        minimalHtml += mainText.outerHtml()
        if ( ! minimalHtml.contains(examplesHeader.outerHtml())) minimalHtml += examplesHeader.outerHtml()
        for (example in examples) {
            minimalHtml += example.first
            minimalHtml += example.second
        }
    }
    var pageTextJson = jsonObject(
        "title" to title.text(),
        "imageElement" to imageElement.text(),
        "imageCaption" to imageCaption.text(),
        "pageQuote" to pageQuote.text(),
        "pageQuoteSource" to pageQuoteSource.text(),
        "mainText" to jsonArray(mainText.eachText()),
        "examplesHeader" to examplesHeader.text(),
        "examples" to jsonObject(examplesText)
    )
    var markdown: String
    var minimalMarkdown: String
    var markdownJson: JsonObject
    init {
        val opts = Options.markdown()
        opts.ignoredHtmlElements.add(IgnoredHtmlElement.create("spoiler"))
        opts.inlineLinks = true
        val remark = Remark(opts)
        markdown = remark.convert(document.toString())
        minimalMarkdown = remark.convert(minimalHtml)
        markdownJson = jsonObject(
            "title" to remark.convert(title.outerHtml()),
            "pageQuote" to remark.convert(pageQuote.outerHtml()),
            "pageQuoteSource" to remark.convert(pageQuoteSource.outerHtml()),
            "mainText" to remark.convert(mainText.outerHtml()),
            "examplesHeader" to remark.convert(examplesHeader.outerHtml()),
            "examples" to jsonObject(examples.map { item -> Pair(item.first, remark.convert(item.second)) })
        )
    }
    override fun toString(): String {
        return "MainPage(title='${title.text()}', url='$url')"
    }
}

data class SearchPage(val searchString: String) {
    private val SEARCH_URL = "https://tvtropes.org/pmwiki/elastic_search_result.php"
    private val parameters = hashMapOf<String, String>("q" to searchString, "page_type" to "all")
    private val document = Jsoup.connect(SEARCH_URL).data(parameters).get()
    private val results = document.select(".search-result")
    val minimalHtml = results.outerHtml()
    val minimalMarkdown: String
    init {
        val opts = Options.markdown()
        opts.ignoredHtmlElements.add(IgnoredHtmlElement.create("spoiler"))
        opts.inlineLinks = true
        val remark = Remark(opts)
        minimalMarkdown = remark.convert(minimalHtml)
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("[!]: No URL passed.")
        return
    }
    val page = MainPage(args[0])
    File("page.md").writeText(page.markdown)
    File("pageMin.md").writeText(page.minimalMarkdown)
    File("pageText.json").writeText(page.pageTextJson.toString())
    File("markdown.json").writeText(page.markdownJson.toString())
    File("minimal.html").writeText(page.minimalHtml)
    val searchPage = SearchPage("person of interest")
    File("searchMarkdown.md").writeText(searchPage.minimalMarkdown)
}