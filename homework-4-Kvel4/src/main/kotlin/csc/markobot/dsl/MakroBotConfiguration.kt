@file:Suppress("FunctionName", "NonAsciiCharacters")

package csc.markobot.dsl

import csc.markobot.api.*

@MakroBotDsl
class MakroBotConfiguration {
    private var _head: Head? = null
    val head: Head
        get() = _head.checkInitialization("Head wasn't properly initialized")!!

    private var _body: Body? = null
    val body: Body
        get() = _body.checkInitialization("Body wasn't properly initialized")!!

    private var _hands: Hands? = null
    val hands: Hands
        get() = _hands.checkInitialization("Hands wasn't properly initialized")!!

    private var _chassis: Chassis? = null
    var шасси: Chassis
        get() = _chassis.checkInitialization("Chassis wasn't properly initialized")!!
        set(value) { _chassis = value }

    val гусеницы
        get() = CaterpillarConfig()
    val ноги
        get() = Chassis.Legs

    @MakroBotDsl
    abstract class MaterialPart {
        private var _material: Material? = null
        val material: Material
            get() = _material.checkInitialization("Material wasn't properly initialized")!!

        val металл
            get() = MetalConfig()
        val пластик
            get() = PlasticConfig()

        infix fun MaterialConfig.толщиной(thickness: Int) {
            this.thickness = thickness

            _material = this.build()
        }

        abstract class MaterialConfig {
            var thickness = 0

            abstract fun build(): Material
        }

        class PlasticConfig : MaterialConfig() {
            override fun build() = Plastik(thickness)
        }

        class MetalConfig : MaterialConfig() {
            override fun build() = Metal(thickness)
        }
    }

    class HeadConfiguration : MaterialPart() {
        private var _eyes: List<Eye>? = null
        val eyes: List<Eye>
            get() = _eyes.checkInitialization("Eyes wasn't properly initialized")!!

        private var _mouth: Mouth? = null
        val mouth: Mouth
            get() = _mouth.checkInitialization("Mouth wasn't properly initialized")!!

        @MakroBotDsl
        class EyesConfiguration {
            val eyes = mutableListOf<Eye>()

            @MakroBotDsl
            class EyeConfiguration {
                var количество = 1
                var яркость = 0
            }

            fun лампы(operations: EyeConfiguration.() -> Unit) =
                with(EyeConfiguration().apply(operations)) {
                    repeat(количество) { this@EyesConfiguration.eyes += LampEye(яркость) }
                }

            fun диоды(operations: EyeConfiguration.() -> Unit) =
                with(EyeConfiguration().apply(operations)) {
                    repeat(количество) { this@EyesConfiguration.eyes += LedEye(яркость) }
                }
        }

        @MakroBotDsl
        class MouthConfiguration {
            var speaker: Speaker? = null

            @MakroBotDsl
            class SpeakerConfiguration {
                var мощность = 0
            }

            fun динамик(operations: SpeakerConfiguration.() -> Unit) =
                with(SpeakerConfiguration().apply(operations)) {
                    this@MouthConfiguration.speaker = Speaker(мощность)
                }
        }

        fun глаза(operations: EyesConfiguration.() -> Unit) =
            with(EyesConfiguration().apply(operations)) { this@HeadConfiguration._eyes = eyes }

        fun рот(operations: MouthConfiguration.() -> Unit) =
            with(MouthConfiguration().apply(operations)) {
                this@HeadConfiguration._mouth = Mouth(speaker)
            }
    }

    class BodyConfiguration : MaterialPart() {
        val strings = mutableListOf<String>()

        @MakroBotDsl
        inner class Inscription {
            operator fun String.unaryPlus() {
                this@BodyConfiguration.strings += (this)
            }
        }

        fun надпись(operations: Inscription.() -> Unit) = Inscription().operations()
    }

    class HandsConfiguration : MaterialPart() {
        private var _нагрузка: load? = null
        var нагрузка: load
            get() = _нагрузка.checkInitialization("Load wasn't properly initialized")!!
            set(value) { _нагрузка = value }

        val очень_легкая
            get() = LoadClass.VeryLight
        val легкая
            get() = LoadClass.Light
        val средняя
            get() = LoadClass.Medium
        val тяжелая
            get() = LoadClass.Heavy
        val очень_тяжелая
            get() = LoadClass.VeryHeavy
        val неадекватная
            get() = LoadClass.Enormous

        operator fun LoadClass.minus(that: LoadClass) = Pair(this, that)
    }

    @MakroBotDsl class CaterpillarConfig

    @MakroBotDsl
    class WheelsConfiguration {
        var диаметр = 0
        var количество = 1
    }

    infix fun CaterpillarConfig.шириной(width: Int) = Chassis.Caterpillar(width)

    fun голова(operations: HeadConfiguration.() -> Unit) =
        with(HeadConfiguration().apply(operations)) {
            this@MakroBotConfiguration._head = Head(material, eyes, mouth)
        }

    fun туловище(operations: BodyConfiguration.() -> Unit) =
        with(BodyConfiguration().apply(operations)) {
            this@MakroBotConfiguration._body = Body(material, strings)
        }

    fun руки(operations: HandsConfiguration.() -> Unit) =
        with(HandsConfiguration().apply(operations)) {
            this@MakroBotConfiguration._hands = Hands(material, нагрузка.first, нагрузка.second)
        }

    fun колеса(operations: WheelsConfiguration.() -> Unit) =
        with(WheelsConfiguration().apply(operations)) { Chassis.Wheel(количество, диаметр) }
}

internal fun <T> T.checkInitialization(message: String): T {
    if (this == null) throw InitializationException(message)

    return this
}

fun робот(name: String, operations: MakroBotConfiguration.() -> Unit) =
    with(MakroBotConfiguration().apply(operations)) { MakroBot(name, head, body, hands, шасси) }

class InitializationException(message: String) : Exception(message)

typealias load = Pair<LoadClass, LoadClass>
