# Razor Yak

_Work in progress, spurios failures and errors expected_

A tool for yak shaving - create a todo list of upstream dependencies
you need to upgrade before you can enjoy the glory of new Scala versions.

![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/com.indoorvivants/razoryak_2.13?server=https%3A%2F%2Foss.sonatype.org%2F)

You can launch it directly from sonatype (copy VERSION from above) using coursier:

```bash
$ cs launch -r sonatype:snapshots com.indoorvivants::razoryak:$VERSION -- \
  com.disneystreaming weaver-cats --scala 3.0.0-RC1

# ❌ Here's a list of actions you need to do (in this order)
# * [ ] you'll need to publish simulacrum-scalafix-annotations for 3.0.0-RC1
# * [ ] you'll need to publish cats-kernel for 3.0.0-RC1
# * [ ] you'll need to publish cats-core for 3.0.0-RC1
# * [ ] you'll need to publish cats-effect for 3.0.0-RC1
# * [ ] you'll need to publish scodec-bits for 3.0.0-RC1
# * [ ] you'll need to publish weaver-core for 3.0.0-RC1
# * [ ] you'll need to publish weaver-cats for 3.0.0-RC1
```
