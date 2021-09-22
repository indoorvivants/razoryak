# Razor Yak

_Work in progress, spurios failures and errors expected_

A tool for yak shaving - create a todo list of upstream dependencies
you need to upgrade before you can enjoy the glory of new Scala versions.

![Maven Central](https://img.shields.io/maven-central/v/com.indoorvivants/razoryak_2.13)

## Installation

You can launch it directly using [Coursier](http://get-coursier.io/)

Bootstrap it:

```
$ cs bootstrap com.indoorvivants::razoryak:latest.release -o razoryak
```

## Usage

```
$ ./razoryak io.get-coursier coursier --scala 3

[ ] Upgrade to org.scala-lang.modules:scala-collection-compat:2.5.0 from 2.2.0
[ ] Publish io.get-coursier:coursier-util for Axis(Scala3,JVM)
[ ] Upgrade to org.scala-lang.modules:scala-xml:2.0.1 from 2.0.0
[ ] Upgrade to com.lihaoyi:utest:0.7.10 from 0.7.5
[ ] Upgrade to io.argonaut:argonaut:6.3.7 from 6.2.5
[ ] Publish io.get-coursier:coursier-cache for Axis(Scala3,JVM)
[ ] Publish org.portable-scala:portable-scala-reflect for Axis(Scala3,JVM)
[ ] Publish com.chuusai:shapeless for Axis(Scala3,JVM)
[ ] Publish com.github.alexarchambault:argonaut-shapeless_6.2 for Axis(Scala3,JVM)
[ ] Publish io.get-coursier:coursier-core for Axis(Scala3,JVM)
[ ] Publish io.get-coursier:courser for Axis(Scala3,JVM)i
```

Seeing what's needed to publish for Scala 3 and Scala.js:

```
$ ./razoryak com.lihaoyi scalatags --scala 3 --js

[ ] Upgrade to com.lihaoyi:geny:0.6.10 from 0.6.7
[ ] Upgrade to com.lihaoyi:sourcecode:0.2.7 from 0.2.3
[ ] Publish com.lihaoyi:scalatags for Axis(Scala3,JS)
```

And when artifact already exists:

```
$ ./razoryak com.lihaoyi upickle --scala 3.0.0 --js

[x] Use com.lihaoyi:upickle:1.4.1
```


## Options

```
Usage: razoryak [--tests] [--verbose] --scala <string> [--js | --native | --jvm] [--track-coursier <string> | --replay-coursier <string>] [[--allow-major] [--allow-minor] [--allow-patch] [--allow-snapshots]] [--no-default] [--resolver <string>]... [--mode <string>] <org> <name>

Welcome to razoryak

To get a test of what this tool does, ask it to upgrade itself to Scala 3:

cs launch com.indoorvivants::razoryak:0.0.4 -- com.indoorvivants razoryak --scala 3

Options and flags:
    --help
        Display this help text.
    --tests
        t
    --verbose, -v
        log output
    --scala <string>
        Scala version you wish to use
        Examples: 2.13, 2.12, 3, 3.0.0-RC3
    --js
        search for Scala.js artifacts
    --native
        search for Scala Native artifacts
    --jvm
        search for JVM artifacts
    --track-coursier <string>
        Path to a file where to dump traces of coursier resolution
    --replay-coursier <string>
        Path to a file with coursier trace to reproduce
    --allow-major
        When looking for resolution, consider major upgrades of dependencies (default: false)
    --allow-minor
        When looking for resolution, consider minor upgrades of dependencies (default: true)
    --allow-patch
        When looking for resoltion, consider patch upgrades of dependencies (default: true)
    --allow-snapshots
        When looking for resoltion, consider snapshot versions of dependencies (default: false)
    --no-default
        Don't add default resolvers (i.e. maven central and ivy2local)
    --resolver <string>, -r <string>
        Resolvers to use, in coursier format
    --mode <string>
        Download mode, passed directly to coursier
        offline|update-changing|update|missing|force
        default is 'missing'
```
