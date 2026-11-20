package com.example.yemenport.domain.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AiDocumentExtraction(
    val fullNameArabic: String = "",
    val fullNameLatin: String = "",
    val passportNumber: String = "",
    val nationality: String = "اليمن",
    val birthDate: String = "",
    val expiryDate: String = "",
    val documentType: String = "جواز سفر",
    val suggestedModule: String = "سجل المسافرين والتأشيرات",
    val summaryArabic: String = ""
)

class SovereignAiRepository {

    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    private val structuredSchema = Schema.obj(
        mapOf(
            "fullNameArabic" to Schema.string("Full Arabic name if visible or transliterated"),
            "fullNameLatin" to Schema.string("Full Latin name as written in passport/document"),
            "passportNumber" to Schema.string("Passport or document ID number"),
            "nationality" to Schema.string("Nationality in Arabic e.g. اليمن"),
            "birthDate" to Schema.string("Birth date YYYY-MM-DD if present"),
            "expiryDate" to Schema.string("Expiry date YYYY-MM-DD if present"),
            "documentType" to Schema.string("Document type in Arabic"),
            "suggestedModule" to Schema.string("Suggested border module in Arabic"),
            "summaryArabic" to Schema.string("Concise official Arabic summary for port officer")
        ),
        optionalProperties = listOf("birthDate", "expiryDate", "fullNameArabic")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = structuredSchema
            }
        )
    }

    suspend fun analyzeDocumentImage(bitmap: Bitmap): Result<AiDocumentExtraction> {
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "أنت محلل وثائق وجوازات سفر سيادي. استخرج بيانات الوثيقة بدقة (الاسم العربي واللاتيني، رقم الجواز/الهوية، الجنسية، تاريخ الميلاد، تاريخ الانتهاء، نوع الوثيقة، والوحدة المناسبة وملخص عربي موجز)."
                        )
                    }
                )
            }
            val raw = response.text ?: error("لم يرد نص تحليلي من Gemini")
            Result.success(jsonParser.decodeFromString<AiDocumentExtraction>(raw))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini vision timeout", e)
            Result.failure(Exception("انتهت مهلة التحليل السحابي عبر Gemini (60 ثانية). يمكنك الاعتماد على المسح المحلي أو المحاولة مجدداً."))
        } catch (e: Exception) {
            Log.e(TAG, "Gemini vision error", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeDocumentText(rawText: String): Result<AiDocumentExtraction> {
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(
                            "حلل النص التالي المستخرج من مستند منفذ حدودي أو مذكرة أو مانيفست واستخرج الحقول المطلوبة بالعربية:\n\n${rawText.take(6000)}"
                        )
                    }
                )
            }
            val raw = response.text ?: error("لم يرد نص تحليلي من Gemini")
            Result.success(jsonParser.decodeFromString<AiDocumentExtraction>(raw))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini text timeout", e)
            Result.failure(Exception("انتهت مهلة تحليل النص عبر Gemini (60 ثانية)."))
        } catch (e: Exception) {
            Log.e(TAG, "Gemini text error", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "SovereignAiRepo"
    }
}
