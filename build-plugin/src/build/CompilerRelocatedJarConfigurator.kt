package build

import build.util.ContentModificationContext
import build.util.ContentTransformer
import build.util.relocatePackages
import build.util.transformPluginXmlContent
import com.github.jengelman.gradle.plugins.shadow.transformers.ComponentsXmlResourceTransformer
import org.jetbrains.gradle.shadow.ShadowJarAction

val CompilerRelocatedJarConfigurator: ShadowJarAction = {
    mergeServiceFiles()
    transform(ComponentsXmlResourceTransformer())

    transform(
        ContentTransformer(
            "META-INF/extensions/compiler.xml",
            ContentModificationContext::transformPluginXmlContent,
        ),
    )
    manifest {
        attributes["Implementation-Version"] = project.version
    }

    exclude("module-info.class")
    exclude("**/module-info.class")

    // See KTNB-707
    exclude("logback.xml")

    relocatePackages {
        +"kotlin.script.experimental.dependencies"
        +"org.jetbrains.kotlin."
        +"org.jetbrains.kotlinx.serialization."
    }

    // http://docs.gradle.org/current/dsl/org.gradle.api.tasks.bundling.Zip.html#org.gradle.api.tasks.bundling.Zip:zip64
    isZip64 = true
}
