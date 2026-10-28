# Zangbundel manual

*In het Nederlands: [Handleiding](Handleiding)*

Zangbundel is a songbook for the campfire. One phone is the **campfire leader**: it picks the songs and sends the lyrics over Bluetooth to every phone nearby. The others are **singers**: they see the lyrics the leader sends. During the campfire no internet, no Wi-Fi and no Bluetooth pairing is needed.

The app itself contains no lyrics. Songs come from **libraries** that the leader adds; each library has its own licence. To sing along you need no library: the leader sends every song.

The app shows its screens in Dutch or English, following the phone's language unless you choose one under **Settings → Language**. This manual names the English labels; the [Dutch manual](Handleiding) has the Dutch ones. It belongs to version 0.2.0.

---

## Contents

1. [Installing](#1-installing)
2. [The first time](#2-the-first-time)
3. [Adding songs](#3-adding-songs)
4. [The songbook](#4-the-songbook)
5. [Setlists](#5-setlists)
6. [Leading a campfire](#6-leading-a-campfire)
7. [Singing along](#7-singing-along)
8. [Memories](#8-memories)
9. [Settings](#9-settings)
10. [About and privacy](#10-about-and-privacy)
11. [When something goes wrong](#11-when-something-goes-wrong)

---

## 1. Installing

Zangbundel is an Android app (Android 8 or newer). Install it from Google Play, preferably at home before the camp: installing needs internet.

Someone does not have the app yet? **About → Zangbundel on another phone** shows a QR code with the link to the app; **Share the link** sends the link. During a campfire the leader finds the same in the leader bar under **Install the app**.

**Without internet** the app can go from phone to phone: choose **Share without internet** on that screen and follow the steps. You share the installation file over Quick Share or Bluetooth. A copy shared this way is genuine when the fingerprint under **About → Signature** is `57:86:7B:…:9B:10`.

## 2. The first time

The first time you open the app you see **How it works**, with the button **What happens to my data?**. Tap **Start**.

The home screen then asks to **Allow Bluetooth**. Android asks for permission to find nearby devices; the app needs it to sing along and to start a campfire. The app reads no location. On Android 11 and older Android does ask for location permission, because searching for Bluetooth devices works that way there. When Bluetooth is off, the app offers **Switch Bluetooth on**.

## 3. Adding songs

Only the leader needs songs. Go to **Songbook → Libraries**.

- **Add Zangbundel bibliotheek**: the app's own library of campfire and children's songs, downloaded from GitHub.
- **Add a library → Online**: type the web address of a library, a release index or a `.zbl` file and tap **Look up**. The app shows the version and size; tap **Download**.
- **Add a library → From a file**: a `.zbl` or `.zip` file already on the phone, handy on a campsite without internet. Tap **Open a file**.

The app then shows the library's **licence**. Only after **Accept** do the songs go into the songbook. Songs the app cannot read are skipped; the report says which and why.

For each library you can:

- switch its songs **On** or **Off** in the songbook;
- **Update** to the newest version; the app shows what changed;
- switch on **Check automatically on Wi-Fi**: once a day, on Wi-Fi only, and nothing is downloaded until you tap **Update**;
- **Remove** it.

If the app refuses a library with *This library needs a newer version of the app*, update the app.

## 4. The songbook

**Songbook** on the home screen shows every song of the libraries that are on.

- **Search** searches titles; with **Also in the lyrics** the lyrics too.
- The buttons under the search field filter, for example by language or kind of song; **A–Z** changes the order.
- Tap a song to read it.
- **Swipe** to the next or previous song: to the left for the next, to the right for the previous, in the order of the list as you saw it (with your search, filters and sort). It stops at the first and the last song.
- At the bottom are three **round buttons**: **‹** previous song, **ⓘ** song info (words and music, licence, source) and **›** next song. They fade when you leave them alone for a few seconds; a touch brings them back. A long press on the title shows the song info too.
- A song opened from a memory is a single song: you do not swipe there.
- From a song you add it to a setlist.

## 5. Setlists

A setlist is the list of songs for an evening. Go to **Songbook → Setlists**.

- **New setlist** makes an empty list; fill it from the songbook with **Add to setlist…**.
- Drag a song by its handle to change the order.
- **Rename**, **Duplicate** and **Delete** act on the whole list; the songs stay in the songbook.
- **Export** shares the setlist as a `.zbs` file; **Import** reads such a file or its web address.
- **From libraries** shows setlists that come with a library; those are read-only.

A song of the setlist that is not in your libraries is listed as *not found*; it fills in once its library is added.

## 6. Leading a campfire

### Before you start

Switch **battery saver off** and bring a **power bank**: a campfire lasts hours, and battery saver can stop the broadcast. The start screen reminds you.

### Starting

1. On the home screen tap **Start a campfire**. The app first listens for 5 seconds for a campfire nearby. If there is one, the app says *A campfire is already running*: sing along there, or move out of range.
2. On the start screen choose:
   - **Name of the campfire** (proposed: *Campfire* with the date);
   - **Who may join**:
     - **Everyone nearby**: singers tap the campfire in their list;
     - **With a join code**: four digits you say aloud;
     - **Only with the QR code**: singers scan the QR code on your screen, so nobody else gets in. Recommended for young groups;
   - a **setlist**, or **No setlist**.
3. Tap **Start**. The first time, the app explains that a notification shows while the campfire runs, and Android asks whether that is allowed. Refusing is fine: the campfire works exactly the same.

### The leader's screen

You see the same lyrics as the singers. The setlist is at the top; each song starts with a bar with **Now**.

- **Now** sends that song to all singers. It gets an orange stripe and *being sung*; earlier songs show *sung*.
- **Clear** on the song being sung: nothing is sung for a moment. Singers keep the lyrics, with *cleared* in their bar.
- Without a setlist you see **Choose a song from the songbook** with **Open the songbook**.
- The round buttons at the bottom right (**▲** previous song, **≡** list, **▼** next song) move only your own screen; they send nothing.

**≡** opens the sheet with two tabs:

- **Setlist**: the song being sung and the sung songs stay put; you move the next songs by their handle or swipe them away (briefly **Undo**). Every change goes to the singers at once.
- **Songbook**: search by title. **Add** puts the song at the end of the setlist; **Sing now** puts it right after the current song and sends it at once.

### The leader bar

The thin bar at the top shows the name, **the two words**, the number of singers and the time. Tap it (or pull it down) to open it:

- **Time**, **Songs**, **Signed in** (with the highest number) and **Reach** (*good*, *fair* or *weak*);
- **The two words of this campfire**: large, to say aloud. Singers see the same words next to the campfire's name; a phone set to another language shows them translated (*kalf-harp* becomes *calf-harp*);
- **Join code** (when you chose one) and **Show QR code**: the QR code large on a white panel; tap to close;
- **Install the app**: the link to the app for whoever does not have it;
- **Share statistics**, when you keep statistics (see [Settings](#9-settings));
- a ⚠ in the bar when **battery saver** is on or **another campfire is nearby**; the open bar says which;
- **End the campfire**.

### Ending

**End the campfire → Close**: singers see that the campfire is over, and the lyrics disappear from their phones. You see a summary (time, songs, most singers at once) and tap **Back to start**.

### If the app stops

If the app stops while you lead, open it again within 10 minutes: you get **Resume** (go on with the same singers and songs) or **Start over**.

## 7. Singing along

### Joining

- **From the list**: open the app. Under *Campfires nearby* each campfire shows its two words and how close it is. Tap it. If it shows a lock, type the **join code** the leader says.
- **With the QR code**: scan the QR code on the leader's screen with your **camera app**. Zangbundel opens and looks for the campfire. If you do not have the app yet, a web page opens that leads to Google Play.

Two campfires with nearly the same name? Ask the leader for **the two words**. If your phone is set to another language than the leader's, you see the same words translated.

### The singer's screen

The thin bar at the top shows the campfire's name, the two words and whether you are **connected**.

- **Tap the two words** to read what they mean.
- **Tap the bar** for the toolbar: **Day**/**Night**, **Smaller**/**Larger**, **Fit width** (the lyrics fit the width), **Song info**, the brightness, **Keep the screen on** and **Follow the leader**.
- **Scrolling**: drag, or tap the lower half (one line on) or the upper half (one line back).
- **Follow the leader** is on when you join: with every new song your screen jumps to its start. If you scroll yourself, it pauses; the leader's next song switches it back on.
- The round buttons: **▲** previous song, **≡** the song list, **▼** next song. In the list you can also browse your own **Songbook**; **Back to the current song** takes you back.

### Leaving

Press back (or choose **Leave the campfire** at the bottom of the list) and confirm with **Leave**. When the leader ends the campfire, the bar turns orange: tap it to leave. The leader's lyrics are then wiped from your phone; the list of songs sung stays as a memory.

## 8. Memories

**Memories** on the home screen keeps the list of songs sung after each campfire, as leader and as singer. **Share the list** shares it; **Delete** and **Delete all** remove them. Your libraries stay.

## 9. Settings

- **Language**: **Automatic** follows the phone's language; or choose **Nederlands** or **English**. A phone set to a language the app does not have shows English. On Android 13 and later this can also be set in the phone's settings, under the app.
- **Name for the leader**: optional; just your first name or a nickname. Only the leader can read it; without a name you are a singer with a number.
- **Campfire statistics** (for leaders, off by default): for each campfire, without names, the phone keeps which songs were sent, how many singers there were and how well Bluetooth reached them. The screen says exactly what is and is not kept. Nothing is sent; it leaves your phone only when you tap **Share** yourself.
- **Remove all data**: libraries, setlists, memories and settings are removed from the phone; the app starts again as on the first day.

## 10. About and privacy

**About** shows the app's version, the installed libraries, the **Signature** (the fingerprint of the genuine app), a link to the source code and this manual.

Your data in short: the app sends something only during a campfire, over Bluetooth to phones nearby: the campfire and the lyrics. Only the leader can read a name. Nothing goes to the app's makers: no account, no advertising, no tracking. The app uses the internet only to download a library or setlist you choose. The full explanation is in the app under **What happens to my data?** and in the [privacy policy](https://pe1mew.github.io/Zangbundel/privacy/).

## 11. When something goes wrong

| What you see | What you can do |
|---|---|
| No campfires in the list | Is Bluetooth on and allowed? Move closer to the leader. Or scan the leader's QR code. |
| *Stand closer to the leader.* | The QR code is fine, but the campfire has not been heard yet. Walk to the leader and tap **Search again**. |
| *This is not the campfire of the QR code* | Another campfire pretends to be the one in the QR code. The app does not join it. Go to start and tell the leader. |
| *The leader did not accept the code* | Ask for the join code again, or scan the QR code. |
| *A campfire is already running* | You cannot start next to another campfire: sing along there, or move out of range. |
| *Load songs first* | A leader needs a library; see [Adding songs](#3-adding-songs). |
| Singers lose the connection | Switch battery saver off on the leader's phone and keep the phone in the middle of the group. |
| *This library needs a newer version of the app* | Update the app from Google Play. |

Questions or bugs? Report them on [GitHub](https://github.com/pe1mew/Zangbundel/issues).
