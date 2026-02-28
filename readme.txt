Ich möchte eine Application bauen, die den rss Heise News Ticker ausliesst (https://www.heise.de/rss/heise.rdf) und anhand meiner Preferenzen Artikel auswählt.

Die Ausgewählten Artikel sollen auf einer Html Seite mit Artikel, Beschreibung und Link/Url angezeigt werden.

Meine Preferenzen stehen in der Datei Artikel-Preferenzen.txt.

Die Entscheidung, ob mich ein Artikel interessiert, soll getroffen werden, indem meine Artikel-Preferenzen.txt Datei zusammen mit dem ausgelesen rss Feed an ein LLM (claude mit API Key) gesendet werden.