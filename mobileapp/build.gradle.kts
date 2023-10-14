plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.kotlinMultiplatform).apply(false)
//    kotlin("multiplatform").apply(false)
    alias(libs.plugins.androidApp).apply(false)
    alias(libs.plugins.androidLib).apply(false)
    alias(libs.plugins.jetbrainsCompose).apply(false)
}
