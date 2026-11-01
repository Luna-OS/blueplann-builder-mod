# Blueprint Planner (Fabric · Minecraft 26.3)

Ein **rein clientseitiger** Mod, der dir einen Bau- und Redstone-Sandkasten wie den Sandbox- bzw. Blueprint-Editor aus Factorio gibt. Dazu kommt ein eigenes Blueprint-System à la Litematica.

* Mit einem Hotkey wechselst du in den **Planner**, eine eigene kleine Welt rund um deine aktuelle Position. Dort bist du im Creative-Modus, und **Redstone funktioniert 1:1 wie in Vanilla**.
* Was du baust, **bleibt gespeichert**: nach dem Verlassen, nach dem Ausloggen und nach einem Neustart.
* Bekommst du in der echten Welt **Schaden**, fliegst du sofort aus dem Planner zurück.
* Fertige Bauten speicherst du als **Blueprint** und zeigst sie in der echten Welt als **Hologramm** an. Dazu gibt es eine Materialliste, Fehleranzeige, „Easy Place" und, wo erlaubt, direktes Bauen.
* Dazu kommen **einfache WorldEdit-Werkzeuge** ohne Commands: Füllen, Ersetzen, Kopieren/Einfügen, Verschieben, Stapeln, Rückgängig.

Auf dem Server muss nichts installiert sein. Der Mod funktioniert auf Vanilla-, Paper- und Fabric-Servern genauso wie im Singleplayer.

---

## Installation

1. Fabric Loader ≥ 0.20 für **Minecraft 26.3** installieren.
2. **Fabric API** (für 26.3) in den `mods`-Ordner legen.
3. `blueprint-planner-1.0.5.jar` in den `mods`-Ordner legen.
4. Java 25 (bringt der Minecraft-Launcher mit).

## Tasten (änderbar unter *Optionen → Steuerung → Blueprint Planner*)

| Taste | Funktion |
|---|---|
| **B** | Planner betreten / verlassen |
| **N** | Planner-Einstellungen (Tick-Rate & Co.) |
| **M** | Bau-Werkzeuge (WorldEdit ohne Commands) |
| **U** | Blueprints & Platzierungen |
| **[** / **]** | Auswahl Ecke 1 / Ecke 2 (Block im Fadenkreuz, bis 128 Blöcke weit) |
| **K** | Hologramm drehen (das getragene, sonst das ausgewählte; dreht um die Mitte) |
| **J** | Easy Place an/aus |
| **Bild ↑** / **Bild ↓** | Hologramm-Ebene höher / tiefer (schaltet den Ebenen-Modus ein) |
| **Pos 1** | Ebenen-Modus an/aus |
| Holzaxt (im Planner) | Linksklick = Ecke 1, Rechtsklick = Ecke 2 |
| **Stock** (echte Welt) | Rechtsklick = Hologramm aufheben / absetzen · Linksklick = auswählen · Schleichen + Mausrad = 1 Block schieben |

> Die Standardtasten sind bewusst nicht P/O/G: Die sind in 26.3 schon belegt (Soziale Interaktionen, Freunde, Quick Actions).

---

## Der Planner

### So funktioniert er

Der Planner ist **kein Fake**. Im Client startet unsichtbar ein zweiter, echter Minecraft-Server, genau wie im Singleplayer. Deshalb verhält sich Redstone exakt wie in Vanilla, mit Update-Reihenfolge, Quasi-Connectivity, 0-Tick-Verhalten, Pistons, Observern und Hoppern.

Die Planner-Welt nutzt **dieselben Koordinaten wie die echte Welt**. Du planst also direkt an der Stelle, an der du stehst. Beim ersten Betreten einer Gegend wird das Gelände um dich herum aus der echten Welt in den Planner **kopiert**: was dein Client geladen hat, standardmäßig 24 Blöcke Radius. Alternativ bekommst du eine Schachbrett-Bauplatte, deren Felder den Chunks entsprechen. Bereiche, die schon einmal vorbereitet wurden, werden nie wieder überschrieben. Deine Bauten bleiben also erhalten.

Während du im Planner bist:
* bleibt deine echte Spielfigur stehen, wo sie war, und die Verbindung zum Server bleibt bestehen (Keep-Alive, Chat, Chunks laufen weiter),
* landen Chat-Nachrichten weiterhin auf dem echten Server (abschaltbar), **Commands** wie `/tick`, `/fill` oder `/gamerule` gehen dagegen an den Planner,
* hörst du die Geräusche aus der echten Welt weiter, zum Beispiel einen Creeper hinter dir.

### Rausflug-Regeln

Du wirst automatisch zurück in die echte Welt gesetzt, wenn
* deine echte Figur **Schaden** nimmt (Schwelle einstellbar, Standard: jeder Schaden),
* du in der echten Welt stirbst, die Dimension wechselst oder der Server dir ein Menü öffnet,
* du die Verbindung trennst oder gekickt wirst. Der Planner wird dann vorher gespeichert und sauber beendet.

Betreten kannst du den Planner nur, wenn du auf dem Boden stehst (abschaltbar). Sonst würde der Server deine stehende Figur fürs „Schweben" kicken.

### Einstellungen (Taste **N**)

**Simulation & Redstone**
* **Tick-Rate** von 1 bis 1000 TPS (Zeitlupe oder Zeitraffer), plus „20 TPS (normal)"
* **Einfrieren / Fortsetzen**, **+1 Tick**, **+10 Ticks** (Einzelschritt), **Sprint 1200 Ticks**
* Random Tick Speed, Tageszyklus, Wetterzyklus, Tageszeit (Morgen/Mittag/Nacht/Mitternacht), Wetter klar
* Mob-Spawning, TNT, Block-Drops, Mob-Griefing, Ranken, Entity-Cramming
* Items oder alle Entities entfernen

**Optionen**
* Bei Schaden raus (an/aus) und ab welcher Schadenshöhe, „Nur am Boden", Pause, wenn du weg bist
* Nachtsicht im Planner, **Signalstärke über Redstone-Staub anzeigen** (plus Repeater-Delay und Comparator-Modus)
* Umgebung kopieren (an/aus, Radius, Tiefe, Höhe) oder Größe der Bauplatte. Kopiert werden ganze Chunks, und zwar immer **bis unter den tiefsten Boden** in dem Bereich (auch wenn du fliegst oder in einer Höhle bist) und bis über den höchsten Hügel. Unter dem kopierten Teil liegt eine Bedrock-Schicht, damit nichts ins Leere läuft. Während du im Planner herumläufst, werden weitere Chunks nachkopiert, soweit dein Client sie aus der echten Welt geladen hat; ohne Kopie wächst die Bauplatte mit.
* „Umgebung neu kopieren" bzw. „Bereich leeren" für die Chunks um dich herum
* **Himmel**: Normal, **Bauplan** (Standard, blauer Himmel ohne Wolken), Sternennacht oder Studio (hell). So siehst du sofort, dass du im Planner bist. Shaderpacks zeichnen oft ihren eigenen Himmel.
* **Geladene Chunks** und **Aktive Chunks** (je 2–32 oder Auto): wie viele Chunks der Planner um dich herum geladen hält und in welchem Umkreis Redstone, Wasser, Pflanzen und Mobs weiterlaufen. Für große Maschinen hochdrehen; die geladenen Chunks werden dabei automatisch mit hochgezogen. Sehen kannst du höchstens so weit wie deine normale Sichtweite.
* Mod-Schutz (siehe unten)
* **Stock verschiebt**: Hologramme mit einem Stock verschieben (siehe „Verschieben mit dem Stock")

Die Tick-Rate wird pro Planner-Welt gespeichert. Die Planner-Welt friert ein, während du in der echten Welt bist, und läuft beim Zurückkommen dort weiter, wo sie war.

### Wo die Daten liegen

```
.minecraft/blueprintplanner/
├── worlds/<server>_<dimension>/   ← Planner-Welten (normale Minecraft-Welten)
├── blueprints/*.nbt               ← Blueprints (Vanilla-Structure-Format)
└── placements/<server>.json       ← Hologramme pro Welt
.minecraft/config/blueprintplanner.json
```

Es gibt eine Planner-Welt **pro Server/Welt und Dimension**. Nether-Pläne landen also nicht in deiner Overworld-Planung.

---

## Bau-Werkzeuge (WorldEdit ohne Commands, Taste **M**)

Funktionieren im Planner auf der aktuellen Auswahl:

* **Block A** = Item in der Haupthand, **Block B** = Item in der Nebenhand
* **Füllen** (A) · **Ersetzen** B → A · **Leeren** · **Wände** · **Hülle** (hohler Quader) · **Rahmen** (nur Kanten)
* **Kopieren** · **Ausschneiden** · **Einfügen** am Blickpunkt (mit oder ohne Luft). Mit Holzaxt oder leerer Hand siehst du eine grüne Vorschau, wo eingefügt wird.
* Die Ablage lässt sich **drehen** (90°) und **spiegeln**
* **Verschieben** und **Stapeln** in Blickrichtung, um 1–64 (praktisch für Redstone-Linien)
* **Rückgängig / Wiederholen** (30 Schritte)

Blöcke werden wie bei Vanillas `/fill` gesetzt: Nachbarn bekommen danach ein Update, Redstone reagiert also.

---

## Blueprints („Litematica selfmade", Taste **U**)

### Speichern
Bereich mit Holzaxt oder `[`/`]` markieren, dann im Werkzeug-Menü auf **„Auswahl als Blueprint speichern"**.
* Aus dem **Planner** gespeichert: inklusive aller Daten wie Kisteninhalte, Spender, Schilder und Comparator-Werte. Standardmäßig wird der Blueprint auch gleich **an genau der Stelle als Hologramm** in die echte Welt gesetzt, an der du ihn geplant hast.
* Aus der **echten Welt** gespeichert: alles, was dein Client kennt (Block-States vollständig, Block-Entity-Daten nur teilweise).

Blueprints sind **Vanilla-Structure-Dateien (`.nbt`)**. Du kannst sie also auch mit Strukturblöcken laden und Strukturblock-Dateien als Blueprint verwenden.

### Hologramm in der echten Welt
* **Fehlende Blöcke** erscheinen als kleine Geister-Blöcke
* **Rot** = falscher Block · **Orange** = richtiger Block, falsche Ausrichtung · **Magenta** = Block, wo Luft sein soll (nur mit „Luft: an")
* Platzierungen: drehen (**K**), spiegeln, „hierher" verschieben, in 1er-Schritten X/Y/Z verschieben, ein-/ausblenden, als Auswahl übernehmen
* Hologramme werden pro Server/Welt gespeichert

### Platzieren: zielen oder in einen Bereich
In der Bibliothek (**U**) hat jeder Blueprint drei Wege:
* **Platzieren** (in der echten Welt): Das Menü geht zu und das Hologramm **hängt an deinem Fadenkreuz** (gelber Rahmen). Schau auf die Stelle, an die es soll (bis 128 Blöcke weit), dann **Rechtsklick = absetzen**, **Linksklick = abbrechen**. Auf dem Boden steht es auf dem angezielten Block, an einer Wand liegt es davor, unter einer Decke hängt es darunter. **Schleichen + Mausrad** hebt oder senkt es (z. B. für einen Keller), **K** dreht es. Wasseroberflächen zählen als Boden. Esc bricht ebenfalls ab. Wurde der Blueprint in dieser Welt gespeichert (oder im Planner dieser Welt), setzt **Mittelklick** ihn genau an die Stelle, von der er stammt.
* **In Auswahl**: Markier vorher mit `[` und `]` einen Bereich (geht auch von weit weg). Der Blueprint wird **unten bündig und mittig** hineingesetzt. Passt er nur um 90° gedreht genau hinein, wird er gedreht. Ist nur eine Ecke gesetzt, kommt er mittig auf diesen einen Block.
* **→ Planner** (nur im Planner): wie „Platzieren", aber der Rechtsklick fügt den Blueprint direkt in die Planner-Welt ein (mit Rückgängig).

Im Planner (und als Zuschauer) legt „Platzieren" das Hologramm für die echte Welt sofort an, ohne Zielen: an der Stelle, von der der Blueprint stammt, sonst vor dir. Verschieben kannst du es danach in der echten Welt.

In der Liste **Platzierungen** gibt es dieselben zwei Wege für ein vorhandenes Hologramm: **Verschieben (Fadenkreuz)** und **In Auswahl setzen**.

### Verschieben mit dem Stock
Nimm einen normalen **Stock** in die Hand (nur in der echten Welt und nur, wenn es dort Hologramme gibt):
* Das Hologramm unter deinem Fadenkreuz wird **weiß umrahmt**.
* **Rechtsklick**: hebt es auf, es folgt dann deinem Blick. Noch ein **Rechtsklick** setzt es ab. Schaust du auf kein Hologramm, wird das ausgewählte geholt.
* **Linksklick**: wählt das Hologramm aus, das du anschaust. Beim Tragen bringt Linksklick es an die alte Stelle zurück.
* **Schleichen + Mausrad**: schiebt das ausgewählte Hologramm 1 Block in deine Blickrichtung (hoch/runter, wenn du steil schaust). Beim Tragen ändert es die Höhe.
* **K**: dreht um die Mitte.

Solange der Stock aktiv ist, baut oder benutzt er nichts (keine Hebel, Türen usw.). Oben links steht, welche Taste was macht. Ausschalten kannst du das in den Einstellungen (**Stock verschiebt**).

### Ebene für Ebene nachbauen
Mit **Pos 1** (oder dem Knopf „Ebenen-Modus“ in der Platzierungsliste) zeigt das ausgewählte Hologramm **nur noch eine Höhe**: Geister-Blöcke und Fehlermarkierungen gibt es dann nur in dieser Ebene, der Rest des Hologramms ist nur als blasser Rahmen zu sehen, die aktuelle Ebene als oranger Rahmen. Mit **Bild ↑ / Bild ↓** gehst du eine Ebene höher oder tiefer. Oben links steht, welche Ebene du gerade baust (z. B. „Ebene y=64 (3/12)“).
* Die **Materialliste** zeigt im Ebenen-Modus nur, was für diese Ebene fehlt (umschaltbar auf das ganze Hologramm).
* **Direkt bauen** baut im Ebenen-Modus nur diese Ebene.
* Easy Place funktioniert weiter in allen Ebenen.

### Materialliste
Zeigt pro Item: **gesamt**, **fehlt noch** (mit der Welt verglichen) und **dabei** (Inventar). Doppelte Slabs, Türen, Betten, Kerzen usw. werden richtig gezählt.

### Easy Place (Taste **J**)
Ist es aktiv und du setzt einen Block in eine Hologramm-Position, bekommt der Block **automatisch die geplante Ausrichtung**: Repeater, Comparator, Observer, Piston, Hopper, Treppen, Slabs, Logs, Hebel, Knöpfe und so weiter. Der Mod probiert dafür aus, mit welcher Blickrichtung und welcher angeklickten Seite Vanilla genau diesen Zustand erzeugt, und schickt die Platzierung so an den Server. Ein **falscher Block** an einer Plan-Position wird verhindert.
Was man erst nach dem Setzen per Klick einstellt (Repeater-Verzögerung, Comparator-Modus, Notenblock-Ton, zweite Slab-Hälfte), bleibt danach orange markiert, bis es stimmt.

### Direkt bauen
* **Singleplayer:** sofort und exakt (inklusive Block-Entity-Daten)
* **Server mit OP-Rechten:** per `/setblock` bzw. `/fill` (gleiche Blöcke in einer Reihe werden zusammengefasst), von unten nach oben, gedrosselt (Befehle pro Tick in der Config)
* **Ohne Rechte:** Hologramm, Materialliste und Easy Place nutzen

---

## Kompatibilität mit anderen Mods

### Abschirmung („Mod-Schutz“, standardmäßig an)

Der Planner ist ein zweiter, versteckter Server mit einer zweiten Verbindung im selben Spiel. Viele Mods rechnen aber nur mit *einer* Welt: Sobald irgendwo ein Server startet oder eine Verbindung aufgebaut wird, halten sie das für einen Weltwechsel. Deshalb schirmt der Planner sich ab:

* Event-Listener anderer Mods laufen **nicht** für den Planner-Server (Server-Start/-Stopp, Welt laden, Chunks, Ticks, Spieler-Join usw.).
* Verbindungs-Events (Login, Konfiguration, Join, Disconnect, Kanal-Registrierung) der Planner-Verbindung sehen andere Mods ebenfalls nicht.
* Für Mods, die sich direkt per Mixin einhängen, gibt es eigene Schutzhaken (siehe unten).
* Stürzt der Planner-Server ab (z. B. durch eine andere Mod), stürzt **nicht mehr das ganze Spiel** ab. Du bekommst eine Meldung, die echte Welt läuft weiter, und der Bericht liegt in `crash-reports/`.

Die Abschirmung lässt sich unter *Einstellungen (N) → Optionen → Mod-Schutz* abschalten. Die Änderung gilt ab dem nächsten Start des Planners, also nach dem Verlassen der Welt. Abschalten ist nur sinnvoll, wenn eine Mod unbedingt im Planner mitlaufen soll.

### Getestete Mods

Getestet mit einem End-to-End-Test, bei dem alle diese Mods gleichzeitig geladen sind. Jede Mod wurde außerdem im Code auf Probleme mit einem zweiten Server und einer zweiten Verbindung geprüft. Wo nötig, hat Blueprint Planner eigene Schutzhaken, die nur aktiv sind, wenn die Mod installiert ist.

* **Distant Horizons 3.3.4** mit **Sodium 0.9.2**, **Iris 1.11.7** und Shadern: Der Absturz `LodUtil$AssertFailureException: Assertion failed` (in `DhClientServerWorld.getOrLoadLevel`) ist behoben. DH hatte den Start des Planner-Servers für einen Singleplayer-Start gehalten.
  * DH arbeitet im Hintergrund normal mit der echten Welt weiter. Planner-Chunks landen nie in der LOD-Datenbank der echten Welt.
  * Im Planner werden keine LODs der echten Welt gezeichnet. Mit DH-fähigen Shaderpacks (z. B. Complementary + Euphoria Patches) erschien vorher ein eingefrorenes „Geisterbild“ der echten LOD-Landschaft im Planner-Himmel. Das ist behoben.
* **Iris + Shader** (getestet mit Complementary Reimagined r5.9.3): Planner, Redstone, Hologramme und Geister-Blöcke funktionieren. Iris-Meldungen beim Einloggen erscheinen nicht doppelt.
  * Kleine optische Einschränkung: Iris hat für Minecrafts gefüllte Debug-Boxen keinen Shader. Iris schreibt dazu einmal „Missing program minecraft:pipeline/debug_filled_box … likely an Iris bug“ ins Log, und die halbtransparenten Flächen der Hologramm-Boxen fehlen mit Shadern. Umrisse und Geister-Blöcke sind sichtbar.
* **Bobby 5.2.16**: Für den Planner legt Bobby keinen Chunk-Cache an. Vorher teilten sich alle Planner-Welten einen Ordner, und es tauchten Geister-Chunks auf. Bobbys „View Distance Overwrite“ bremst den Planner-Server nicht mehr.
* **Flashback 0.43.6**: Eine laufende Aufnahme pausiert automatisch, während du im Planner bist, und läuft danach weiter. Planner-Daten landen nie in der Aufnahme. Vorher stürzte das Spiel mit Aufnahme + Planner ab. Eine Aufnahme kannst du nicht im Planner starten, und während einer Wiedergabe gibt es keinen Planner.
* **Axiom 6.1.3**: Im Planner ist Axiom aus. Vorher landeten Axiom-Bearbeitungen im Planner in Singleplayer direkt in der echten Welt. Laufende Axiom-Aktionen in der echten Welt warten, bis du zurück bist.
* **Plasmo Voice 2.2.0**: Dein Voice-Chat bleibt immer mit der echten Welt verbunden. Vorher hat Plasmo Voice dich in Singleplayer/LAN in die Planner-Welt „verschoben“.
* **LambDynamicLights 4.13.0**: Dynamische Lichter wandern nicht mehr zwischen Planner und echter Welt hin und her.
* **ViaFabricPlus 5.1.2**: Auf Servern mit älterer Version stürzte das Spiel im Planner beim ersten Klick ab. Der Planner benutzt jetzt immer das aktuelle Protokoll. Rein optisch bleibt: alte Hitboxen (z. B. Zäune bei 1.8) gelten auch im Planner.
* **Roughly Enough Items 26.3**: REI bleibt bei den Rezepten und Daten der echten Welt. Der Planner-Login löst keinen REI-Neuaufbau mehr aus.
* **Xaero's Minimap 26.6.2** und **Xaero's World Map 1.47.2**: Vorher hat Xaero den Planner bei jedem Betreten sofort wieder beendet („Planner connection closed: Disconnected“). Xaero richtet seine Serverdaten über ein Startereignis ein, das der Mod-Schutz für den Planner ausblendet, aber sein Spieler-Beitritt lief trotzdem, fand keine Daten und warf den Planner-Spieler raus. Das ist behoben.
  * Minimap und Weltkarte bleiben bei der echten Welt: Ihre Sitzung wird beim Betreten und Verlassen nicht mehr beendet, und Planner-Gelände landet nicht in der Karte der echten Welt.
  * Im Planner ist die Minimap ausgeblendet. Die Weltkarte (M) zeigt dort weiter die Karte der echten Welt.
  * Der „Client shutdown watchdog“-Absturz beim Schließen des Spiels hing damit zusammen: Jeder abgebrochene Planner-Besuch hinterließ eine verwaiste Xaero-Kartensitzung, deren Thread weiterlief. Der Planner legt jetzt keine solche Sitzung mehr an; in den Tests mit Xaero beendet sich das Spiel normal.
* **Simple Voice Chat 2.6.24**: Der Voice-Chat bleibt mit dem echten Server verbunden. Stummschalten und Gruppen funktionieren auch im Planner (die Steuerpakete gingen sonst an den Planner statt an den Server), und Spieler in deiner Nähe hörst du im Planner weiter, so als stündest du an deiner echten Position. Ausnahme: Teleportiert dich der Server, während du im Planner bist, stimmt die Entfernung der Stimmen erst nach dem Verlassen wieder.
* **JEI 31.17**: JEI startet beim Betreten des Planners nicht mehr neu (vorher jedes Mal, mit der Meldung „This fabric server does not have JEI installed“) und behält Rezepte und Daten der echten Welt. Items aus JEIs Liste im Cheat-Modus funktionieren im Planner, auch verzauberte Bücher (vorher konnte das den Planner beenden).
* **Jade, spark, EntityCulling, ImmediatelyFast, Better Block Entities, Continuity**: geprüft, keine Änderungen nötig. Jade zeigt im Planner keine Server-Infos (z. B. Kisteninhalt), wenn der echte Server kein Jade hat.
* **Litematica / MaLiLib**: **nicht kompatibel**. MaLiLib hält den Planner für einen Weltwechsel. Litematica kann dabei die Platzierungen deiner echten Welt überschreiben und in Singleplayer aus dem Planner heraus die echte Welt verändern. Blueprint Planner warnt dich beim ersten Betreten. Am besten Litematica und MaLiLib entfernen, denn Blueprint Planner hat Hologramme, Materialliste und Easy Place selbst.
* **Lu Hub 1.0.5** (Nachthimmel-Theme, HUD, F3-Panels): getestet und kompatibel. Lu Hubs Theme gilt auch für die Planner-Menüs.
  * Hinweis: Im Planner bist du im Creative-Modus, deshalb zeigt Lu Hub dort (wie Vanilla) keine Herzen und keinen Hunger an. Wenn du den Planner selbst auf Survival stellst (F3+F4), können Lu Hubs Herzen eine Weile weiterblinken. Das ist ein Lu-Hub-Effekt, der genauso nach einem Portal oder Tod auftritt, und ist rein optisch.
* Nicht prüfbar, weil nicht öffentlich verfügbar: Luna Map und die NoRisk-Client-Module. Falls eine davon Probleme macht, schick mir den Crash-Report bzw. das Log.

### Doppelt belegte Tasten

Liegt eine andere Mod auf derselben Taste wie der Planner, löst Minecraft beide aus. Öffnet die andere Mod dabei ein Fenster, geht der Planner-Tastendruck verloren. Beispiele: Xaero's Minimap nutzt **B** (neuer Wegpunkt) und **U** (Wegpunkte), Xaero's World Map **M** (Karte) und **]**, Simple Voice Chat **M** und **N**. Blueprint Planner weist dich nach dem ersten Betreten einer Welt einmal im Chat darauf hin. Ändere dann eine der Tasten unter *Optionen → Steuerung*.

### Wenn der Planner unerwartet zugeht

Wirft der Planner-Server dich selbst raus (meist wegen einer anderen Mod), steht der Grund jetzt im Chat: „Der Planner wurde vom eigenen Server beendet: …“. Vorher stand dort oft nur „Disconnected“.

## Bekannte Einschränkungen

* Der Planner ist ein zweiter Server im Client und braucht etwas mehr RAM (gut 0,5–1 GB) und CPU.
* Mods, die sich per Mixin direkt in Minecraft einhängen und dabei nur mit *einer* Welt rechnen, kann die Abschirmung nicht automatisch erfassen. Für die Mods oben gibt es deshalb eigene Schutzhaken. Falls eine andere Mod im Planner Probleme macht, schick mir den Crash-Report bzw. das Log.
* Die Planner-Welt nutzt die Vanilla-Datapacks. Auf stark modifizierten Servern mit eigenen Blöcken sind diese im Planner nicht vorhanden.
* Easy Place sendet die nötige Blickrichtung kurz vor dem Platzieren an den Server. Auf Servern mit sehr strengem Anti-Cheat kann das auffallen, deshalb ist Easy Place standardmäßig aus.
* Entities wie Item-Frames oder Rüstungsständer werden in Blueprints nicht gespeichert.
* `/publish` im Planner öffnet die Planner-Welt nicht für andere. „Im LAN öffnen“ im Pausenmenü öffnet wie gewohnt deine echte Singleplayer-Welt.

---

## Selbst bauen

```bash
./gradlew build
```

Benötigt JDK 25. Das Jar landet in `build/libs/`.

### Automatischer End-to-End-Test

```bash
./scripts/autotest.sh
```

Das Skript startet einen echten Vanilla-Dedicated-Server (eigener Prozess) und einen Client unter Xvfb. Der Testtreiber (`src/autotest`) arbeitet dann folgende Schritte ab:

* Planner per Taste betreten, Redstone mit eingefrorenen Ticks und Einzelschritten prüfen
* Füllen, Rückgängig und Stapeln testen
* Blueprint speichern, Hologramm und Materialliste prüfen, Easy Place (Repeater-Ausrichtung) testen
* Kopierte Umgebung: ganze Chunks bis zum Bedrock, Nachladen beim Herumlaufen; Ebenen-Modus per Tasten, Materialliste und Anzeige nur für die Ebene
* Stock: Hologramm anvisieren, per Rechtsklick aufheben, zum Fadenkreuz ziehen, mit Schleichen + Mausrad anheben, per Linksklick abbrechen, absetzen und schieben (über die echten Maus-Eingabewege)
* Bibliothek: „Platzieren" per Zielen, „In Auswahl" (mittig und gedreht), im Planner „→ Planner" per Zielen einfügen und rückgängig machen
* Mit Xaero's Minimap/World Map, Simple Voice Chat und JEI: Planner betreten und verlassen, ohne dass die Sitzungen von Xaero und die Laufzeit von JEI wechseln, Erkennung doppelt belegter Tasten
* Rauswurf durch Schaden, direktes Bauen per Commands
* Persistenz nach Trennen und Neuverbinden
* Singleplayer mit zwei Servern gleichzeitig

Das Ergebnis steht in `build/run/autotest/autotest-result.txt`, die Screenshots in `build/run/autotest/screenshots/`.

Andere Mods (mit `:` getrennt) und ein Shaderpack für Iris lassen sich gleich mittesten:

```bash
EXTRA_MODS=/pfad/DistantHorizons.jar:/pfad/sodium.jar:/pfad/iris.jar:/pfad/luhub-1.0.5.jar \
SHADERPACK=/pfad/ComplementaryReimagined_r5.9.3.zip ./scripts/autotest.sh
```

Zusätzlich prüft der Test:
* mit einer Test-„Fremd-Mod" (`src/autotest/java/probe`), dass andere Mods weder Start, Ticks und Befehls-Registrierung des Planner-Servers noch Join oder Disconnect der Planner-Verbindung mitbekommen, die echten Welten aber ganz normal sehen,
* dass alle Schutzhaken für die installierten Mods ihr Ziel finden. Im Test sind sie Pflicht (`-PstrictCompat`), im normalen Jar optional.
* dass keine Mixin-Fehler von Blueprint Planner im Log stehen,
* mit Flashback eine laufende Aufnahme, die im Planner pausiert und danach weiterläuft, sowie Bobby (kein Cache für den Planner), Himmel und Chunk-Radius.

## Technischer Überblick

| Teil | Datei |
|---|---|
| Unsichtbarer Planner-Server (Unterklasse von `IntegratedServer`) | `planner/PlannerServer.java` |
| Welt anlegen/laden (Void-Flat, Creative) | `planner/PlannerWorlds.java` |
| Zwei Client-Kontexte (echte Welt ↔ Planner) umschalten | `planner/ContextSwitcher.java` |
| Lebenszyklus, Rauswurf, Paket-Routing | `planner/PlannerManager.java` |
| Pakete der Hintergrund-Welt im richtigen Kontext verarbeiten | `mixin/PacketProcessorMixin.java` |
| Abschirmung vor anderen Mods (Fabric-Events) | `compat/PlannerIsolation.java`, `mixin/FabricEventMixin.java`, `mixin/ArrayBackedEventMixin.java` |
| Schutzhaken für andere Mods (nur aktiv, wenn die Mod installiert ist) | `compat/mixin/*`, `compat/*Compat.java`, `blueprintplanner.compat.mixins.json` |
| Fabric-Verbindungs-Events der Planner-Verbindung verstecken | `mixin/FabricClientNetworkAddonMixin.java` |
| Blueprint-Format, Bibliothek, Hologramme, Easy Place | `blueprint/*` |
| Rendering (Gizmos + Geister-Blöcke) | `render/OverlayRenderer.java` |
