import com.github.salomonbrys.kotson.jsonArray
import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.JsonObject
import com.overzealous.remark.IgnoredHtmlElement
import com.overzealous.remark.Options
import com.overzealous.remark.Remark
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.File

data class MainPage(val url: String) {
    private val document = Jsoup.connect(url).get()
    init {
        /*
        * Surrounds all spoilers with the <spoiler> tag to be handled separately (since Markdown doesn't support them).
        * Also onverts all TV tropes links to absolute references.
        */
        document.selectFirst("div#main-article").select(".spoiler").tagName("spoiler").removeAttr("title").removeAttr("class")
        document.setBaseUri("https://tvtropes.org/")
        for (link in document.select(".twikilink, .subpage-link, .section-links a")) {
            val absoluteUrl = link.attr("abs:href")
            link.attr("href", absoluteUrl)
        }
        for (link in document.select(".more-subpages option[value*=pmwiki]")) {
            val absoluteUrl = link.attr("abs:value")
            link.attr("value", absoluteUrl)
        }
    }
    private val title = document.selectFirst("h1.entry-title")
    private val article = document.selectFirst("div#main-article")
    private val pageQuote = document.selectFirst("#main-article > div.indent")
    private val pageQuoteSource = document.selectFirst("#main-article > div.indent > div")
    private val imageElement = document.selectFirst("div.quoteright")
    private val image = imageElement?.selectFirst("img")
    private val imageCaption = document.selectFirst("div.acaptionright")
    private val examplesHeader = article.selectFirst("h2:not(.comment-title)")
    private val mainText = article.select("#main-article > p,#main-article > h2,#main-article > ul")
    private val exampleFolderHeaders = article.select("div.folderlabel:not([onclick='toggleAllFolders();'])")
    private val exampleFolders = article.select("div.folder")
    private val examples = mutableListOf<Pair<Element, Element>>()
    private val examplesText = mutableListOf<Pair<String, String>>()
    init {
        /*
        * Creates a list of pairs of folder labels and folder contents
        */
        for (label in exampleFolderHeaders) {
            val regex = Regex("togglefolder\\('(.*)'\\)")
            val folderID = regex.find(label.attr("onclick"))!!.destructured.toList()[0]
            val folder = exampleFolders.select("div[id=$folderID]")[0]
            examples.add(label to folder)
            examplesText.add(label.text() to folder.text())
        }
    }
    private val stinger = Elements()
    init {
        /*
        * Gets the stinger text by grabbing everything in the article that comes after everything in mainText
        * Hacky solution, but given the lack of uniform classes, ids, or even layout, the only reliable one I could
        * come up with.
        */
        var reachedEndOfMainText = false
        for (child in article.children()) {
            if (child === mainText.last()) {
                reachedEndOfMainText = true
                continue
            }
            if (reachedEndOfMainText) {
                stinger.add(child)
            }
        }
    }
    private val alternativeTitles = document.selectFirst(".alt-titles")
    private val sectionLinks = document.selectFirst(".section-links")
    private val sectionLinksTable: Element
    private val subpageLinks: Elements
    private val subpageLinksTable: Element
    init {
        /*
        * Grabs the subpage links, including the ones in the "More" dropdown.
        * Checks if value contains pmwiki to ensure that empty or invalid items are not included.
        */
        subpageLinks = document.select("ul.subpage-links li:not(.more-subpages, .create-subpage) a")
        for (item in document.select("ul.subpage-links li.more-subpages option[value*=pmwiki]")) {
            item.tagName("a").attr("href", item.attr("value"))
            subpageLinks.add(item)
        }
    }
    val minimalHtml: String
    init {
        /*
        * Uses Jsoup to construct an HTML doc that contains only the actual meat of the page.
        */
        val minDoc = Document.createShell(url)
        minDoc.appendChild(title)
        // Puts the subpage links in a table for cleaner formatting.
        subpageLinksTable = minDoc.appendElement("table")
        var subpageLinksTableRow = subpageLinksTable.appendElement("tr")
        for (link in subpageLinks) {
            subpageLinksTableRow.appendElement("td").appendChild(link)
            // Each row is limited to 4 cells. This should probably be made configurable somehow.
            if (subpageLinksTableRow.children().size % 4 == 0) subpageLinksTableRow = subpageLinksTable.appendElement("tr")
        }
        // Puts the image and imagecaption (if they exist) into a right-aligned table to mimic the look and feel of the actual page.
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
        // The examples header will sometimes show up twice, particularly when a page has both links and example folders.
        // This fixes that.
        if ( examplesHeader != null && ! minDoc.children().contains(examplesHeader)) minDoc.appendChild(examplesHeader)
        for (example in examples) {
            minDoc.appendChild(example.first)
            minDoc.appendChild(example.second)
        }
        for (child in stinger) {
            minDoc.appendChild(child)
        }
        if (alternativeTitles != null) minDoc.appendChild(alternativeTitles)
        sectionLinksTable = minDoc.appendElement("table")
        val sectionLinksTableHeader = sectionLinksTable.appendElement("tr")
        for (title in sectionLinks.select(".titles div")) {
            sectionLinksTableHeader.appendElement("th").appendChild(title.selectFirst("h3"))
        }
        for (row in sectionLinks.select(".links ul")) {
            val tableRow = sectionLinksTable.appendElement("tr")
            for (cell in row.select("li")) {
                if (cell.children().isNotEmpty()) tableRow.appendElement("td").appendChild(cell.selectFirst("a"))
                else tableRow.appendElement("td").append("")
            }
        }
        minimalHtml = minDoc.outerHtml()
    }
    // Outputs page text to JSON. Mainly for dev purposes. Will probably remove at some point.
    val pageTextJson = jsonObject(
        "title" to title.text(),
        "image" to image?.outerHtml(),
        "imageCaption" to imageCaption?.text(),
        "pageQuote" to pageQuote?.text(),
        "pageQuoteSource" to pageQuoteSource?.text(),
        "mainText" to jsonArray(mainText.eachText()),
        "examplesHeader" to examplesHeader?.text(),
        "examples" to jsonObject(examplesText),
        "stinger" to stinger.text(),
        "alternativeTitles" to alternativeTitles?.text()
    )
    val minimalMarkdown: String
    val markdownJson: JsonObject
    // Converts the minimal HTML to Markdown. Also puts the MD version of the various sections into a JSON.
    init {
        val opts = Options.markdown()
        opts.ignoredHtmlElements.add(IgnoredHtmlElement.create("spoiler"))
        opts.ignoredHtmlElements.add(IgnoredHtmlElement.create("table", "align"))
        opts.inlineLinks = true
        val remark = Remark(opts)
        minimalMarkdown = remark.convert(minimalHtml)
        markdownJson = jsonObject(
            "title" to remark.convert(title.outerHtml()),
            "image" to if (image != null) remark.convert(image.outerHtml()) else null,
            "imageCaption" to if (imageCaption != null) remark.convert(imageCaption.html()) else null,
            "pageQuote" to if (pageQuote != null) remark.convert(pageQuote.outerHtml()) else null,
            "pageQuoteSource" to if (pageQuoteSource != null) remark.convert(pageQuoteSource.outerHtml()) else null,
            "mainText" to remark.convert(mainText.outerHtml()),
            "examplesHeader" to if (examplesHeader != null) remark.convert(examplesHeader.outerHtml()) else null,
            "examples" to jsonObject(examples.map { item -> Pair(item.first.text(), remark.convert(item.second.outerHtml())) }),
            "stinger" to remark.convert(stinger.outerHtml()),
            "alternativeTitles" to if (alternativeTitles != null) remark.convert(alternativeTitles.outerHtml()) else null,
            "sectionLinks" to remark.convert(sectionLinksTable.outerHtml()),
            "subpageLinks" to remark.convert(subpageLinksTable.outerHtml())
        )
    }
    override fun toString(): String {
        return "MainPage(title='${title.text()}', url='$url')"
    }
}

data class SearchPage(val searchString: String) {
    private val searchUrl = "https://tvtropes.org/pmwiki/elastic_search_result.php"
    private val parameters = hashMapOf("q" to searchString, "page_type" to "all")
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
    /*
    * Commandline arguments can be URLs or search strings. Anything that doesn't start with http:// or https:// is taken
    * as a search string.
    * Can also specify an output directory using the -o/--output-dir flag. The implementation for this is terrible, but
    * it would have been overkill to include a full commandline parser library for such a tiny use case. In any case,
    * commandline usage is not the intended final use case - ideally, the class would be used directly.
    */
    if (args.isEmpty()) {
        println("[!]: No URL(s) passed.")
        return
    }
    var baseDir = ""
    var isOutputDir = false
    for (arg in args) {
        if (arg == "-o" || arg == "--output-dir") {
            isOutputDir = true
            continue
        }
        if (isOutputDir) {
            baseDir = arg
            File(baseDir).mkdirs()
            break
        }
    }
    isOutputDir = false
    for (arg in args){
        if (arg == "-o" || arg == "--output-dir") {
            isOutputDir = true
            continue
        }
        if (isOutputDir) {
            isOutputDir = false
            continue
        }
        if (Regex("^https?://.*").matches(arg)) {
            val page = MainPage(arg)
            val baseFilename = Regex("^https?://.*/(.*)").find(arg)!!.destructured.toList()[0]
            val category = Regex("^https?://.*/(.*)/.*").find(arg)!!.destructured.toList()[0]
            File("$baseDir/${baseFilename}${category}_minimal.md").writeText(page.minimalMarkdown)
            File("$baseDir/${baseFilename}${category}_text.json").writeText(page.pageTextJson.toString())
            File("$baseDir/${baseFilename}${category}_markdown.json").writeText(page.markdownJson.toString())
            File("$baseDir/${baseFilename}${category}_minimal.html").writeText(page.minimalHtml)
        } else {
            val searchPage = SearchPage(arg)
            val baseFilename = arg.replace("\\s".toRegex(), "_")
            File("$baseDir/search_$baseFilename.html").writeText(searchPage.minimalHtml)
            File("$baseDir/search_$baseFilename.md").writeText(searchPage.minimalMarkdown)
        }
    }
}