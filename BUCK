android_resource(
  name = "res",
  res = "src/main/res",
  assets = "src/main/assets",
  package = "id.co.veritrans.sdk",
  deps = ["//deps:appcompat-v7", "//deps:design"],
  visibility = ["PUBLIC"]
)

android_build_config(
  name = "build-config",
  package = "id.co.veritrans.sdk",
  values = ["boolean DEBUG = false"],
)

android_library(
  name = "sdklib",
  srcs = glob(["src/main/java/**/*.java"]),
  deps = [":res", 
          ":build-config",
          "//deps:rxandroid",
          "//deps:rxjava",
          "//deps:appcompat-v7",
          "//deps:retrofit", 
          "//deps:gson",
          "//deps:support-v4",
          "//deps:design",
          "//deps:annotation",
          "//deps:recyclerview-v7",
          "//deps:okhttp"],
  visibility = ["PUBLIC"]
)

android_aar(
  name = "sdk",
  manifest_skeleton = "src/main/AndroidManifest.xml",
  deps = [":sdklib", ":build-config", ":res"],
  visibility = ["PUBLIC"]
)
