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

package felixwiemuth.tka.client.plugin

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import felixwiemuth.tka.api.ApiResponse
import felixwiemuth.tka.api.GET
import felixwiemuth.tka.api.POST
import felixwiemuth.tka.client.annotation.WithApis
import io.ktor.resources.Resource

/**
 * Generates code for the [WithApis] annotation.
 */
class ClientApisProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    private val resourceAnnotation: String = Resource::class.qualifiedName.orEmpty()
    private val withApisAnnotation: String = WithApis::class.qualifiedName.orEmpty()
    private val withApisAnnotationSimpleName: String = WithApis::class.simpleName.orEmpty()
    private val GetInterface = GET::class.qualifiedName!!
    private val PostInterface = POST::class.qualifiedName!!

    private fun String.nbs() = replace(' ', '·')

    private fun log(msg: String) {
        logger.info("ClientApisProcessor: $msg")
    }

    private fun KSClassDeclaration.hasResourceAnnotation(): Boolean =
        annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == resourceAnnotation }

    private fun KSPropertyDeclaration.filterWithApisAnnotations(): Sequence<KSAnnotation> =
        annotations.filter { it.annotationType.resolve().declaration.qualifiedName?.asString() == withApisAnnotation }

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        log("Starting processing")
        val annotatedProps =
            resolver.getSymbolsWithAnnotation(withApisAnnotation)
                .filterIsInstance<KSPropertyDeclaration>()

        log("Properties annotated with $withApisAnnotationSimpleName: ${annotatedProps.map { it.toString() }.toList()}")

        // TODO To allow multiple clients per package, could add an optional "ClientName" parameter to the annotation which would become part of the generated name
        // TODO Instead of throwing exceptions, consider logging error and continuing, but this requires extra structure
        annotatedProps.forEach { clientVal ->
            val packageName = clientVal.packageName.asString()
            log("Processing property ${clientVal.qualifiedName} with following annotations: ${clientVal.annotations.toList()}")
            clientVal.filterWithApisAnnotations()
                .forEach {
                    log("Processing $withApisAnnotationSimpleName annotation $it with arguments ${it.arguments}")
                    val apisList = it.arguments.first().value as Collection<*> // the only argument is an array of class definitions; here we get an ArrayList
                    apisList.forEach { rootResourceClassType -> // expect this to be the root resource class, for which generate ApiClient object with the nested APIs
                        rootResourceClassType as KSType
                        // Walk through the hierarchy, create corresponding hierarchy of objects and for each interface, add the implementation
                        val rootResourceClassQualifiedName = rootResourceClassType.declaration.qualifiedName
                            ?: throw IllegalArgumentException("Class without qualified name provided to ${WithApis::class.qualifiedName.orEmpty()} annotation.")
                        log("Processing root class $rootResourceClassQualifiedName")
                        // To be able to get child declarations etc., we find the actual declarations of the provided class references
                        val rootClassDecl = resolver.getClassDeclarationByName(rootResourceClassQualifiedName)
                            ?: throw IllegalArgumentException("Cannot find class $rootResourceClassQualifiedName provided in annotation.")
                        if (!rootClassDecl.hasResourceAnnotation()) throw IllegalArgumentException("Class $rootResourceClassQualifiedName does not have the $resourceAnnotation annotation")
                        genApiClientFile(rootClassDecl, packageName)?.writeTo(codeGenerator, Dependencies(true))
                    }
                }

        }
        return emptyList()
    }

    private fun genApiClientFile(rootClassDecl: KSClassDeclaration, packageName: String): FileSpec? {
        logger.info("ClientApisProcessor: processing ${rootClassDecl.qualifiedName}")
        val apiBaseName = rootClassDecl.simpleName.getShortName()

        val fileBuilder = FileSpec.builder(
            packageName,
            fileName = "${apiBaseName}Client"
        )  // Note: API root resource classes must have a unique name per client (they are added to the same package)

        // Find interfaces in all nested resource classes
        fun traverseResourceClasses(clz: KSClassDeclaration, builder: TypeSpec.Builder): TypeSpec {
            log("Checking interfaces of class ${clz.toClassName()}")
            val interfaces = clz.declarations
                .filterIsInstance<KSClassDeclaration>()
                .also { log("Class declarations: ${it.toList()}") }
                .filter { it.classKind == ClassKind.INTERFACE }
                .also { log("Interfaces: ${it.toList()}") }
                .filter { it.superTypes.count() == 1 }
                .also { log("Interfaces with exactly one supertype: ${it.toList()}") }
                .forEach { interf ->
                    // The only supertype should be the Get/Post interface from the library
                    val extendedInterface = interf.superTypes.first().resolve()
                    val extendedInterfaceDecl = extendedInterface.declaration
                    if (extendedInterfaceDecl !is KSClassDeclaration || extendedInterfaceDecl.classKind != ClassKind.INTERFACE) return@forEach

                    log("Processing interface ${interf.toClassName()}")

                    builder.addSuperinterface(interf.asType(emptyList()).toTypeName())

                    fun getTypeArg(idx: Int): TypeName? =
                        // Note: Probably can only identify type arguments by index, not by parameter name
                        extendedInterface.arguments.getOrElse(idx) {
                            logger.error(
                                "Implemented interface does not have type parameter no. ${idx + 1}",
                                interf // TODO it does not show symbol in compiler output
                            ); null
                        }?.toTypeName()

                    when (extendedInterfaceDecl.qualifiedName?.asString().orEmpty()) {
                        GetInterface -> {
                            val resTy = getTypeArg(1) ?: return builder.build()
                            val errTy = getTypeArg(2) ?: return builder.build()
                            val retTy = ApiResponse::class.asTypeName().parameterizedBy(resTy, errTy)
                            with(FunSpec.builder("get")) {
                                receiver(clz.toClassName())
                                modifiers.addAll(listOf(KModifier.OVERRIDE, KModifier.SUSPEND))
                                addCode("return get(this)".nbs())
                                returns(retTy)
                                builder.addFunction(build())
                            }
                        }
                        PostInterface -> {
                            val paramTy = getTypeArg(1) ?: return builder.build()
                            val resTy = getTypeArg(2) ?: return builder.build()
                            val errTy = getTypeArg(3) ?: return builder.build()
                            val retTy = ApiResponse::class.asTypeName().parameterizedBy(resTy, errTy)
                            with(FunSpec.builder("post")) {
                                receiver(clz.toClassName())
                                modifiers.addAll(listOf(KModifier.OVERRIDE, KModifier.SUSPEND))
                                addParameter("param", paramTy)
                                addCode("return post(this, param)".nbs())
                                returns(retTy)
                                builder.addFunction(build())
                            }
                        }
                        else -> {} // No interface to implement - skip
                    }
                }

            val nestedResourceClasses = clz.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.hasResourceAnnotation() }
            nestedResourceClasses.forEach {
                val objBuilder = TypeSpec.objectBuilder(it.simpleName.getShortName())
                // Note: cannot remove public modifier with the following (while adding modifiers works)
                // objBuilder.modifiers.remove(KModifier.PUBLIC)
                val obj = traverseResourceClasses(it, objBuilder)
                builder.typeSpecs.add(obj)
            }
            val ty = builder.build()
            logger.info("Built type ${ty.name}")
            return ty
        }

        val rootObj = traverseResourceClasses(rootClassDecl, TypeSpec.objectBuilder("${apiBaseName}Client"))
        fileBuilder.addType(rootObj)
        return fileBuilder.build()
    }
}

