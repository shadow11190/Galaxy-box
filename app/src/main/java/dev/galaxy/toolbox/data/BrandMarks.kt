package dev.galaxy.toolbox.data

import java.util.Locale

/** Transparent typographic brand marks. Exact aliases only; never infer a brand from a device code. */
object BrandMarks {
    private val names = """
        Xiaomi|xiaomi,mi
        Redmi|redmi
        POCO|poco
        Black Shark|black shark,blackshark
        OPPO|oppo,oplus
        OnePlus|oneplus,one plus
        realme|realme
        vivo|vivo
        iQOO|iqoo
        HUAWEI|huawei
        HONOR|honor
        ZTE|zte
        nubia|nubia
        REDMAGIC|redmagic,red magic
        Meizu|meizu
        Lenovo|lenovo
        ZUK|zuk
        Motorola|motorola,motorola mobility
        TCL|tcl
        Alcatel|alcatel
        WIKO|wiko
        Coolpad|coolpad
        Gionee|gionee
        Smartisan|smartisan
        LeEco|leeco,le tv
        Meitu|meitu
        8848|8848
        Hisense|hisense
        Haier|haier
        Konka|konka
        Amoi|amoi
        Elephone|elephone
        UMIDIGI|umidigi
        Ulefone|ulefone
        OUKITEL|oukitel
        DOOGEE|doogee
        Blackview|blackview
        CUBOT|cubot
        AGM|agm
        FOSSiBOT|fossibot
        HOTWAV|hotwav
        IIIF150|iiif150,f150
        Oscal|oscal
        8849|8849
        NIO Phone|nio phone,nio
        ASUS|asus,asustek,asustek computer inc.
        ROG Phone|rog phone,rog
        HTC|htc
        Acer|acer
        BenQ|benq
        GIGABYTE|gigabyte
        InFocus|infocus
        E-TEN|e-ten,eten
        SAMSUNG|samsung
        LG|lg,lge,lg electronics
        Pantech|pantech
        Sony|sony,sony mobile
        Sharp|sharp
        Kyocera|kyocera
        Fujitsu|fujitsu
        Panasonic|panasonic
        NEC|nec
        Toshiba|toshiba
        Casio|casio
        BALMUDA|balmuda
        Rakuten Mobile|rakuten mobile,rakuten
        Google Pixel|google pixel,google
        BLU|blu
        Essential|essential
        Razer Phone|razer phone,razer
        RED Hydrogen|red hydrogen,red digital cinema
        Nextbit|nextbit
        Amazon Fire Phone|amazon fire phone,amazon
        BlackBerry|blackberry
        Palm|palm
        CAT Phone|cat phone,cat phones,caterpillar
        Energizer|energizer
        Nothing|nothing
        CMF by Nothing|cmf by nothing,cmf
        Fairphone|fairphone
        HMD|hmd,hmd global,hmd global oy
        Nokia|nokia
        Gigaset|gigaset
        SHIFTphone|shiftphone,shift
        Volla|volla
        Bittium|bittium
        Doro|doro
        Crosscall|crosscall
        Archos|archos
        BQ|bq
        Allview|allview
        Wileyfox|wileyfox
        Vertu|vertu
        YotaPhone|yotaphone,yota devices
        Jolla|jolla
    """.trimIndent().lines().filter(String::isNotBlank).map { line ->
        val parts = line.split('|')
        parts[0] to parts[1].split(',').map(::normalize)
    }
    // Only brands with an Android 9+ handset and a checked transparent mark are shown.
    val supported: Set<String> = setOf(
        "Xiaomi", "Redmi", "POCO", "Black Shark", "OPPO", "OnePlus", "realme", "vivo", "iQOO",
        "HUAWEI", "HONOR", "ZTE", "nubia", "Meizu", "Lenovo", "Motorola",
        "ASUS", "ROG Phone", "HTC", "SAMSUNG", "LG", "Sony", "Sharp", "Kyocera", "Fujitsu",
        "Rakuten Mobile", "Google Pixel", "Razer Phone", "Nothing", "CMF by Nothing", "Fairphone", "Nokia"
    )
    private val aliases = names.flatMap { (mark, variants) -> variants.map { it to mark } }.toMap()

    fun resolve(manufacturer: String?, brand: String?, knownDeviceName: String?): String? {
        if (knownDeviceName.isNullOrBlank()) return null
        val fromBuild = listOfNotNull(brand, manufacturer).firstNotNullOfOrNull { aliases[normalize(it)] } ?: return null
        val marketing = normalize(knownDeviceName)
        val child = listOf("CMF by Nothing", "ROG Phone", "Redmi", "POCO", "Black Shark", "iQOO", "REDMAGIC", "nubia", "OnePlus")
            .firstOrNull { normalize(it).let { prefix -> marketing == prefix || marketing.startsWith("$prefix ") } }
        val chosen = when {
            child == "CMF by Nothing" && fromBuild == "Nothing" -> child
            child == "ROG Phone" && fromBuild == "ASUS" -> child
            child in listOf("Redmi", "POCO", "Black Shark") && fromBuild == "Xiaomi" -> child
            child == "iQOO" && fromBuild == "vivo" -> child
            child in listOf("REDMAGIC", "nubia") && fromBuild == "ZTE" -> child
            child == "OnePlus" && fromBuild == "OPPO" -> child
            fromBuild == "HMD" && marketing.startsWith("nokia ") -> "Nokia"
            else -> fromBuild
        }
        return chosen.takeIf { it in supported }
    }

    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
