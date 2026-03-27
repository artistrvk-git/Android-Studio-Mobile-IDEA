package com.rvk.studio.engine.sdk

data class SdkComponent(
    val name: String,
    val url: String,
    val targetDir: String,
    val description: String,
    val required: Boolean = true
)

object SdkConfig {

    val COMPONENTS = listOf(
        SdkComponent(
            name = "rvk-sdk-tools",
            url = "https://www.dropbox.com/scl/fi/3ysun0jhiu9frbq8bklnh/rvk-sdk-tools.zip?rlkey=t8mj9vksh2m64g45y2yq490vg&st=065kbp3t&dl=1",
            targetDir = "sdk-tools",
            description = "Android SDK Build Tools & Platform Tools"
        ),
        SdkComponent(
            name = "rvk-jdk17",
            url = "https://www.dropbox.com/scl/fi/jga2lx146hnel1ya2oxym/rvk-jdk17.zip?rlkey=9vtahoq4l6vyl9c461czq0lp8&st=vboenjhb&dl=1",
            targetDir = "jdk17",
            description = "Java Development Kit 17"
        ),
        SdkComponent(
            name = "rvk-jdk21",
            url = "https://www.dropbox.com/scl/fi/e9qrujvyjyvdo7lawy3oa/rvk-jdk21.zip?rlkey=v6s9r1ia72l4i0ovfgiunpt6z&st=qranog7g&dl=1",
            targetDir = "jdk21",
            description = "Java Development Kit 21"
        ),
        SdkComponent(
            name = "rvk-ndk",
            url = "https://www.dropbox.com/scl/fi/7iw11jdx4nhein5t7jmne/rvk-ndk.zip?rlkey=8pkfi60270j7lysv6l2sbbbs7&st=c8qsgtpd&dl=1",
            targetDir = "ndk",
            description = "Android NDK (Native Development Kit)"
        ),
        SdkComponent(
            name = "rvk-gradle-8.14.4",
            url = "https://www.dropbox.com/scl/fi/eh6u3dv0vyfyvyrd11n1f/rvk-gradle-8.14.4.zip?rlkey=cszyti4f307qy0o0h7xe9u0et&st=zc5eluoc&dl=1",
            targetDir = "gradle-8.14.4",
            description = "Gradle Build System 8.14.4"
        ),
        SdkComponent(
            name = "rvk-gradle-9.3.0",
            url = "https://www.dropbox.com/scl/fi/s1sqyzqtteums31yz1es5/rvk-gradle-9.3.0.zip?rlkey=ivgqg6b2kexgl3ylenckdv145&st=9pabtglh&dl=1",
            targetDir = "gradle-9.3.0",
            description = "Gradle Build System 9.3.0"
        ),
        SdkComponent(
            name = "rvk-gradle-9.4.0",
            url = "https://www.dropbox.com/scl/fi/73xs9relzom0ktxsblga7/rvk-gradle-9.4.0.zip?rlkey=b3uvsceldc5692d0xebee9gt7&st=eklvpj3e&dl=1",
            targetDir = "gradle-9.4.0",
            description = "Gradle Build System 9.4.0"
        ),
        SdkComponent(
            name = "rvk-flutter",
            url = "https://www.dropbox.com/scl/fi/qfrzvql7w5jh27rh53dd9/rvk-flutter.zip?rlkey=r94cm1muuq4l485vzccxjmfjk&st=k3bdmzg4&dl=1",
            targetDir = "flutter",
            description = "Flutter SDK",
            required = false
        ),
        SdkComponent(
            name = "rvk-nodejs",
            url = "https://www.dropbox.com/scl/fi/zk9gheaqkxj3gcy1urcvr/rvk-nodejs.zip?rlkey=vrm2gu8xdksamcnvha7xhvtly&st=0iqfkysw&dl=1",
            targetDir = "nodejs",
            description = "Node.js Runtime",
            required = false
        ),
        SdkComponent(
            name = "rvk-python",
            url = "https://www.dropbox.com/scl/fi/jgvlipu2pgf9zt84j1ton/rvk-python.zip?rlkey=t3tbzk5uyn8216c57yqat5974&st=5xfh6rv8&dl=1",
            targetDir = "python",
            description = "Python Interpreter",
            required = false
        )
    )

    const val APP_LOGO_URL = "https://i.supaimg.com/44dd479f-14e2-45df-bb57-c406454c4851.png"

    fun getComponentByName(name: String): SdkComponent? {
        return COMPONENTS.find { it.name == name }
    }

    fun getRequiredComponents(): List<SdkComponent> {
        return COMPONENTS.filter { it.required }
    }

    fun getAllComponents(): List<SdkComponent> {
        return COMPONENTS
    }
}
