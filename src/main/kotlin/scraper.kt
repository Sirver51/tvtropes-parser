import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.overzealous.remark.IgnoredHtmlElement
import com.overzealous.remark.Options
import com.overzealous.remark.Remark
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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
    private val imageElement = document.selectFirst("div.quoteright")
    private val image = if (imageElement != null) imageElement.selectFirst("img") else null
    private val imageCaption = document.selectFirst("div.acaptionright")
    private val examplesHeader = article.selectFirst("h2:not(.comment-title)")
    private val mainText = article.select("#main-article > p,#main-article > h2,#main-article > ul")
    private val exampleFolderHeaders = article.select("div.folderlabel:not([onclick='toggleAllFolders();'])")
    private var exampleFolders = article.select("div.folder")
    private var examples = mutableListOf<Pair<Element, Element>>()
    private var examplesText = mutableListOf<Pair<String, String>>()
    init {
        for (label in exampleFolderHeaders) {
            val regex = Regex("togglefolder\\('(.*)'\\)")
            val folderID = regex.find(label.attr("onclick"))!!.destructured.toList()[0]
            val folder = exampleFolders.select("div[id=$folderID]")[0]
            examples.add(label to folder)
            examplesText.add(label.text() to folder.text())
        }
    }
    val minimalHtml: String
    init {
        val minDoc = Document.createShell(url)
        minDoc.appendChild(title)
        val table = minDoc.appendElement("table")
        table.attr("align", "right")
        var row = table.appendElement("tr")
        row.appendElement("td").appendChild(imageElement)
        row = table.appendElement("tr")
        row.appendElement("td").appendChild(imageCaption)
        minDoc.appendChild(pageQuote)
        minDoc.appendChild(pageQuoteSource)
        mainText.forEach { text -> text.appendTo(minDoc) }
        if ( ! minDoc.children().contains(examplesHeader)) minDoc.appendChild(examplesHeader)
        for (example in examples) {
            minDoc.appendChild(example.first)
            minDoc.appendChild(example.second)
        }
        minimalHtml = minDoc.outerHtml()
    }
    val pageTextJson = jsonObject(
        "title" to title.text(),
        "imageElement" to imageElement.text(),
        "imageCaption" to imageCaption.text(),
        "pageQuote" to pageQuote.text(),
        "pageQuoteSource" to pageQuoteSource.text(),
        "mainText" to jsonArray(mainText.eachText()),
        "examplesHeader" to examplesHeader.text(),
        "examples" to jsonObject(examplesText)
    )
    val markdown: String    // Remove for prod
    val minimalMarkdown: String
    val markdownJson: JsonObject
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
            "examples" to jsonObject(examples.map { item -> Pair(item.first.text(), remark.convert(item.second.outerHtml())) })
        )
    }
    override fun toString(): String {
        return "MainPage(title='${title.text()}', url='$url')"
    }
}

data class SearchPage(val searchString: String) {
    private val searchUrl = "https://tvtropes.org/pmwiki/elastic_search_result.php"
    private val parameters = hashMapOf<String, String>("q" to searchString, "page_type" to "all")
    private val document = Jsoup.connect(searchUrl).data(parameters).get()
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
    for (arg in args){
        if (Regex("^https?://.*").matches(arg)) {
            val page = MainPage(arg)
            val baseFilename = page.pageTextJson.get("title").string.replace("\\s*".toRegex(), "")
            File("$baseFilename.md").writeText(page.markdown)
            File("${baseFilename}_minimal.md").writeText(page.minimalMarkdown)
            File("${baseFilename}_text.json").writeText(page.pageTextJson.toString())
            File("${baseFilename}_markdown.json").writeText(page.markdownJson.toString())
            File("${baseFilename}_minimal.html").writeText(page.minimalHtml)
        } else {
            val searchPage = SearchPage(arg)
            val baseFilename = arg.replace("\\s".toRegex(), "_")
            File("search_$baseFilename.html").writeText(searchPage.minimalHtml)
            File("search_$baseFilename.md").writeText(searchPage.minimalMarkdown)
        }
    }
}