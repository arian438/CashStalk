package com.example.myfin.data

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class UserRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val currentUserLiveData = MutableLiveData<User?>()

    companion object {
        private const val TAG = "UserRepository"
    }

    fun getCurrentUserLiveData() = currentUserLiveData

    // Загрузить данные текущего пользователя
    fun loadCurrentUser() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.e(TAG, "User is not authenticated")
            currentUserLiveData.postValue(null)
            return
        }

        Log.d(TAG, "Loading user data for userId: $currentUserId")

        db.child("users").child(currentUserId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val user = snapshot.getValue(User::class.java)
                            if (user != null) {
                                // Обновляем ID пользователя
                                val updatedUser = user.copy(id = currentUserId)
                                currentUserLiveData.postValue(updatedUser)
                                Log.d(TAG, "✅ User data loaded: $updatedUser")
                            } else {
                                Log.w(TAG, "User data is null")
                                createDefaultUser(currentUserId)
                            }
                        } else {
                            Log.w(TAG, "User data not found in database")
                            createDefaultUser(currentUserId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error parsing user data: ${e.message}", e)
                        currentUserLiveData.postValue(null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "❌ Failed to load user data: ${error.message}")
                    currentUserLiveData.postValue(null)
                }
            })
    }

    // Создать пользователя по умолчанию
    private fun createDefaultUser(userId: String) {
        try {
            val authUser = auth.currentUser
            val defaultUser = User(
                id = userId,
                fio = authUser?.displayName ?: "Пользователь",
                email = authUser?.email ?: "",
                currency = "Российский рубль",
                currencySymbol = "₽",
                darkMode = false,
                language = "Русский",
                notifications = true
            )

            db.child("users").child(userId)
                .setValue(defaultUser)
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Default user created: $defaultUser")
                    currentUserLiveData.postValue(defaultUser)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to create default user: ${e.message}")
                    currentUserLiveData.postValue(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error creating default user: ${e.message}", e)
            currentUserLiveData.postValue(null)
        }
    }

    // Обновить настройки пользователя
    fun updateUser(user: User, onResult: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot update user: user not authenticated")
            onResult(false, "User not authenticated")
            return
        }

        Log.d(TAG, "Updating user settings: $user")

        // Создаем объект для обновления
        val updates = hashMapOf<String, Any?>(
            "currency" to user.currency,
            "currencySymbol" to user.currencySymbol,
            "darkMode" to user.darkMode,
            "language" to user.language,
            "notifications" to user.notifications
        )

        db.child("users").child(userId)
            .updateChildren(updates as Map<String, Any>)
            .addOnSuccessListener {
                Log.d(TAG, "✅ User settings updated successfully")
                currentUserLiveData.postValue(user)
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update user settings: ${e.message}")
                onResult(false, e.message)
            }
    }

    // Обновить отдельные поля пользователя
    fun updateUserField(field: String, value: Any, onResult: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "Cannot update user field: user not authenticated")
            onResult(false, "User not authenticated")
            return
        }

        db.child("users").child(userId).child(field)
            .setValue(value)
            .addOnSuccessListener {
                Log.d(TAG, "✅ User field '$field' updated to: $value")
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to update user field '$field': ${e.message}")
                onResult(false, e.message)
            }
    }
}