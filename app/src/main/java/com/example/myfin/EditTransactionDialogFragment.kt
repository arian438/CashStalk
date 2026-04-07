package com.example.myfin

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.example.myfin.data.*
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class EditTransactionDialogFragment : DialogFragment() {

    private lateinit var titleEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var descriptionEditText: EditText
    private lateinit var dateEditText: EditText
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var notificationRepository: NotificationRepository
    private var currentTransaction: Transaction? = null
    private var categoriesLoaded = false
    private var isProcessing = false
    private var isFragmentActive = true
    private val mainHandler = Handler(Looper.getMainLooper())

    interface OnTransactionUpdatedListener {
        fun onTransactionUpdated()
        fun onTransactionDeleted()
    }

    private var listener: OnTransactionUpdatedListener? = null

    companion object {
        private const val TAG = "EditTransactionDialog"
        private const val ARG_TRANSACTION_ID = "transaction_id"
        private const val ARG_TRANSACTION_TITLE = "transaction_title"
        private const val ARG_TRANSACTION_AMOUNT = "transaction_amount"
        private const val ARG_TRANSACTION_TYPE = "transaction_type"
        private const val ARG_TRANSACTION_CATEGORY = "transaction_category"
        private const val ARG_TRANSACTION_DESCRIPTION = "transaction_description"
        private const val ARG_TRANSACTION_DATE = "transaction_date"

        fun newInstance(transaction: Transaction): EditTransactionDialogFragment {
            val fragment = EditTransactionDialogFragment()
            val args = Bundle()
            args.putString(ARG_TRANSACTION_ID, transaction.id)
            args.putString(ARG_TRANSACTION_TITLE, transaction.title)
            args.putDouble(ARG_TRANSACTION_AMOUNT, transaction.amount)
            args.putString(ARG_TRANSACTION_TYPE, transaction.type)
            args.putString(ARG_TRANSACTION_CATEGORY, transaction.categoryName)
            args.putString(ARG_TRANSACTION_DESCRIPTION, transaction.description)
            args.putString(ARG_TRANSACTION_DATE, transaction.date)
            fragment.arguments = args
            return fragment
        }
    }

    fun setTransactionUpdatedListener(listener: OnTransactionUpdatedListener) {
        this.listener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments ?: return

        currentTransaction = Transaction(
            id = args.getString(ARG_TRANSACTION_ID) ?: "",
            title = args.getString(ARG_TRANSACTION_TITLE) ?: "",
            amount = args.getDouble(ARG_TRANSACTION_AMOUNT) ?: 0.0,
            type = args.getString(ARG_TRANSACTION_TYPE) ?: "expense",
            categoryName = args.getString(ARG_TRANSACTION_CATEGORY) ?: "",
            description = args.getString(ARG_TRANSACTION_DESCRIPTION) ?: "",
            date = args.getString(ARG_TRANSACTION_DATE) ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        try {
            notificationRepository = NotificationRepository(requireContext())
            categoryRepository = CategoryRepository(notificationRepository)
            transactionRepository = TransactionRepository(notificationRepository)

            val view = layoutInflater.inflate(R.layout.dialog_edit_transaction, null)

            titleEditText = view.findViewById(R.id.titleEditText)
            amountEditText = view.findViewById(R.id.amountEditText)
            categorySpinner = view.findViewById(R.id.categorySpinner)
            typeSpinner = view.findViewById(R.id.typeSpinner)
            descriptionEditText = view.findViewById(R.id.descriptionEditText)
            dateEditText = view.findViewById(R.id.dateEditText)

            populateTransactionData()
            setupDatePicker()
            setupTypeSpinner()

            val dialog = AlertDialog.Builder(requireContext())
                .setView(view)
                .setTitle(getString(R.string.edit_transaction_title))
                .setPositiveButton(getString(R.string.save)) { _, _ -> }
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setNeutralButton(getString(R.string.delete)) { _, _ ->
                    showDeleteConfirmationDialog()
                }
                .create()

            dialog.setOnShowListener {
                if (isFragmentActive && currentTransaction != null) {
                    loadCategories()
                    val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    positiveButton.setOnClickListener { updateTransaction() }
                    if (!categoriesLoaded) showLoadingState()
                }
            }

            return dialog
        } catch (e: Exception) {
            Log.e(TAG, "Error creating dialog", e)
            return AlertDialog.Builder(requireContext())
                .setTitle("Ошибка")
                .setMessage("Не удалось открыть диалог редактирования")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .create()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        listener = null
        mainHandler.removeCallbacksAndMessages(null)
        isProcessing = false
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        isProcessing = false
    }

    private fun showLoadingState() {
        try {
            val ctx = context ?: return
            if (!isFragmentActive) return

            val categories = listOf(getString(R.string.loading_categories))
            val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, categories)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            categorySpinner.adapter = adapter
            categorySpinner.isEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "Error showing loading state", e)
        }
    }

    private fun hideLoadingState() {
        try {
            if (isFragmentActive) categorySpinner.isEnabled = true
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding loading state", e)
        }
    }

    private fun populateTransactionData() {
        currentTransaction?.let { transaction ->
            titleEditText.setText(transaction.title)
            amountEditText.setText(transaction.amount.toString())
            descriptionEditText.setText(transaction.description)
            dateEditText.setText(transaction.date)
        }
    }

    private fun loadCategories() {
        if (!isFragmentActive) return
        categoriesLoaded = false

        try {
            categoryRepository.expenseCategories.observe(this, Observer {
                if (isAdded && isFragmentActive) updateCategorySpinner()
            })
            categoryRepository.incomeCategories.observe(this, Observer {
                if (isAdded && isFragmentActive) updateCategorySpinner()
            })
            categoryRepository.loadAllCategories()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories", e)
        }
    }

    private fun updateCategorySpinner() {
        try {
            if (!isAdded || !isFragmentActive || currentTransaction == null) return
            val ctx = context ?: return

            val types = resources.getStringArray(R.array.transaction_types)
            val expenseLabel = types.getOrNull(0) ?: getString(R.string.expense)
            val incomeLabel = types.getOrNull(1) ?: getString(R.string.income)

            val currentType = typeSpinner.selectedItem?.toString() ?:
            if (currentTransaction!!.type == "income") incomeLabel else expenseLabel
            val type = if (currentType == incomeLabel) "income" else "expense"

            val categories = if (type == "expense")
                categoryRepository.expenseCategories.value ?: emptyList()
            else
                categoryRepository.incomeCategories.value ?: emptyList()

            if (categories.isEmpty()) {
                categoriesLoaded = false
                return
            }

            categoriesLoaded = true
            hideLoadingState()

            val defaultCategories = categories.filter { it.isDefault }
            val userCategories = categories.filter { !it.isDefault }
            val sortedCategories = defaultCategories + userCategories
            val categoryNames = sortedCategories.map {
                if (it.isDefault) it.categoryName else "⭐ ${it.categoryName}"
            }

            val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, categoryNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            categorySpinner.adapter = adapter

            val currentCategoryName = currentTransaction!!.categoryName
            val categoryIndex = categoryNames.indexOfFirst {
                it == currentCategoryName || it == "⭐ $currentCategoryName"
            }
            categorySpinner.setSelection(if (categoryIndex >= 0) categoryIndex else 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up category spinner", e)
        }
    }

    private fun setupTypeSpinner() {
        try {
            if (!isFragmentActive || currentTransaction == null) return

            val types = resources.getStringArray(R.array.transaction_types)
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            typeSpinner.adapter = adapter

            typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isAdded && isFragmentActive) updateCategorySpinner()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            val expenseLabel = types.getOrNull(0) ?: getString(R.string.expense)
            val incomeLabel = types.getOrNull(1) ?: getString(R.string.income)
            val type = if (currentTransaction!!.type == "income") incomeLabel else expenseLabel
            val typeIndex = types.indexOfFirst { it == type }
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up type spinner", e)
        }
    }

    private fun setupDatePicker() {
        dateEditText.setOnClickListener {
            if (!isAdded || !isFragmentActive || currentTransaction == null) return@setOnClickListener

            val calendar = Calendar.getInstance()
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(currentTransaction!!.date) ?: Date()
                calendar.time = date
            } catch (e: Exception) {
                calendar.time = Date()
            }

            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    if (isAdded && isFragmentActive) {
                        val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                        dateEditText.setText(formattedDate)
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun updateTransaction() {
        if (!isFragmentActive || !isAdded || currentTransaction == null) return

        if (isProcessing) {
            Toast.makeText(requireContext(), "Сохранение уже выполняется", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val title = titleEditText.text.toString().trim()
            val amountText = amountEditText.text.toString().trim()
            val selectedCategoryName = categorySpinner.selectedItem?.toString() ?: ""
            val types = resources.getStringArray(R.array.transaction_types)
            val expenseLabel = types.getOrNull(0) ?: getString(R.string.expense)
            val incomeLabel = types.getOrNull(1) ?: getString(R.string.income)
            val typeLabel = typeSpinner.selectedItem?.toString() ?: expenseLabel
            val description = descriptionEditText.text.toString().trim()
            val date = dateEditText.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.enter_title), Toast.LENGTH_SHORT).show()
                return
            }
            if (amountText.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.enter_amount), Toast.LENGTH_SHORT).show()
                return
            }
            val cleanAmountText = amountText.replace(",", ".")
            val amount = cleanAmountText.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(requireContext(), getString(R.string.invalid_amount), Toast.LENGTH_SHORT).show()
                return
            }
            if (date.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.select_date), Toast.LENGTH_SHORT).show()
                return
            }

            val categoryName = selectedCategoryName.replace("⭐ ", "")
            val categories = if (typeLabel == incomeLabel)
                categoryRepository.incomeCategories.value ?: emptyList()
            else
                categoryRepository.expenseCategories.value ?: emptyList()

            val category = categories.find {
                val cleanName = if (it.isDefault) it.categoryName else "⭐ ${it.categoryName}"
                cleanName == selectedCategoryName || it.categoryName == categoryName
            }
            val categoryId = category?.id ?: ""

            isProcessing = true
            transactionRepository.updateTransaction(
                transactionId = currentTransaction!!.id,
                title = title,
                amount = amount,
                type = if (typeLabel == incomeLabel) "income" else "expense",
                categoryId = categoryId,
                categoryName = categoryName,
                description = description,
                date = date
            ) { success, message ->
                isProcessing = false
                mainHandler.post {
                    if (!isFragmentActive || !isAdded) return@post
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.transaction_updated), Toast.LENGTH_SHORT).show()
                        listener?.onTransactionUpdated()
                        dismissAllowingStateLoss()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.error, message ?: "Ошибка"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update error", e)
            isProcessing = false
            Toast.makeText(requireContext(), getString(R.string.unexpected_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog() {
        if (!isFragmentActive || !isAdded || currentTransaction == null) return

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_transaction))
            .setMessage(getString(R.string.delete_confirmation, currentTransaction!!.title))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                performDelete()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performDelete() {
        val transactionId = currentTransaction!!.id
        Log.d(TAG, "Начинаем удаление транзакции: $transactionId")

        transactionRepository.deleteTransaction(transactionId) { success, message ->
            if (success) {
                Log.d(TAG, "Транзакция успешно удалена")

                mainHandler.post {
                    try {
                        if (isFragmentActive && isAdded) {
                            Toast.makeText(requireContext(), "Транзакция удалена", Toast.LENGTH_SHORT).show()
                            listener?.onTransactionDeleted()
                            dismissAllowingStateLoss()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при закрытии диалога: ${e.message}")
                        try {
                            dismissAllowingStateLoss()
                        } catch (ex: Exception) {
                            Log.e(TAG, "Не удалось закрыть диалог", ex)
                        }
                    }
                }
            } else {
                Log.e(TAG, "Ошибка удаления: $message")
                mainHandler.post {
                    if (isFragmentActive && isAdded) {
                        Toast.makeText(requireContext(), "Ошибка: $message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}