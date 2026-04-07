package com.example.myfin

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.example.myfin.data.CategoryRepository
import com.example.myfin.data.NotificationRepository
import com.example.myfin.data.TransactionRepository
import com.google.firebase.auth.FirebaseAuth

class AddTransactionDialogFragment : DialogFragment() {

    private lateinit var titleEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var descriptionEditText: EditText
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var notificationRepository: NotificationRepository
    private var categoriesLoaded = false

    interface OnTransactionAddedListener {
        fun onTransactionAdded()
    }

    private var listener: OnTransactionAddedListener? = null

    companion object {
        private const val TAG = "AddTransactionDialog"
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        listener = when {
            parentFragment is OnTransactionAddedListener -> parentFragment as OnTransactionAddedListener
            context is OnTransactionAddedListener -> context as OnTransactionAddedListener
            else -> null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        notificationRepository = NotificationRepository(requireContext())
        categoryRepository = CategoryRepository(notificationRepository)
        transactionRepository = TransactionRepository(notificationRepository)

        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_add_transaction, null)

        titleEditText = view.findViewById(R.id.titleEditText)
        amountEditText = view.findViewById(R.id.amountEditText)
        categorySpinner = view.findViewById(R.id.categorySpinner)
        typeSpinner = view.findViewById(R.id.typeSpinner)
        descriptionEditText = view.findViewById(R.id.descriptionEditText)

        setupTypeSpinner()
        loadCategories()

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle(getString(R.string.add_transaction_title))
            .setPositiveButton(getString(R.string.add_button)) { _, _ -> }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                addTransactionToDatabase()
            }
            if (!categoriesLoaded) {
                showLoadingState()
            }
        }

        return dialog
    }

    private fun showLoadingState() {
        val categories = listOf(getString(R.string.loading_categories))
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        categorySpinner.isEnabled = false
    }

    private fun hideLoadingState() {
        categorySpinner.isEnabled = true
    }

    private fun loadCategories() {
        categoriesLoaded = false

        categoryRepository.expenseCategories.observe(this, Observer {
            updateAllCategories()
        })

        categoryRepository.incomeCategories.observe(this, Observer {
            updateAllCategories()
        })

        categoryRepository.loadAllCategories()
    }

    private fun updateAllCategories() {
        val expenseCategories = categoryRepository.expenseCategories.value ?: emptyList()
        val incomeCategories = categoryRepository.incomeCategories.value ?: emptyList()

        categoriesLoaded = expenseCategories.isNotEmpty() || incomeCategories.isNotEmpty()

        if (categoriesLoaded) {
            hideLoadingState()
            setupCategorySpinner()
        }
    }

    private fun setupCategorySpinner() {
        try {
            val types = resources.getStringArray(R.array.transaction_types)
            val expenseLabel = types.getOrNull(0) ?: getString(R.string.expense)
            val incomeLabel = types.getOrNull(1) ?: getString(R.string.income)

            val currentType = typeSpinner.selectedItem?.toString() ?: expenseLabel
            val type = if (currentType == incomeLabel) "income" else "expense"

            val categories = if (type == "expense") {
                categoryRepository.expenseCategories.value ?: emptyList()
            } else {
                categoryRepository.incomeCategories.value ?: emptyList()
            }

            if (categories.isEmpty()) {
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    listOf(getString(R.string.no_categories_short))
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = adapter
                categorySpinner.isEnabled = false
                return
            }

            val defaultCategories = categories.filter { it.isDefault }
            val userCategories = categories.filter { !it.isDefault }
            val sortedCategories = defaultCategories + userCategories

            val categoryNames = sortedCategories.map {
                if (it.isDefault) it.categoryName else "⭐ ${it.categoryName}"
            }

            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                categoryNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            categorySpinner.adapter = adapter
            categorySpinner.isEnabled = true

            if (sortedCategories.isNotEmpty()) {
                categorySpinner.setSelection(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up category spinner", e)
        }
    }

    private fun setupTypeSpinner() {
        try {
            val types = resources.getStringArray(R.array.transaction_types)
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                types
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            typeSpinner.adapter = adapter

            typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    setupCategorySpinner()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            typeSpinner.setSelection(0)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up type spinner", e)
        }
    }

    private fun addTransactionToDatabase() {
        try {
            val auth = FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid

            if (userId == null) {
                Toast.makeText(requireContext(), getString(R.string.auth_error_short), Toast.LENGTH_SHORT).show()
                return
            }

            val title = titleEditText.text.toString().trim()
            val amountText = amountEditText.text.toString().trim()
            val selectedCategoryName = categorySpinner.selectedItem?.toString() ?: ""

            if (selectedCategoryName == getString(R.string.no_categories_short) ||
                selectedCategoryName == getString(R.string.loading_categories)) {
                Toast.makeText(requireContext(), getString(R.string.please_select_category), Toast.LENGTH_SHORT).show()
                return
            }

            val categoryName = selectedCategoryName.replace("⭐ ", "")

            val types = resources.getStringArray(R.array.transaction_types)
            val expenseLabel = types.getOrNull(0) ?: getString(R.string.expense)
            val incomeLabel = types.getOrNull(1) ?: getString(R.string.income)
            val currentType = typeSpinner.selectedItem?.toString() ?: expenseLabel
            val type = if (currentType == incomeLabel) "income" else "expense"
            val description = descriptionEditText.text.toString().trim()

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

            val categories = if (type == "expense") {
                categoryRepository.expenseCategories.value ?: emptyList()
            } else {
                categoryRepository.incomeCategories.value ?: emptyList()
            }

            val category = categories.find {
                val cleanName = if (it.isDefault) it.categoryName else "⭐ ${it.categoryName}"
                cleanName == selectedCategoryName || it.categoryName == categoryName
            }

            val categoryId = category?.id ?: ""
            val finalCategoryName = category?.categoryName ?: categoryName

            transactionRepository.addTransaction(
                title = title,
                amount = amount,
                type = type,
                categoryId = categoryId,
                categoryName = finalCategoryName,
                description = description
            ) { success, message ->
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.transaction_added), Toast.LENGTH_SHORT).show()
                    listener?.onTransactionAdded()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.error, message ?: "Ошибка"), Toast.LENGTH_LONG).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Toast.makeText(requireContext(), getString(R.string.unexpected_error), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
        categoryRepository.cleanup()
    }
}