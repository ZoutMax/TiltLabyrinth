import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

android {
  signingConfigs {
    create("release") {
      if (keystoreProps.isNotEmpty()) {
        storeFile = file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }
  namespace = "com.megapro.labyrinth"
  compileSdk {
    version = release(37) { minorApiLevel = 0 }
  }
  defaultConfig {
    applicationId = "com.megapro.labyrinth"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  // On-device tests run against the release build, which is what the devices carry (same signing key)
  testBuildType = "release"

  buildTypes {
    release {
      isMinifyEnabled = false
      // Signed with the owner's key when keystore.properties is present (it never leaves this machine);
      // elsewhere, as on GitHub's CI, with the debug key, so the release build can still be built and
      // tested there. A debug-signed build is never published.
      signingConfig = if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
}

// For the dependency audit (CI): `./gradlew :app:dependencies --write-locks -PlockDeps` writes every
// library version the app is built from to app/gradle.lockfile, which the vulnerability scanner reads.
if (providers.gradleProperty("lockDeps").isPresent) dependencyLocking { lockAllConfigurations() }

// The lint tool runs on the build machine, never in the app. Its own libraries are raised past
// their published security advisories (Guava, Commons Lang, HttpClient, Bouncy Castle), so the
// full dependency audit is clean too.
configurations.matching { it.name == "androidLintTool" }.configureEach {
  resolutionStrategy.eachDependency {
    when {
      requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> useVersion("1.86")
      requested.group == "com.google.guava" && requested.name == "guava" -> useVersion("33.7.2-jre")
      requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> useVersion("3.21.0")
      requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" -> useVersion("4.5.14")
    }
  }
}

// tools/render_written.sh passes -PrenderOut=<dir> to have the two written pieces rendered to WAV
tasks.withType<Test>().configureEach {
  systemProperty("render.out", providers.gradleProperty("renderOut").getOrElse(""))
}
