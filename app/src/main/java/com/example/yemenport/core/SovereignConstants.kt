package com.example.yemenport.core

data class PortSpec(
    val name: String,
    val type: String, // جوي | بري | بحري | مركز رئيسي
    val governorate: String,
    val code: String
)

data class SlaServiceSpec(
    val serviceName: String,
    val category: String,
    val targetDays: Int
)

enum class OfficerRole(
    val arabicTitle: String,
    val canManageUsers: Boolean,
    val canApproveBan: Boolean,
    val canViewAuditLogs: Boolean,
    val canDeleteRecords: Boolean,
    val canEditOrImport: Boolean
) {
    DIRECTOR_GENERAL("مدير عام", true, true, true, true, true),
    DEPUTY_DIRECTOR("نائب مدير عام", true, true, true, true, true),
    DEPARTMENT_MANAGER("مدير إدارة", true, true, true, true, true),
    SECTION_HEAD("رئيس قسم", false, true, true, true, true),
    SENIOR_INVESTIGATOR("محقق أول", false, true, true, false, true),
    FIELD_OFFICER("ضابط منفذ", false, false, false, false, true),
    DATA_ENTRY("موظف إدخال بيانات", false, false, false, false, true);

    companion object {
        fun fromTitle(title: String): OfficerRole {
            return entries.firstOrNull { it.arabicTitle == title.trim() } ?: FIELD_OFFICER
        }
    }
}

object SovereignConstants {

    val YEMEN_PORTS = listOf(
        PortSpec("المركز الرئيسي - العاصمة صنعاء", "مركز رئيسي", "صنعاء", "SAH-HQ"),
        PortSpec("مطار صنعاء الدولي", "جوي", "صنعاء", "SAH"),
        PortSpec("مطار عدن الدولي", "جوي", "عدن", "ADE"),
        PortSpec("مطار الريان الدولي", "جوي", "حضرموت", "RIY"),
        PortSpec("مطار سيئون الدولي", "جوي", "حضرموت", "GXF"),
        PortSpec("مطار الغيضة الدولي", "جوي", "المهرة", "AAY"),
        PortSpec("مطار سقطرى الدولي", "جوي", "سقطرى", "SCT"),
        PortSpec("مطار الحديدة الدولي", "جوي", "الحديدة", "HOD"),
        PortSpec("منفذ الوديعة البري", "بري", "حضرموت", "WAD"),
        PortSpec("منفذ شحن البري", "بري", "المهرة", "SHN"),
        PortSpec("منفذ صرفيت البري", "بري", "المهرة", "SRF"),
        PortSpec("منفذ علب البري", "بري", "صعدة", "ALB"),
        PortSpec("منفذ الطوال البري", "بري", "حجة", "TWL"),
        PortSpec("ميناء عدن البحري", "بحري", "عدن", "ADEP"),
        PortSpec("ميناء الحديدة البحري", "بحري", "الحديدة", "HODP"),
        PortSpec("ميناء المكلا البحري", "بحري", "حضرموت", "MKLP"),
        PortSpec("ميناء نشطون البحري", "بحري", "المهرة", "NSHP"),
        PortSpec("ميناء الصليف البحري", "بحري", "الحديدة", "SLFP"),
        PortSpec("ميناء المخا البحري", "بحري", "تعز", "MKHP")
    )

    val PORT_NAMES = YEMEN_PORTS.map { it.name }

    val SLA_SERVICES = listOf(
        SlaServiceSpec("إصدار جواز سفر جديد / تجديد", "خدمات الجوازات", 3),
        SlaServiceSpec("فحص ومباينة جواز مشتبه", "الأمن والمباينة", 2),
        SlaServiceSpec("تأشيرة دخول دبلوماسية / منظمات", "التنسيق والخارجية", 2),
        SlaServiceSpec("تصريح تنقل وفد دولي", "التنسيق الميداني", 1),
        SlaServiceSpec("تسليم جواز مضبوط لصاحبه", "الضبط والمحاضر", 5),
        SlaServiceSpec("إقامة مستثمر أو عامل أجنبي", "شؤون الوافدين", 7),
        SlaServiceSpec("تجديد بطاقة لاجئ UNHCR", "شؤون اللاجئين", 10),
        SlaServiceSpec("طلب تجنيس أو إثبات جنسية", "الجنسية", 30),
        SlaServiceSpec("توثيق زواج يمني من أجنبية", "الجنسية والزواج", 14),
        SlaServiceSpec("جوازات أبناء اليمنيات", "الجنسية", 10),
        SlaServiceSpec("تخليص معاملة شركة سياحية", "الوكالات", 4),
        SlaServiceSpec("استقبال كشف رحلة إنسانية", "الطيران الإنساني", 1),
        SlaServiceSpec("إحالة بلاغ أمني للتحقيق", "البلاغات", 1),
        SlaServiceSpec("فحص بصمة ومطابقة بيومترية", "الهوية البيومترية", 1),
        SlaServiceSpec("إغلاق محضر مخالفة إقامة", "الضبط الميداني", 5)
    )

    val ICAO_COUNTRY_TO_ARABIC = mapOf(
        "YEM" to "اليمن",
        "SAU" to "السعودية",
        "ARE" to "الإمارات",
        "OMN" to "عمان",
        "KWT" to "الكويت",
        "QAT" to "قطر",
        "BHR" to "البحرين",
        "EGY" to "مصر",
        "JOR" to "الأردن",
        "SYR" to "سوريا",
        "IRQ" to "العراق",
        "SDN" to "السودان",
        "ETH" to "إثيوبيا",
        "SOM" to "الصومال",
        "DJI" to "جيبوتي",
        "IND" to "الهند",
        "PAK" to "باكستان",
        "USA" to "أمريكا",
        "GBR" to "بريطانيا",
        "FRA" to "فرنسا",
        "DEU" to "ألمانيا",
        "TUR" to "تركيا",
        "CHN" to "الصين",
        "MYS" to "ماليزيا",
        "IDN" to "إندونيسيا"
    )
}
