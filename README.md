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
for your podcast app to consume.

I've been learning Scala these last few months, so I took this as a challenge to see how well Scala could solve this.
And I'm very happy with the result so far. It's very concise, it's asynchronous and will request the pages parallelly,
and the code is fun to write and maintain. The main code is just in this
[1 file](https://github.com/sindux/infoq-rss/blob/master/src/main/scala/code/rss/infoq.scala)

I used [LiftWeb](http://liftweb.net/) to build the app, although I could have used a more lightweight framework.

To start the program, you can clone the repo, start sbt, and connect your podcast to http://yourmachine:8080/video/rss.

Enjoy!
