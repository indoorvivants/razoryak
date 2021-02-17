# Razor Yak

_Work in progress, spurios failures and errors expected_

A tool for yak shaving - create a todo list of upstream dependencies
you need to upgrade before you can enjoy the glory of new Scala versions.

![Maven Central](https://img.shields.io/maven-central/v/com.indoorvivants/razoryak_2.13)

## Installation

You can launch it directly [Coursier](http://get-coursier.io/)

```bash
$ cs launch com.indoorvivants::razoryak:latest.release -- \
   com.disneystreaming weaver-cats --scala 3.0.0-RC1

# ❌ Here's a list of actions you need to do (in this order)
# * [ ] you'll need to publish simulacrum-scalafix-annotations for 3.0.0-RC1
# * [ ] you'll need to publish cats-kernel for 3.0.0-RC1
# * [ ] you'll need to publish cats-core for 3.0.0-RC1
# * [ ] you'll need to publish cats-effect for 3.0.0-RC1
# * [ ] you'll need to publish scodec-bits for 3.0.0-RC1
# * [ ] you'll need to publish weaver-core for 3.0.0-RC1
# * [ ] you'll need to publish weaver-cats for 3.0.0-RC1

$ cs launch com.indoorvivants::razoryak:latest.release -- \
   com.lihaoyi upickle --scala 3.0.0-M3 --js

❌ Here's a list of actions you need to do (in this order)
* [ ] you'll need to publish upickle-core for 3.0.0-M3
* [ ] you'll need to publish geny for 3.0.0-M3
* [ ] you'll need to publish upickle-implicits for 3.0.0-M3
* [ ] you'll need to publish upickle for 3.0.0-M3
```

You can also bootstrap it locally with coursier:

```
❯ cs bootstrap -f -o razoryak com.indoorvivants::razoryak:latest.release
Wrote /home/user/projects/razoryak/razoryak

❯ ./razoryak org.tpolecat skunk-core --scala 3.0.0-RC1

❌ Here's a list of actions you need to do (in this order)
* [ ] you'll need to publish fs2-core for 3.0.0-RC1
* [ ] you'll need to publish fs2-io for 3.0.0-RC1
* [ ] you'll need to publish sourcepos for 3.0.0-RC1
* [ ] you'll need to publish scala-collection-compat for 3.0.0-RC1
* [ ] you'll need to publish skunk-core for 3.0.0-RC1
```
