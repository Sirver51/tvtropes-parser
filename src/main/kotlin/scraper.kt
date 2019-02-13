import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
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
    init {
        document.selectFirst("div#main-article").select(".spoiler").tagName("spoiler").removeAttr("title").removeAttr("class")
        document.setBaseUri("https://tvtropes.org/")
        for (link in document.select(".twikilink")) {
            val absoluteUrl = link.attr("abs:href")
            link.attr("href", absoluteUrl)
        }
    }
    private val title = document.selectFirst("h1.entry-title")
    private val article = document.selectFirst("div#main-article")
    private val pageQuote = document.selectFirst("#main-article > div.indent")
    private val pageQuoteSource = document.selectFirst("#main-article > div.indent > div")
    private val imageElement = document.selectFirst("div.quoteright")
//    private val image = if (imageElement != null) imageElement.selectFirst("img") else null
    private val image = imageElement?.selectFirst("img")
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
        if (image != null) {
            val table = minDoc.appendElement("table").attr("align", "right")
            var row = table.appendElement("tr")
            row.appendElement("td").attr("align", "center").appendChild(image)
            row = table.appendElement("tr")
            if (imageCaption != null) row.appendElement("td").attr("align", "center").append(imageCaption.html())
        }
        if (pageQuote != null) minDoc.appendChild(pageQuote)
        if (pageQuoteSource != null) minDoc.appendChild(pageQuoteSource)
        mainText.forEach { text -> text.appendTo(minDoc) }
        if ( examplesHeader != null && ! minDoc.children().contains(examplesHeader)) minDoc.appendChild(examplesHeader)
        for (example in examples) {
            minDoc.appendChild(example.first)
            minDoc.appendChild(example.second)
        }
        minimalHtml = minDoc.outerHtml()
    }
    val pageTextJson = jsonObject(
        "title" to title.text(),
        "image" to image?.outerHtml(),
        "imageCaption" to imageCaption?.text(),
        "pageQuote" to pageQuote?.text(),
        "pageQuoteSource" to pageQuoteSource?.text(),
        "mainText" to jsonArray(mainText.eachText()),
        "examplesHeader" to examplesHeader?.text(),
        "examples" to jsonObject(examplesText)
    )
    val markdown: String    // Remove for prod
    val minimalMarkdown: String
    val markdownJson: JsonObject
    init {
        val opts = Options.markdown()
        opts.ignoredHtmlElements.add(IgnoredHtmlElement.create("spoiler"))
        opts.ignoredHtmlElements.add(IgnoredHtmlElement.create("table", "align"))
        opts.inlineLinks = true
        val remark = Remark(opts)
        markdown = remark.convert(document.toString())
        minimalMarkdown = remark.convert(minimalHtml)
        markdownJson = jsonObject(
            "title" to remark.convert(title.outerHtml()),
            "image" to if (image != null) remark.convert(image.outerHtml()) else null,
            "imageCaption" to if (imageCaption != null) remark.convert(imageCaption.html()) else null,
            "pageQuote" to if (pageQuote != null) remark.convert(pageQuote.outerHtml()) else null,
            "pageQuoteSource" to if (pageQuoteSource != null) remark.convert(pageQuoteSource.outerHtml()) else null,
            "mainText" to remark.convert(mainText.outerHtml()),
            "examplesHeader" to if (examplesHeader != null) remark.convert(examplesHeader.outerHtml()) else null,
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
        val baseDir = "out/files"
        if (Regex("^https?://.*").matches(arg)) {
            val page = MainPage(arg)
            val baseFilename = Regex("^https?://.*/(.*)").find(arg)!!.destructured.toList()[0]
            File("$baseDir/$baseFilename.md").writeText(page.markdown)
            File("$baseDir/${baseFilename}_minimal.md").writeText(page.minimalMarkdown)
            File("$baseDir/${baseFilename}_text.json").writeText(page.pageTextJson.toString())
            File("$baseDir/${baseFilename}_markdown.json").writeText(page.markdownJson.toString())
            File("$baseDir/${baseFilename}_minimal.html").writeText(page.minimalHtml)
        } else {
            val searchPage = SearchPage(arg)
            val baseFilename = arg.replace("\\s".toRegex(), "_")
            File("$baseDir/search_$baseFilename.html").writeText(searchPage.minimalHtml)
            File("$baseDir/search_$baseFilename.md").writeText(searchPage.minimalMarkdown)
        }
    }
}