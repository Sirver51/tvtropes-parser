import org.jsoup.Jsoup
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.overzealous.remark.Remark
import com.overzealous.remark.Options
import com.overzealous.remark.IgnoredHtmlElement
import org.jsoup.select.Elements
import java.io.File

data class MainPage(val url: String) {
    private val doc = Jsoup.connect(url).get()
    private val title = doc.selectFirst("h1.entry-title")
    private val pageQuote = doc.selectFirst(".indent em")
    private val pageQuoteSource = doc.selectFirst(".indent .indent")
    private val article = doc.selectFirst("div#main-article").select(".spoiler").tagName("spoiler").removeAttr("title").removeAttr("class")
    val mainText: Elements = article.select("p")
    var pageJson: JsonObject = jsonObject(
        "doc" to doc.toString(),
        "title" to title.toString(),
        "pageQuote" to pageQuote.toString(),
        "pageQuoteSource" to pageQuoteSource.toString(),
        "mainText" to mainText.toString()
    )
    var pageTextJson: JsonObject = jsonObject(
        "title" to title.text(),
        "pageQuote" to pageQuote.text(),
        "pageQuoteSource" to pageQuoteSource.text(),
        "mainText" to jsonArray(mainText.eachText())
        // "mainText" to jsonArray(mainText.eachText(). map{p -> p.replace("[\n\r]", "")})
    )
    var markdown: String
    init {
        val opts = Options.markdown()
        opts.ignoredHtmlElements.add(IgnoredHtmlElement.create("spoiler"))
        markdown = Remark(opts).convert(doc.toString())
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
    File("page.json").writeText(page.pageJson.toString())
    File("pageText.json").writeText(page.pageTextJson.toString())
}