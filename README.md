# Code-Word

A code-breaking game for Android  
by Jake Rosin  
(c) 2023 Peace Ray LLC  

https://www.peaceray.com/codeword

## License

This project is licensed under GNU Public License 3.0. All original media
assets (art, music, sound, documentation, etc.) are licensed under
a Creative Commons Attribution ShareAlike 4.0 International License. Various
derivative works are included under their respective licenses. See NOTICE.txt
for details.

Code Word, Peace Ray, and the tile logo are trademarks of Peace Ray LLC.

## Installation

Code Word may be built from source (this project uses Android Studio) or installed
directly as an APK (see [Code Word Releases](https://github.com/Peace-Ray/Code-Word/releases)).

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.peaceray.codeword/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.peaceray.codeword)

## Description

Code Word is a code-breaking game for Android, implemented in Kotlin. Given a code length and a vocabulary, the player refines a series of guesses based on feedback to arrive at the correct code or exhaust their chances. "Codes" may be words of a particular length with feedback marking each correct letter, arbitrary letter sequences with feedback showing only the number of correct and included characters, etc. Code Word is implemented to support variable game rules and future extensions in code types and feedback details. At launch, the two main code types are English Words with per-letter feedback (exact, included-somewhere-else, not-present) and Code Sequences with aggregated feedback (number of exact and included characters).

Code Word is free and open-source.

### Permissions

* Internet: to ensure the Daily puzzle remains consistent for all users across app updates.

### Credits

Game design, art, and code by Jake Rosin.

Code Word draws from a long history of code-breaking games and related work.

* [Wordle™](https://www.nytimes.com/games/wordle/index.html): the viral hit by Josh Wardle. Wordle's daily puzzle is a 5-letter English word allowing six guesses. Trademark of The New York Times Corporation.
* [Jotto™](http://jotto.augiehill.com/): five-letter English words like Wordle, but with only the number of included letters (not their positions) provided as feedback. Trademark of Michael Rosenfeld.
* [Mastermind®](https://webgamesonline.com/mastermind/): a two-player board game using colored pegs. Trademark of the Pressman Toy Corporation.
* [Bulls and Cows](https://www.mathsisfun.com/games/bulls-and-cows.html): a guess-the-number game with multiple software implementations.
* [Absurdle](https://qntm.org/files/absurdle/absurdle.html): this adversarial version of Wordle by qntm features a cheating opponent who changes the secret in response to your guesses.

## Author Note

Code Word began as a hobby project to overengineer a Wordle solver. That project still exists as the core of the game's logic, available in a CLI by running ConsoleGame.kt.

Code Word is implemented in Kotlin, using layered Model-View-Presenter application architecture. Hilt is used for dependency injection, and Kotlin coroutines for asynchronous computation and IO operations. Originally RxJava was used for the latter purpose, but the use case did not justify the complexity of the tool.

Thank you to Josh Wardle for creating Wordle and inspiring this project (and many others)!

### Donations

Updates to Code Word may be infrequent or sporadic, and I don't expect to make
money from the project. If you want to send a donation anyway, I'm on PayPal at
[paypal.me/jakemrosin](https://paypal.me/jakemrosin).
