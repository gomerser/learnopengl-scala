name := "LearnOpenGL"
version := "0.1"
scalaVersion := "3.3.6"

// ✅ LWJGL version
val lwjglVersion = "3.3.3"

// ✅ OS classifier for macOS (Intel or Apple Silicon)
val osClassifier = System.getProperty("os.arch") match {
  case "aarch64" => "natives-macos-arm64" // Apple Silicon (M1/M2/M3)
  case _         => "natives-macos"       // Intel Mac
}

// ✅ LWJGL modules you need
libraryDependencies ++= Seq(
  "org.lwjgl" % "lwjgl" % lwjglVersion,
  "org.lwjgl" % "lwjgl-opengl" % lwjglVersion,
  "org.lwjgl" % "lwjgl-glfw" % lwjglVersion,
  "org.lwjgl" % "lwjgl-stb" % lwjglVersion,
  "org.lwjgl" % "lwjgl" % lwjglVersion classifier osClassifier,
  "org.lwjgl" % "lwjgl-opengl" % lwjglVersion classifier osClassifier,
  "org.lwjgl" % "lwjgl-glfw" % lwjglVersion classifier osClassifier,
  "org.lwjgl" % "lwjgl-stb" % lwjglVersion classifier osClassifier,
  // ✅ Add JOML for math types
  "org.joml" % "joml" % "1.10.5",
  // ✅ Add MUnit for testing
  "org.scalameta" %% "munit" % "1.0.0-M11" % Test,
  "org.typelevel" %% "cats-effect" % "3.6.3",
  "co.fs2" %% "fs2-core" % "3.12.0"
)

// ✅ Enable more helpful compiler options
scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-feature",
  "-encoding", "utf8",
  "-Wnonunit-statement"
)

javaOptions ++= Seq(
  "-XstartOnFirstThread"
)