package com.gassense.viewmodels

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.Exception

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockkStatic(FirebaseAuth::class)
        mockkStatic(FirebaseFirestore::class)
        
        auth = mockk(relaxed = true)
        firestore = mockk(relaxed = true)
        
        every { FirebaseAuth.getInstance() } returns auth
        every { FirebaseFirestore.getInstance() } returns firestore
        
        viewModel = AuthViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `login success sets Authenticated state`() = runTest {
        val email = "test@example.com"
        val password = "password"
        val task = mockk<Task<AuthResult>>()
        
        every { auth.signInWithEmailAndPassword(email, password) } returns task
        every { task.isComplete } returns true
        every { task.exception } returns null
        every { task.isCanceled } returns false
        every { task.isSuccessful } returns true
        every { task.result } returns mockk()

        viewModel.login(email, password)
        advanceUntilIdle()

        assertEquals(AuthState.Authenticated, viewModel.authState.value)
    }

    @Test
    fun `login failure sets Error state`() = runTest {
        val email = "test@example.com"
        val password = "wrong"
        val task = mockk<Task<AuthResult>>()
        
        every { auth.signInWithEmailAndPassword(email, password) } returns task
        every { task.isComplete } returns true
        every { task.exception } returns Exception("Invalid credentials")
        every { task.isCanceled } returns false
        every { task.isSuccessful } returns false

        viewModel.login(email, password)
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.Error)
        assertEquals("Invalid credentials", (viewModel.authState.value as AuthState.Error).message)
    }

    @Test
    fun `registration rollback on firestore failure`() = runTest {
        val email = "test@example.com"
        val password = "password"
        val deviceId = "DEV123"
        
        // 1. Mock Device ID check (Empty result)
        val collection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val queryTask = mockk<Task<QuerySnapshot>>()
        val querySnapshot = mockk<QuerySnapshot>()
        
        every { firestore.collection("users") } returns collection
        every { collection.whereEqualTo("deviceId", deviceId) } returns query
        every { query.get() } returns queryTask
        every { queryTask.isComplete } returns true
        every { queryTask.exception } returns null
        every { queryTask.isCanceled } returns false
        every { queryTask.isSuccessful } returns true
        every { queryTask.result } returns querySnapshot
        every { querySnapshot.isEmpty } returns true

        // 2. Mock Auth Creation (Success)
        val authResultTask = mockk<Task<AuthResult>>()
        val authResult = mockk<AuthResult>()
        val user = mockk<FirebaseUser>()
        
        every { auth.createUserWithEmailAndPassword(email, password) } returns authResultTask
        every { authResultTask.isComplete } returns true
        every { authResultTask.exception } returns null
        every { authResultTask.isCanceled } returns false
        every { authResultTask.isSuccessful } returns true
        every { authResultTask.result } returns authResult
        every { authResult.user } returns user
        every { user.uid } returns "uid123"

        // 3. Mock Firestore Profile Write (Failure)
        val docRef = mockk<DocumentReference>()
        val setTask = mockk<Task<Void>>()
        
        every { collection.document("uid123") } returns docRef
        every { docRef.set(any()) } returns setTask
        every { setTask.isComplete } returns true
        every { setTask.exception } returns Exception("Firestore error")
        every { setTask.isCanceled } returns false
        every { setTask.isSuccessful } returns false
        every { setTask.result } throws Exception("Firestore error")

        // 4. Mock Auth Rollback (Success)
        val deleteTask = mockk<Task<Void>>()
        every { user.delete() } returns deleteTask
        every { deleteTask.isComplete } returns true
        every { deleteTask.exception } returns null
        every { deleteTask.isCanceled } returns false
        every { deleteTask.isSuccessful } returns true
        every { deleteTask.result } returns mockk()

        viewModel.register("Name", email, "123", deviceId, password)
        advanceUntilIdle()

        // Verify rollback was called
        verify { user.delete() }
        assertTrue(viewModel.authState.value is AuthState.Error)
        assertEquals("Registration failed. Please try again.", (viewModel.authState.value as AuthState.Error).message)
    }
}
