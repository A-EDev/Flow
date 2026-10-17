# Flow v3.0.0

**Release date:** 2026-10-18
**Status:** Stable

Enjoying Flow? Consider supporting its development on [Open Collective](https://opencollective.com/flow-tube) or [Patreon](https://patreon.com/A_EDev) to help keep the app free, open source, ad-free, and improving!

## Highlights

- The whole app is redesigned on Material 3 Expressive, with real tablet and foldable layouts
- Flow now runs on its own native InnerTube client everywhere, NewPipe's extractor is gone
- New on-device music taste engine: Quick Picks, Daily Mixes, endless radio and new shelves
- Home recommendations rebuilt to learn from what you actually watch (#907)
- Flow Recap: private monthly and yearly stats, plus a Recap story you can share
- New Equalizer page with AutoEQ and EqualizerAPO presets
- Word by word lyrics, a rebuilt music player and a new queue
- Big upgrades to Downloads, Local media and subtitles
- In-app updates, private notes, and themes shared with Flow Desktop

## Behavior changes

- Background Play is now on by default, you can turn it off in Playback settings (#1298)
- Backups now carry your settings and library only. The recommendation engine and sync identity stay on the device, so a reinstall starts the engine clean (#907)
- The Flow Control Center is now called Your taste
- Explore's Movies tab is gone because YouTube stopped serving its chart (#1254)

## Native innertube client

- Search, channels, Explore, Shorts, comments, descriptions and the player all run on Flow's own InnerTube client
- Video search makes one request per page instead of thirteen, with filters and sorting done by the server
- The player resolves videos through one client instead of racing two, and fetches the watch page once per video
- Channel pages handle scheduled streams and premieres correctly
- Explore uses YouTube's own destinations instead of the trending endpoints YouTube retired

## Search, explore and channels

- New Material 3 search bar, filters in a sheet and a grid/list toggle
- Creator cards are tinted by the channel avatar and show their latest uploads
- Blocked channels are hidden from search results too, not just the feed
- Search history keeps each search's filters and applies them again on tap (#1174)
- Recently uploaded, Unwatched and Watched chips in the search filter row. Watched and Unwatched use your watch history (#940)
- Explore is rebuilt around Live, Gaming, Music, News, Sports, Learning and Fashion, with the region one tap away in the top bar
- Explore's Music tab shows trending music videos from YouTube Music's charts for your region (#1254)
- Explore tells you when to try again instead of showing raw errors
- YouTube Courses show as courses and open as a playlist (#1299)
- Text in channel posts and the About tab can be selected and copied, and a long press copies a channel's name (#951)

## Home and recommendations

- Home covers all your interests: the biggest ones always show, smaller ones rotate in, and each gets space based on how much you watch it
- Each load goes deeper into your interests, and the feed never dead-ends, it digs new related lanes from what's on screen, your history, likes and playlists
- Loading more adds several videos at a time, and Home says when it has run out of videos (#1317)
- Videos you've already seen are filtered out of the next refreshes and come back slowly later, and pull to refresh gives you a fresh page
- Home shows your subscriptions right away and adds new uploads below what you're looking at
- Chips above the feed: top interests, New to you, Recently uploaded, Mixes, Live and Watched. Similar interests are grouped into themes like "Gaming"
- Cards are mixed: two from related videos, searches or channels you watch, then one or two from channels you follow. A channel never shows twice within 20 cards
- Home remembers channels you keep watching without subscribing and brings in their uploads, marked "From a channel you watch" (#907)
- Related videos rotate their seeds, spread across your interests, and say which video they came from in Details (#907)
- New subscription uploads come from your whole subscription feed, and only a couple pin to the top, one per channel
- Subscriptions show collaborations a channel you follow took part in, with every collaborator on the card. Turn it off in Content settings (#840)
- Subscriptions save new uploads as soon as the quick check ends, and the slower Shorts and live check has its own counter (#1042)
- With a subscription group open, refresh fetches only that group, small groups read further back, and older uploads stay between refreshes (#1099)
- Channels you interact with are no longer ranked below ones you've never seen
- The engine learns from watch time, early skips, downloads, Watch Later, playlist saves, live subscribes and your searches
- It learns phrases instead of single words: creator tags, hashtags and word pairs you keep watching together. lo-fi and lofi count as one (#907)
- It learns what kind of videos you watch (reviews, tutorials, playalongs) apart from what they're about (#907)
- It learns each channel's topics, its YouTube category, and which channels go together (#907)
- Long term interests are no longer wiped by a Shorts binge or a couple of new videos, while recent watching still gets a quarter of the say (#907)
- Thumbs down and Not interested now stick: rejected topics are remembered for months and recognised under new titles (#907)
- Blocking a topic is precise now, blocking "art" no longer hides "startup"
- Small words in your language are skipped, forms like "guitar" and "guitars" count as one, and Chinese, Japanese and Thai titles are split properly (#907)
- Styled titles like 𝙋𝙃𝙊𝙉𝙆 read as the plain word, short topics like AI, F1 and 3D count, and interests show as proper titles like "MMA" (#907)
- Profiles damaged by the old learning are rebuilt from your channels on update, with blocked topics cleared out (#907)
- Importing subscriptions or a long history no longer locks in old interests (#1030)

## Your taste

- The Flow Control Center is now Your taste: video and music taste, engine details, and a Hidden from you page
- Your picks are listed apart from what was learned, each with how much you watch it (#907)
- Discover and subscribe to channels here, next to channels you watch without subscribing (#804)
- Remove a remembered channel or clear channel memory (#907)
- Hidden from you shows every blocked channel's name instead of its ID (#1325)
- Profiles sync between devices and export to a file

## Music

- A local taste engine ranks every music shelf and builds Quick Picks from how much of each song you actually play, all on your device
- New shelves with no network calls: On Repeat, Rediscover, a time of day rotation, and From artists you love
- Daily Mixes group artists you listen to together and open as full playlists you can play, shuffle and save
- Endless radio adds similar songs as the queue runs low, keeps Up next stable, and won't play the same artist twice in a row
- "Not interested" softens an artist for two weeks, "Don't recommend" blocks them everywhere, both can be undone
- Hiding an artist also hides their albums and collaborations
- Artist pages show your play count, liked status, most played tracks, and which similar artists you already listen to
- Artist pages play, shuffle or start the artist's radio in one tap, show subscriber and audience figures, and the Videos shelf plays all of them
- The artist photo runs up under the status bar, which turns solid once the photo scrolls away
- The engine learns genres and moods, and keeps a private monthly listening log on your device
- New On Repeat home screen widget
- Music follows your chosen region instead of the device locale and refreshes when you change it
- While the engine is still new, the music page leads with charts and moods
- A song played from search plays on its own and endless radio fills Up next, like YouTube Music
- The whole music section moved to Material 3 Expressive: wide carousels, grouped Quick Picks, cookie shaped artists, mood tiles and new recognition screens
- Android Auto: For you, Library, Playlists and Queue tabs, search, voice play and artwork, and music started in the car joins your queue (#1221)

## Music player

- Full redesign with a pill mini player and a progress ring
- Album colours flow through the whole player and stay readable on any cover
- Six seek bar styles and an Immersive Artwork background that follows your swipes
- Controls can use black and white or your theme instead of artwork colours
- Rebuilt queue with drag to reorder, swipe actions and an endless radio toggle
- Tablets and foldables show Up next and Lyrics in a side pane, upright tablets get a centred layout, landscape phones show the cover beside the controls
- The mini player sits on its own at the bottom, moves with the navigation bar and closes with a swipe down
- On tablets the mini player is a centred card that doesn't cover the navigation rail
- New songs start in the mini player unless you turn on the full player in Playback settings
- Music comes back after Flow was closed: the system media controls and a headset's Play button resume your queue where it stopped, mid song (#1280)
- Optional sleep timer button next to the queue button, turned on in Player Appearance
- Palette Gradient, Blur and Blur Gradient can turn light with a light app theme, mini player included, with Light in light theme in Player Appearance

## Lyrics

- Lyrics sheet with source browsing, editing, alignment and sync offset
- Word by word lyrics even when the source only times each line
- Duets split across both sides, instrumental breaks show three dots, and word timed sources are preferred
- Lyrics in other scripts can show a romanized line
- The sync offset now moves highlighting, scrolling and word fills live

## Music albums and playlists

- Redesigned pages with a side pane on tablets, search, and sorting by order, title, artist, album, length or date added
- Select songs to play next, queue, add to a playlist, download or remove with Undo
- Your playlists can be edited, deleted and filled from a new Add songs sheet
- Save a Daily Mix as your own playlist
- Download all covers the whole album or playlist and keeps going after you leave the page
- Share links work, and Save as file works for your playlists and Liked music

## Equalizer

- New Equalizer page: drag bands on the curve, type exact values, or switch to 10 band graphic mode (#810)
- Hold Compare to hear the difference
- Import and export AutoEQ and EqualizerAPO presets, kept in backup and sync
- No more clipping on boosts (auto preamp is on by default) and no clicks while dragging
- Now applies to Shorts, surround audio and audio offload
- Low pass, high pass and shelf Q work as labelled

## Video player

- Adaptive player: a side pane and navigation rail on wide screens, with description, chapters, comments and settings in the pane or a fullscreen drawer
- Every player surface moved to Material 3 Expressive
- Tapping a video opens the player out of the card, keeping the thumbnail until it starts (#1091)
- Wide videos keep a 16:9 player with black bars, taller ones grow it smoothly (#1091)
- Scrubbing previews the frame under your thumb, as a filmstrip or single frame, with the time, chapter and SponsorBlock segment
- The seek bar shows the most replayed curve while scrubbing and marks its peak
- Optional frame by frame stepping while paused
- Rebuilt portrait fullscreen with the title and channel above the video
- Volume and brightness overlays can be circular, vertical, a bar or just the number, picked from a preview sheet
- Chapters are a segmented list with the current one tinted and its progress shown
- Double tap seek can be turned off, and its side zones made narrower (#1178)
- A new Edge to edge seek zone lets double taps reach the screen edges (#1314)
- Swipe up or down on one side of the fullscreen player to change speed in steps of 0.05x, 0.1x or 0.25x, with a read-out while you swipe (#1159)
- Swipe sideways on the video to seek outside fullscreen too (#1312)
- The controls step aside while you drag to seek, so the time read-out stays visible
- Previous and Next buttons can be hidden (#1308)
- New setting to open videos paused, so you can read the description and comments first (#1024)
- A SponsorBlock button turns it off for the current video only, also in the settings sheet and TV player (#930)
- Auto quality now switches mid playback without stopping, based on your real connection speed. Before it started at 480p and never went past 720p
- The transcript stays where you leave it, with a Sync to video button, chapter headings, any caption language and copyable lines
- Live chat on tablets opens on tap, and can be turned off completely in Playback settings (#1042)
- A video linked from a playlist plays with that playlist as its queue (#802)

## Video queue and mini player

- Redesigned queue: swipe away with Undo, long press menu, shuffle and repeat toggles, and time left
- Sharing a video to Flow while something plays lets you choose Play now, Play next, Add to queue, Save or Download instead of losing your queue. Content, Links, Shared links sets when Flow asks (#1116)
- On tablets the queue opens in the side pane, and in fullscreen from its own button
- A video playing in the background keeps a mini bar with play, pause and its queue (#980)
- Swipe the mini bar up to grow it into the player under your finger (#980)
- Choose what swiping the mini bar left or right does: close, next, previous, Watch later, like, download and more (#980)
- The video mini player closes with a swipe down (#966), tucks into the side when thrown past it, and stays above the keyboard (#1241)

## Background play

- Videos keep playing as audio when you leave the app or turn off the screen (#1298)
- With Background Play off, Play in the notification resumes audio only (#1298)
- Coming back to the video no longer forces a rebuffer, and the decoder is reused instead of rebuilt (#1298)

## Shorts

- New reel page and settings sheet
- New feed that mixes reels like ones you liked, saved or watched with discovery and subscriptions, endless and one reel per channel at a time
- Shorts respect blocked channels and topics, add "Don't show this channel", and drop YouTube's ad reels
- A Short that links a full video shows it, one tap to open (#1031)
- Picture in picture has previous and next buttons (#959)
- A channel's Shorts tab can stay visible while Shorts are hidden everywhere else (#1106)
- Default quality can go up to 1440p or 2160p (#1125)
- Remove from Saved Shorts in the card menu, with Undo (#1024)

## Library, playlists and notes

- Likes are now two playlists, Liked videos and Liked music, with search, sort, select, Play all, Shuffle, Download all and Save as file
- Rebuilt playlist page with a big artwork header, side by side on tablets like Flow Desktop
- YouTube playlists open on the first page right away, remember their sort order, and removals can be undone
- Search inside any playlist
- Save a playlist as a file, share it, and import it from the Playlists screen or by opening the file
- Playlist rows give titles more room and only show the added date when sorted by date added (#1052)
- Optional: remove videos from Watch later once watched, with Undo (#1060)
- Private notes on any channel or video, kept in backup and device sync
- Notes can hold timestamps that play from that moment, and a Notes page in Library lists them all with search and filters (#843)

## Downloads

- Downloads are M4A or MP4 with readable names, cover art, details and lyrics inside the file, so other players can use them and Flow restores them after a reinstall. Songs get their own quality setting (#866, #894, #764)
- Download a whole album or playlist as one item in its own folder, playable offline (#913)
- Downloads keep running after Flow is closed and resume where they stopped
- Downloads shows used and free space, has search, sort and select, and lists quality, size and date for each item
- Optional: download videos you open, on Wi-Fi or any network (#771)
- Save a subtitle file beside downloaded videos (#1182)
- A downloaded song plays from its file wherever it's queued

## Local media

- Now a full offline library: search, sort, filters, folders, grid view, Continue watching, New marks, Play all and Shuffle
- File menu to queue, share, inspect or delete, and deleting moves files to the system trash with Undo
- Works with Android 14's "Select photos and videos" and updates when files change
- Choose what Local media hides in Settings
- Search matches file names and ignores accents (#1177)
- Videos show the embedded artist as the channel and the description under the video (#1168)
- Songs show lyrics from a .lrc file or from the file itself, offline (#1169)
- Covers or a video frame show in lists and in the notification

## Subtitles

- Local videos find their subtitle files on their own, including episode matching and Subs folders, and list subtitles inside MKV and MP4 (#1028)
- Add any subtitle file from the subtitle menu, older text encodings included (#1028)
- Move captions earlier or later, local videos and downloads remember the timing
- Subtitle Style adds custom colours, caption window colour and opacity, and outline or drop shadow (#1182)
- Separate subtitle positions for landscape and vertical fullscreen, sitting on the picture (#1098)

## Settings and appearance

- Settings rebuilt with grouped rows, search for any option, and two panes on tablets
- Themes match Flow Desktop, Monochrome has a true black AMOLED style, and named custom themes can move between both apps
- Custom colours can be translucent again with an Opacity slider
- Choose the app font: System, Condensed, Serif or your own .ttf or .otf (#1222)
- Thumbnail quality can be set for Wi-Fi and mobile data separately, or thumbnails turned off (#886, #849)
- Cache settings show space per type with separate limits and Clear buttons (#1229)
- New switches: Subscriptions on Home, watch progress on thumbnails, Pause watch history (#1086)
- A Content setting shows if YouTube links open in Flow and takes you to the system screen to turn it on (#802)
- The proxy can step aside while a VPN is on (#677)
- The app language list shows every translated language, Traditional Chinese (Taiwan) included, and matches Android's App languages setting (#1300)
- Original titles, in Language and region, keeps titles and descriptions in the video's own language instead of YouTube's automatic translation, on cards, Shorts and the watch page (#1230)

## App-wide design

- Video cards rebuilt: one card family everywhere, with I want more like this, Not interested and Mark as watched grouped together
- Quick action menus for videos, songs, albums and playlists rebuilt, they open halfway and expand as you drag
- Snackbars with Undo for Watch later, subscriptions and hidden channels
- Downloading from a card opens the same quality picker as the player
- Album and playlist menus can play, shuffle or start a radio, and History, Liked, Continue watching and Downloads open the menu on long press
- One grid everywhere: up to three columns on tablets, same margins and gaps, and rows the grid can't fill turn into full width rows
- Your cards per row setting now applies to Home, Channels, Search, Subscriptions and related videos on the watch page (#1281)
- List mode on wide screens is one column with large thumbnails
- Pages scroll under the navigation bar and mini player instead of stopping above a blank space
- Loading placeholders match the shape of the cards they stand in for
- Onboarding rebuilt with a morphing shape, notifications asked in context, imports grouped by app, and two panes on tablets
- Device sync, dialogs, About and every bottom sheet redesigned to match
- Notifications have grouped rows, swipe to remove with Undo, and Clear all asks first
- The crash page shows a plain summary, and Report fills the whole GitHub bug form (#1189)
- The donation prompt has Don't show again and never shows on the same launch as an update
- Flow can now be supported on Open Collective, alongside Patreon and crypto

## Recap and privacy

- Flow Recap: a private record of your watching and listening, with charts for any month, year or all time
- A monthly and yearly Recap story, shareable as a poster
- Digital wellbeing reads watch time from Recap once it covers a full week
- Deep Flow keeps a session private: nothing goes to Recap, feed memory or scrobbling. Long press the FLOW wordmark or logo to switch it (#1228)
- An optional Deep Flow switch above recent searches, turned on in Settings (#706)

## Imports, sync and updates

- Updates download, verify and install inside Flow, with release notes, size and Skip this version
- Google Takeout imports a whole split export at once: subscriptions, playlists, likes, searches, watch history and your YouTube Music library (#858, #1027)
- Takeout works in any account language (#1128)
- Importing the same export twice no longer duplicates playlists, and they keep YouTube's creation date (#858)
- Device pairing can be done by typing connection details when a QR scan isn't practical

## Performance

- One shared music engine serves every music page. Daily Mixes open in about a second instead of up to 30, with much less battery drain and heat
- Animations pause when their screen isn't visible, ending a constant drain while listening
- Music shelves only update while the Music screen is open, and similar songs are fetched once per track
- Flow opens as soon as the first screen is ready instead of waiting on a two second splash, and on Android 13+ the splash matches your theme and icon
- Playlists are fetched once instead of twice, Local media doesn't rescan on every visit, and download progress updates once a second
- The expanded music player isn't rebuilt on every song, and lyrics stop updating while paused
- Feed ranking is lighter, and less is written to disk while scrolling
- The bot check runs once per video instead of three times
- Opening and collapsing the player is smoother, with no jumps after slow frames and no bounce
- Live streams use far less data, about 20 KB/s instead of 116 KB/s at 144p in testing, and live chat pauses in the background (#1042)
- Flow no longer warms up playback when it only wakes in the background for a check, a widget or the media controls (#1272)
- Home, Subscriptions, Library and the player share reusable parts, and the feed pipeline was rebuilt to fix stalls

## Fixes: playback

- Fix videos stopping or buffering forever after about a minute on any network or VPN. Refused streams now switch to another source without stopping (#921, #1251)
- Fix playback never recovering once YouTube stopped trusting the app. The identity is replaced automatically, and Diagnostics has a Reset YouTube session button
- Fix Reset YouTube session being undone by the next restart, and keep the YouTube identity out of Android backups
- Fix day long outages when YouTube changes its player script
- Fix label and kids videos (Disney, VEVO, CoComelon) failing to load since YouTube's 6 October update (#1291, #1292, analysis by @Chiehx0220)
- Fix videos only the web player serves being treated as removed and skipped
- Fix playlists and background queues stopping at a removed or refused video, and the queue being lost when the app closed (#1008)
- Fix songs buffering 30 seconds in, and removed songs spinning forever. They're now skipped with a message (#1180)
- Fix videos playing in a French dub (or another) when a specific audio language is set (#1223)
- Fix automatic captions not turning on for the next video, and the TV Subtitles switch doing nothing (#1182)
- Fix the subtitle position being ignored on downloads (#1182)
- Fix a video without chapters showing the previous video's chapters and seek bar data (#1111)
- Fix videos behind a content warning having no suggestions or autoplay (#1244)
- Fix SponsorBlock Mute categories playing at full volume, notify only categories showing nothing, and downloads skipping with SponsorBlock off
- Fix videos from a playlist, Watch Later or Downloads always starting from the beginning
- Fix a finished video's progress bar disappearing on autoplay, and watched videos losing their length
- Fix Shorts audio playing after picture in picture is closed (#1152)
- Fix a memory leak when reopening the video player (#1148)
- Fix a playback statistics crash on some devices
- Fix the ambient glow, and a crash on some ROMs from oversized artwork
- Fix crashes from a cancelled back swipe (#1307) and from media buttons with nothing to play (#1309)
- Fix the next video in a queue staying hidden behind its thumbnail while it plays (#1313)
- Fix a paused video staying in the notification after clearing Flow from recents, and the sleep timer's Close app leaving the players running (#1272)

## Fixes: player and gestures

- Fix a back swipe seeking 10 seconds, double taps now need both taps to lift (#1068)
- Fix a double tap landing on Previous or Next instead of seeking (#1308)
- Fix fullscreen transitions jumping, and short swipes collapsing the player or entering fullscreen by accident (#1065)
- Fix the page bouncing when a drag to portrait fullscreen is let go early
- Fix the title and page jumping right after the player opens (#1091)
- Fix upright tablets splitting the player into two narrow columns
- Fix the volume gesture starting from an old level or dropping a boost above 100%
- Fix vertical video shades staying after the controls hide, and landscape tablets getting the upright layout (#1179)
- Fix pinching the mini player making it jump corners
- Fix the comment count showing the previous video's count
- Fix the like count sometimes missing (#1282)
- Fix the Subscribe button showing the wrong state after a reload (#1160)
- Fix collaboration videos showing a blank avatar, all collaborators are shown now (#1162)
- Fix the queue always being called Playlist, and the queue dock covering the last related video
- Fix mini bar swipe actions on the wrong side in RTL languages (#980)

## Fixes: feed and recommendations

- Fix watch progress and listened songs never reaching the recommendation engines
- Fix ages like "3 days ago" being read as seconds, which pushed old uploads to the top
- Fix related videos getting the current time as their upload date
- Fix junk words like "right", "build" or "test" becoming interests
- Fix liked videos not teaching the engine when their details failed to load
- Fix mature profiles not picking up new interests, and learned topic pairs being deleted right away
- Fix onboarding picks merging into one interest, so cooking and anime stay separate (#907)
- Fix Home drifting to unrelated videos, favouring weaker ones after a few skips, and swiping Shorts counting as skips (#907)
- Fix Home giving early spots to weak videos just because their channel wasn't shown yet (#907)
- Fix popular videos ranked as clickbait because search results have no like count (#907)
- Fix the feed shrinking after a few refreshes in a row
- Fix device sync resetting parts of the video profile
- Fix related videos treating any video watched past a third as a strong watch
- Fix related music fetching broken by a YouTube change, which flooded Quick Picks with charts
- Fix old Shorts showing up as new uploads, and Shorts staying filed as videos in Subscriptions (#1175)
- Fix Subscriptions failing after a short network drop (#1186)
- Fix subscriptions with a broken avatar link, they're repaired on launch
- Fix onboarding subscribing by handle instead of id, and back leaving setup
- Fix pressing Home repeatedly stacking reloads (#1171)
- Fix Home stopping after a dropped connection: a failed search no longer ends the feed or stays empty for half an hour, and a Retry row shows where loading failed (#1317)
- Fix a failed refresh clearing the feed, it now stays on screen with Retry, and a pull to refresh during a load no longer snaps back (#1317)
- Fix a reset profile coming back, and Boost missing your starting picks (#907)

## Fixes: music

- Fix the music feed crashing after an update, stored tracks are repaired on read with no clean install needed
- Fix song menu entries doing nothing, multi artist songs opening the first artist, and the playlist picker not showing which playlists have the song
- Fix music playlists showing no song length, saving an album wiping song details, and imported playlists being treated as someone else's
- Fix music pages reloading on every visit, blank screens after a failed load, and playlists stopping partway
- Fix likes disappearing when a title had a "|", and unliking needing two taps
- Fix music search showing suggestions when they're turned off
- Fix music started during a background video getting no mini player
- Fix music stopping when Flow was cleared from recents while a song loaded (#1280)
- Fix artist bios stopping at "From Wikipedia (", they show in full with their source and licence links, and song rows on artist pages are visible again
- Fix the sleep timer's Start and Cancel buttons sitting below the fold, the sheet now fits its content

## Fixes: downloads and local media

- Fix downloads freezing at a percentage, they now retry or stop with Retry (#1224)
- Fix paused downloads not resuming after a restart, failed downloads that could only be deleted, and hidden in-progress music downloads
- Fix a queue of downloads streaming instead of playing the files, and refetching SponsorBlock and captions on every play
- Fix downloaded videos missing their title, description, related videos and channel (#1129)
- Fix Downloads missing from Library on a fresh install, it can now find files from an earlier install (#1246)
- Fix in-progress download titles shrinking on narrow phones (#1225)
- Fix local files reaching YouTube: no Like, Subscribe or Download on them, and their history stays local
- Fix missing local files loading forever, and local songs failing after a restart
- Fix finished local files reopening at their last second
- Fix accented letters and emoji showing garbled in Local media (#1176)
- Fix missing covers on Opus, Ogg and FLAC, with cover.jpg or folder.jpg used as a fallback (#1250)
- Fix Local media folder search ignoring what you typed, and a refresh spinner that never went away (#1167)
- Fix Local media search flickering while typing (#1220)
- Fix videos removed from a saved YouTube playlist coming back

## Fixes: other

- Fix YouTube playlists stopping at 100 videos (#1299)
- Fix YouTube links in descriptions, comments and posts opening the browser (#1032)
- Fix parts of the app ignoring the proxy or keeping an old one until a restart
- Fix a channel page losing its sort and scroll after PiP or opening a Short (#1024)
- Fix the Shorts quality menu listing the same resolution several times (#1213)
- Fix the Exact and Both date styles showing made up dates for older videos (#1242)
- Fix titles in the wrong language after restoring a backup or syncing, and Home keeping titles cached in the previous language (#1277)
- Fix the watch page briefly showing the original title before the translated one (#1230)
- Fix videos touching each other with two or three cards per row on a phone, narrow cards drop the channel picture so the title fits (#1281)
- Fix the new video check running with New Videos off, its interval now shows Off, and the update check running with Updates notifications off (#1272)
- Fix import and export results never showing on Backup, Your taste and onboarding
- Fix videos watched with history paused still reaching Recap
- Fix custom themes losing their colours in release builds, or their opacity after updating
- Fix Notification settings crashing in German, System theme ignoring dark mode with an app language set, and Material You colours drifting
- Fix a crash on About tab links without https://
- Fix the Unlimited cache stopping at 500 MB, and playback errors clearing cached songs
- Fix snackbar buttons being hard to read in some themes
- Fix Continue watching cards being cut off on narrow phones, and placeholders not matching list mode
- Diagnostics reports now show how recent runs ended and which player script couldn't be read
- Add Open source notices to About

## Build and ci

- Offline recommendation, music and learning benchmarks with regression floors
- Offline brain diagnostics using real exported profiles
- #907 scenario tests for drift, junk interests, recency and resets
- Search, channel and player parsing tested against real API responses
- Limit parallel Kotlin compilations so CI stops running out of memory
- Keep the R8 mapping with every CI build so crashes can be traced
