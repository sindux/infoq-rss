infoq-rss
=========

InfoQ has a lot of great [presentations](http://www.infoq.com/presentations/).
However their [RSS feed](http://www.infoq.com/feed/presentations) lacks 
the [mp4 media enclosure](http://en.wikipedia.org/wiki/RSS_enclosure)
so your podcast app wouldn't be able to show/download the video directly.

Furthermore, if you visit any of their presentation page using a desktop browser,
it'd give you a Flash version, which makes it even harder to find the video file.
However, if you visit it using an iPad, they'd actually present the mp4 file in HTML5's video tag.

This small app will act as a proxy for infoq RSS feed and add the needed enclosure and media:thumbnail tags
for your podcast app to consume. It'll also add a download link for the PDF (please login to InfoQ website from your default browser first).

Subscribe your podcast to http://hammock.ddns.net/infoq/video/rss and enjoy!

PS. I used to write this in Scala, you can find that version [here](https://github.com/sindux/infoq-rss/tree/da73379c51152dbb04389406670ff7e805bb7795).
