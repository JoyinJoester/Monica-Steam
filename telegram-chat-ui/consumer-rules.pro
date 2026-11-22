# AndroidSVG can receive the platform KXmlParser through Xml.newPullParser().
# A host dependency (e.g. Simple XML/xpp3 via WebDAV) may also supply these API
# interfaces as program classes. Renaming them breaks the platform boundary.
-keep class org.xmlpull.v1.** { *; }
