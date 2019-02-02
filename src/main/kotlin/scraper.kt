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
    private val examplesHeader = article.select("h2:not(.comment-title)")
    private val mainText = article.select("p,ul,h2:not(.comment-title)")
    private val exampleFolderHeaders = article.select("div.folderlabel").not("[onclick=toggleAllFolders();]")
    private var exampleFolders = article.select("div.folder")
    private var examples = mutableListOf<Pair<String, String>>()
    private var examplesText = mutableListOf<Pair<String, String>>()
    init {
        for (label in exampleFolderHeaders) {
            val regex = Regex("togglefolder\\('(.*)'\\)")
            val folderID = regex.find(label.attr("onclick"))!!.destructured.toList()[0]
            val folderName = label.text()
            val folder = exampleFolders.select("div[id=$folderID]")[0]
            examples.add(folderName to folder.toString())
            examplesText.add(folderName to folder.text())
        }
    }
    var minimalHtml = ""
    init {
        minimalHtml += title
        minimalHtml += pageQuote
        minimalHtml += pageQuoteSource
        minimalHtml += mainText
        if (examples.isNotEmpty()) minimalHtml += examplesHeader
        for (example in examples) {
            minimalHtml += example.first
            minimalHtml += example.second
        }
    }
    var pageJson = jsonObject(
        "title" to title.toString(),
        "pageQuote" to pageQuote.toString(),
        "pageQuoteSource" to pageQuoteSource.toString(),
        "mainText" to mainText.toString(),
        "examplesHeader" to examplesHeader.toString(),
        "examples" to jsonObject(examples)
    )
    var pageTextJson = jsonObject(
        "title" to title.text(),
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
            "title" to remark.convert(title.toString()),
            "pageQuote" to remark.convert(pageQuote.toString()),
            "pageQuoteSource" to remark.convert(pageQuoteSource.toString()),
            "mainText" to remark.convert(mainText.toString()),
            "examplesHeader" to remark.convert(examplesHeader.toString()),
            "examples" to jsonObject(examples.map { item -> Pair(item.first, remark.convert(item.second)) })
        )
    }
    override fun toString(): String {
        return "MainPage(title='${title.text()}', url='$url')"
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
    File("page.json").writeText(page.pageJson.toString())
    File("pageText.json").writeText(page.pageTextJson.toString())
    File("markdown.json").writeText(page.markdownJson.toString())
    File("minimal.html").writeText(page.minimalHtml)
}