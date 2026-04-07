package com.example.myfin.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.example.myfin.R
import com.example.myfin.data.Transaction
import com.example.myfin.data.User
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataExporter(private val context: Context) {

    companion object {
        private const val TAG = "DataExporter"

        // Поддерживаемые форматы
        const val FORMAT_CSV = "csv"
        const val FORMAT_EXCEL = "xlsx"
        const val FORMAT_TEXT = "txt"
    }

    /**
     * Экспорт транзакций в CSV файл
     */
    fun exportToCSV(
        transactions: List<Transaction>,
        user: User,
        onSuccess: (filePath: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        try {
            // Создаем содержимое CSV
            val csvContent = StringBuilder()

            // Заголовок с информацией о пользователе
            csvContent.appendLine("# ${context.getString(R.string.export_username, user.fio)}")
            csvContent.appendLine("# ${context.getString(R.string.email)}: ${user.email}")
            csvContent.appendLine("# ${context.getString(R.string.export_date)}: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}")
            csvContent.appendLine("# ${context.getString(R.string.export_transactions_count, transactions.size)}")
            csvContent.appendLine()

            // Заголовки колонок
            csvContent.appendLine(
                context.getString(R.string.transaction_date_label) + "," +
                        context.getString(R.string.transaction_title_label) + "," +
                        context.getString(R.string.transaction_category_label) + "," +
                        context.getString(R.string.transaction_type_label) + "," +
                        context.getString(R.string.transaction_amount_label) + "," +
                        context.getString(R.string.transaction_description_label)
            )

            // Данные
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            transactions.forEach { transaction ->
                val dateStr = dateFormat.format(Date(transaction.createdAt))
                val amount = transaction.amount
                val type = if (transaction.type == "income")
                    context.getString(R.string.income)
                else
                    context.getString(R.string.expense)
                val description = transaction.description?.replace(",", " ") ?: ""

                csvContent.append("$dateStr,")
                csvContent.append("\"${transaction.title}\",")
                csvContent.append("\"${transaction.categoryName}\",")
                csvContent.append("$type,")
                csvContent.append("$amount,")
                csvContent.append("\"$description\"\n")
            }

            // Итоговая информация
            appendSummary(csvContent, transactions, user)

            // Создаем имя файла
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "myfin_transactions_${timestamp}.csv"

            // Сохраняем файл
            saveFile(fileName, csvContent.toString(), "text/csv", onSuccess, onError)

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to CSV", e)
            onError(context.getString(R.string.export_error, e.message))
        }
    }

    /**
     * Экспорт транзакций в Excel файл
     */
    fun exportToExcel(
        transactions: List<Transaction>,
        user: User,
        onSuccess: (filePath: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        try {
            // Создаем содержимое Excel (табулированный CSV)
            val excelContent = StringBuilder()

            // Заголовок с информацией о пользователе
            excelContent.appendLine("# ${context.getString(R.string.export_username, user.fio)}")
            excelContent.appendLine("# ${context.getString(R.string.email)}: ${user.email}")
            excelContent.appendLine("# ${context.getString(R.string.export_date)}: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}")
            excelContent.appendLine("# ${context.getString(R.string.export_transactions_count, transactions.size)}")
            excelContent.appendLine()

            // Заголовки колонок
            excelContent.appendLine(
                context.getString(R.string.transaction_date_label) + "\t" +
                        context.getString(R.string.transaction_title_label) + "\t" +
                        context.getString(R.string.transaction_category_label) + "\t" +
                        context.getString(R.string.transaction_type_label) + "\t" +
                        context.getString(R.string.transaction_amount_label) + "\t" +
                        context.getString(R.string.transaction_description_label)
            )

            // Данные
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            transactions.forEach { transaction ->
                val dateStr = dateFormat.format(Date(transaction.createdAt))
                val amount = transaction.amount
                val type = if (transaction.type == "income")
                    context.getString(R.string.income)
                else
                    context.getString(R.string.expense)
                val description = transaction.description?.replace("\t", " ") ?: ""

                excelContent.append("$dateStr\t")
                excelContent.append("${transaction.title}\t")
                excelContent.append("${transaction.categoryName}\t")
                excelContent.append("$type\t")
                excelContent.append("$amount\t")
                excelContent.append("$description\n")
            }

            // Итоговая информация
            appendSummary(excelContent, transactions, user, "\t# ")

            // Создаем имя файла
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "myfin_transactions_${timestamp}.xlsx"

            // Сохраняем файл
            saveFile(fileName, excelContent.toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                onSuccess, onError)

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to Excel", e)
            onError(context.getString(R.string.export_error, e.message))
        }
    }

    /**
     * Экспорт сводного отчета
     */
    fun exportSummaryReport(
        transactions: List<Transaction>,
        user: User,
        onSuccess: (filePath: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        try {
            val reportContent = StringBuilder()

            // Заголовок отчета
            reportContent.append("=".repeat(50))
            reportContent.append("\n")
            reportContent.append("${context.getString(R.string.my_finances_export)}\n")
            reportContent.append("=".repeat(50))
            reportContent.append("\n\n")

            // Информация о пользователе
            reportContent.append("${context.getString(R.string.export_username, user.fio)}\n")
            reportContent.append("${context.getString(R.string.email)}: ${user.email}\n")
            reportContent.append("${context.getString(R.string.currency)}: ${user.currency} (${user.currencySymbol})\n")
            reportContent.append("\n")

            // Сводная информация
            val income = transactions.filter { it.type == "income" }.sumOf { it.amount }
            val expense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
            val balance = income - expense

            reportContent.append("${context.getString(R.string.export_summary)}:\n")
            reportContent.append("-".repeat(30))
            reportContent.append("\n")
            reportContent.append("${context.getString(R.string.total_income)}: ${user.currencySymbol}${String.format("%.2f", income)}\n")
            reportContent.append("${context.getString(R.string.total_expense)}: ${user.currencySymbol}${String.format("%.2f", expense)}\n")
            reportContent.append("${context.getString(R.string.net_balance)}: ${user.currencySymbol}${String.format("%.2f", balance)}\n")
            reportContent.append("\n")

            // Статистика по категориям
            val categoryStats = transactions.groupBy { it.categoryName }
                .mapValues { (_, trans) ->
                    trans.groupBy { it.type }
                        .mapValues { (_, typeTrans) -> typeTrans.sumOf { it.amount } }
                }

            if (categoryStats.isNotEmpty()) {
                reportContent.append("${context.getString(R.string.stats_income_expense_by_month)}:\n")
                reportContent.append("-".repeat(30))
                reportContent.append("\n")

                categoryStats.forEach { (category, stats) ->
                    val catIncome = stats["income"] ?: 0.0
                    val catExpense = stats["expense"] ?: 0.0
                    val catBalance = catIncome - catExpense

                    reportContent.append("$category:\n")
                    reportContent.append("  ${context.getString(R.string.income)}: ${user.currencySymbol}${String.format("%.2f", catIncome)}\n")
                    reportContent.append("  ${context.getString(R.string.expense)}: ${user.currencySymbol}${String.format("%.2f", catExpense)}\n")
                    reportContent.append("  ${context.getString(R.string.net_balance)}: ${user.currencySymbol}${String.format("%.2f", catBalance)}\n")
                }
                reportContent.append("\n")
            }

            // Детали по транзакциям
            if (transactions.isNotEmpty()) {
                reportContent.append("${context.getString(R.string.all_transactions)} (${transactions.size}):\n")
                reportContent.append("-".repeat(30))
                reportContent.append("\n")

                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                transactions.sortedByDescending { it.createdAt }.forEachIndexed { index, transaction ->
                    val dateStr = dateFormat.format(Date(transaction.createdAt))
                    val type = if (transaction.type == "income")
                        context.getString(R.string.income)
                    else
                        context.getString(R.string.expense)

                    reportContent.append("${index + 1}. $dateStr\n")
                    reportContent.append("   ${context.getString(R.string.transaction_title_label)}: ${transaction.title}\n")
                    reportContent.append("   ${context.getString(R.string.transaction_category_label)}: ${transaction.categoryName}\n")
                    reportContent.append("   ${context.getString(R.string.transaction_type_label)}: $type\n")
                    reportContent.append("   ${context.getString(R.string.transaction_amount_label)}: ${user.currencySymbol}${String.format("%.2f", transaction.amount)}\n")
                    if (transaction.description.isNotEmpty()) {
                        reportContent.append("   ${context.getString(R.string.transaction_description_label)}: ${transaction.description}\n")
                    }
                    reportContent.append("\n")
                }
            } else {
                reportContent.append("${context.getString(R.string.no_transactions)}\n")
            }

            // Создаем имя файла
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "myfin_report_${timestamp}.txt"

            // Сохраняем файл
            saveFile(fileName, reportContent.toString(), "text/plain", onSuccess, onError)

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting report", e)
            onError(context.getString(R.string.export_error, e.message))
        }
    }

    /**
     * Добавление итоговой информации
     */
    private fun appendSummary(
        sb: StringBuilder,
        transactions: List<Transaction>,
        user: User,
        prefix: String = "# "
    ) {
        if (transactions.isNotEmpty()) {
            val income = transactions.filter { it.type == "income" }.sumOf { it.amount }
            val expense = transactions.filter { it.type == "expense" }.sumOf { it.amount }
            val balance = income - expense

            sb.appendLine()
            sb.appendLine("${prefix}${context.getString(R.string.export_summary)}:")
            sb.appendLine("${prefix}${context.getString(R.string.total_income)}: ${formatAmount(income)} ${user.currencySymbol}")
            sb.appendLine("${prefix}${context.getString(R.string.total_expense)}: ${formatAmount(expense)} ${user.currencySymbol}")
            sb.appendLine("${prefix}${context.getString(R.string.net_balance)}: ${formatAmount(balance)} ${user.currencySymbol}")
        }
    }

    /**
     * Форматирование суммы
     */
    private fun formatAmount(amount: Double): String {
        return String.format(Locale.getDefault(), "%.2f", amount)
    }

    /**
     * Общий метод сохранения файла
     */
    private fun saveFile(
        fileName: String,
        content: String,
        mimeType: String,
        onSuccess: (filePath: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Для Android 10+ (API 29+) используем MediaStore
                saveFileUsingMediaStore(fileName, content, mimeType, onSuccess, onError)
            } else {
                // Для более старых версий используем File API
                saveFileUsingLegacyAPI(fileName, content, mimeType, onSuccess, onError)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error saving file", e)
            onError(context.getString(R.string.export_error, e.message))
        }
    }

    /**
     * Сохранение файла для Android 10+ (API 29+)
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileUsingMediaStore(
        fileName: String,
        content: String,
        mimeType: String,
        onSuccess: (filePath: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()

                val filePath = uri.toString()
                Log.d(TAG, "File saved successfully using MediaStore: $filePath")
                onSuccess(filePath)
            } ?: run {
                onError(context.getString(R.string.storage_permission_required))
            }
        } else {
            onError(context.getString(R.string.storage_permission_required))
        }
    }

    /**
     * Сохранение файла для версий ниже Android 10 (до API 28)
     */
    @Suppress("DEPRECATION")
    private fun saveFileUsingLegacyAPI(
        fileName: String,
        content: String,
        mimeType: String,
        onSuccess: (filePath: String) -> Unit,
        onError: (error: String) -> Unit
    ) {
        try {
            // Проверяем доступность внешнего хранилища
            val state = Environment.getExternalStorageState()
            if (state != Environment.MEDIA_MOUNTED) {
                onError(context.getString(R.string.storage_permission_required))
                return
            }

            // Получаем путь к папке Downloads
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            // Создаем папку, если она не существует
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Создаем файл
            val file = File(downloadsDir, fileName)

            // Записываем данные в файл
            FileOutputStream(file).use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }

            // Обновляем MediaStore для отображения в галерее/файловых менеджерах
            MediaStoreScanner.scanFile(context, file)

            val filePath = file.absolutePath
            Log.d(TAG, "File saved successfully using legacy API: $filePath")
            onSuccess(filePath)

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied", e)
            onError(context.getString(R.string.storage_permission_required))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file", e)
            onError(context.getString(R.string.export_error, e.message))
        }
    }

    /**
     * Диалог выбора формата экспорта с переводом
     */
    fun showExportFormatDialog(
        transactions: List<Transaction>,
        user: User,
        onExportComplete: (filePath: String, format: String) -> Unit
    ) {
        val formats = arrayOf(
            context.getString(R.string.export_format_csv),
            context.getString(R.string.export_format_excel),
            context.getString(R.string.export_format_text)
        )
        val formatKeys = arrayOf(FORMAT_CSV, FORMAT_EXCEL, FORMAT_TEXT)

        if (transactions.isEmpty()) {
            showNoDataDialog()
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.export_data_title))
            .setItems(formats) { _, which ->
                when (formatKeys[which]) {
                    FORMAT_CSV -> {
                        exportToCSV(transactions, user,
                            onSuccess = { filePath ->
                                onExportComplete(filePath, FORMAT_CSV)
                            },
                            onError = { error ->
                                showErrorDialog(error)
                            }
                        )
                    }
                    FORMAT_EXCEL -> {
                        exportToExcel(transactions, user,
                            onSuccess = { filePath ->
                                onExportComplete(filePath, FORMAT_EXCEL)
                            },
                            onError = { error ->
                                showErrorDialog(error)
                            }
                        )
                    }
                    FORMAT_TEXT -> {
                        exportSummaryReport(transactions, user,
                            onSuccess = { filePath ->
                                onExportComplete(filePath, FORMAT_TEXT)
                            },
                            onError = { error ->
                                showErrorDialog(error)
                            }
                        )
                    }
                }
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    /**
     * Показ диалога с ошибкой
     */
    private fun showErrorDialog(error: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.error))
            .setMessage(error)
            .setPositiveButton(context.getString(R.string.ok), null)
            .show()
    }

    /**
     * Показ диалога, если нет данных для экспорта
     */
    private fun showNoDataDialog() {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.no_data_title))
            .setMessage(context.getString(R.string.no_data_message))
            .setPositiveButton(context.getString(R.string.ok), null)
            .show()
    }

    /**
     * Получение пути к файлу для отображения пользователю
     */
    fun getReadableFilePath(uriString: String): String {
        return try {
            if (uriString.startsWith("content://")) {
                // Это URI MediaStore
                val uri = uriString.toUri()
                val fileName = uri.lastPathSegment ?: "myfin_export"
                context.getString(R.string.export_file_location, fileName)
            } else {
                // Это путь файловой системы
                val file = File(uriString)
                context.getString(R.string.export_file_location, file.name)
            }
        } catch (e: Exception) {
            context.getString(R.string.export_file_location, "Downloads")
        }
    }

    /**
     * Показ диалога успешного экспорта
     */
    fun showExportSuccessDialog(
        filePath: String,
        format: String,
        onShareClick: () -> Unit,
        onOpenClick: () -> Unit
    ) {
        val formatName = when (format) {
            FORMAT_CSV -> context.getString(R.string.export_format_csv)
            FORMAT_EXCEL -> context.getString(R.string.export_format_excel)
            FORMAT_TEXT -> context.getString(R.string.export_format_text)
            else -> format
        }

        val readablePath = getReadableFilePath(filePath)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.export_success_title))
            .setMessage(context.getString(R.string.export_success_message, formatName, readablePath))
            .setPositiveButton(context.getString(R.string.share)) { _, _ ->
                onShareClick()
            }
            .setNeutralButton(context.getString(R.string.open_file)) { _, _ ->
                onOpenClick()
            }
            .setNegativeButton(context.getString(R.string.ok), null)
            .show()
    }
}

/**
 * Вспомогательный класс для обновления MediaStore
 */
object MediaStoreScanner {

    @Suppress("DEPRECATION")
    fun scanFile(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Для API 19+ используем новый способ
            val mediaScanIntent = android.content.Intent(
                android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE
            )
            val contentUri = android.net.Uri.fromFile(file)
            mediaScanIntent.data = contentUri
            context.sendBroadcast(mediaScanIntent)
        } else {
            // Для старых версий
            context.sendBroadcast(
                android.content.Intent(
                    android.content.Intent.ACTION_MEDIA_MOUNTED,
                    android.net.Uri.parse("file://" + Environment.getExternalStorageDirectory())
                )
            )
        }
    }
}