# tvtropes-scraper
Parses a TV Tropes page into sections (i.e. page title, article text, categories, etc.), and convert them into plaintext and Markdown. *Not* intended for mass scraping. Written in Kotlin.

I needed a page parser for TV Tropes that would work with all the various types of pages on the site, but couldn't find any that were general enough. This is my attempt at fixing that.  
I've done my best to make sure all relevant content is extracted, including:

* Main article text
* Page quote
* Page image (link only) and image caption
* Trope examples (in their respective folders)
* Subpage links
* Section/index links
* Stinger text

I'm fairly confident that in its current state it should handle most pages on the wiki, but I don't have time for comprehensive testing. Submit an issue if anything doesn't work.

### Some things to keep in mind:

* I wrote this with the intention of using it as the backend for an Android app (which is why it's written in Kotlin), so some of the design choices may seem odd. Feel free to suggest ways to improve it.
* I am completely new to Gradle, so as of this writing I have no idea how to make this installable from a Gradle build script. This will hopefully change soon.
* I am also completely new to Kotlin, so the code is probably full of bad practices or something. Again, feel free to suggest changes, or submit a pull request.
