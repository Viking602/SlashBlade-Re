from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
assert ROOT == Path(r'D:\MC26\.minecraft\versions\project02\.work\SlashBlade_2')
FILES = {
    'build.gradle': r'''plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.148'
    id 'idea'
}
version = mod_version
group = mod_group_id
base { archivesName = 'SlashBlade-26.1.2' }
java.toolchain.languageVersion = JavaLanguageVersion.of(25)
repositories { maven { url = 'https://maven.kosmx.dev/' } }
dependencies {
    // Compile-only while the original optional animation integration is migrated.
    compileOnly 'dev.kosmx.player-anim:player-animation-lib-forge:1.0.2-rc1+1.20'
    testImplementation platform('org.junit:junit-bom:5.13.4')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
neoForge {
    version = neo_version
    runs {
        client { client(); gameDirectory = file('../..') }
        server { server(); gameDirectory = file('../test-server'); programArgument '--nogui' }
        gameTestServer { type = 'gameTestServer'; gameDirectory = file('../test-gametest'); systemProperty 'neoforge.enabledGameTestNamespaces', mod_id }
        configureEach { logLevel = org.slf4j.event.Level.INFO }
    }
    mods { slashblade { sourceSet sourceSets.main } }
}
sourceSets.main.resources {
    srcDir 'src/generated/resources'
    exclude 'META-INF/mods.toml', 'META-INF/accesstransformer.cfg'
}
def generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    inputs.properties([mod_id:mod_id, mod_name:mod_name, mod_version:mod_version, mod_license:mod_license, neo_version:neo_version])
    expand inputs.properties
    from 'src/main/templates'
    into 'build/generated/sources/modMetadata'
}
sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata
tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8'; options.compilerArgs += ['-Xmaxerrs', '1000'] }
tasks.named('test', Test) { useJUnitPlatform() }
tasks.named('jar', Jar) { manifest.attributes('Implementation-Version':project.version) }
''',
    'gradle.properties': '''org.gradle.jvmargs=-Xmx4G -Dfile.encoding=UTF-8
org.gradle.daemon=false
org.gradle.caching=true
org.gradle.configuration-cache=false
minecraft_version=26.1.2
neo_version=26.1.2.114
mod_id=slashblade
mod_name=Slash Blade
mod_version=0.1.2-26.1.2-port.1
mod_group_id=mods.flammpfeil.slashblade
mod_license=NyMmd-MIT:nyatla, ObjModelImporter:forge, All other Rights reserved.
''',
}
for name, text in FILES.items():
    (ROOT / name).write_text(text, encoding='utf-8')
print('NeoForge build files initialized')
