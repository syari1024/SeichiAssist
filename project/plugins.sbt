// プラグインJarを(依存関係にあるJarをすべて同梱して)出力するため
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")

// Lintを掛けるため
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.11.0")

// コードフォーマットするため
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
