import org.jsoup.Jsoup

fun parse(url: String) {
    //1. Fetching the HTML from a given URL
    val doc = Jsoup.connect(url).get()
    val title = doc.select("h1.entry-title")
    val pageQuote = doc.selectFirst(".indent em")
    val pageQuoteSource = doc.selectFirst(".indent .indent")
    val article = doc.select("div#main-article")
    val mainText = article.select("p")
    println(title.text())
    println(pageQuote.text())
    println(pageQuoteSource.text())
    for (para in mainText.eachText()) println(para)
}

fun main(args: Array<String>) {
    if (args.size == 0) {
        println("[!]: No URL passed.")
        return
    }
    parse(args[0])
}