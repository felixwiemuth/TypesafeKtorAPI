/*
 * Copyright (C) 2024 Felix Wiemuth
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.moveTo

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.ksp)
    id("module.publication")
}

kotlin {
    targetHierarchy.default()
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.tkaBase)
                implementation(projects.tkaClient)
                implementation(projects.tkaExampleApi)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.resources)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.logback)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.java)
            }
        }
    }
}

// Adding tka-client-plugin (following https://kotlinlang.org/docs/ksp-multiplatform.html)
dependencies {
    add("kspCommonMainMetadata", projects.tkaClientPlugin)
    add("kspJvm", projects.tkaClientPlugin)

    // Adding the sources generated in jvmMain to commonMain
    kotlin.sourceSets.commonMain {
        // This is where sources should be generated; we move them there manually, then IDE also recognizes them
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }

    // Move generated commonMain sources to correct directory
    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().all {
        doLast {
            val generatedSources = Paths.get("$buildDir/generated/ksp/jvm/jvmMain/kotlin")
            val targetDir = Paths.get("$buildDir/generated/ksp/metadata/commonMain/kotlin")

            if (generatedSources.exists()) {
                // If targetParentDir is not empty, get java.nio.file.DirectoryNotEmptyException
                @OptIn(ExperimentalPathApi::class)
                targetDir.deleteRecursively()
                targetDir.createDirectories()
                generatedSources.moveTo(targetDir, overwrite = true)
                logger.info("Moved generated sources from build/generated/ksp/jvm/jvmMain/kotlin to /generated/ksp/metadata/commonMain/kotlin")
            }
        }
    }
}

android {
    namespace = "felixwiemuth.tka.example"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}