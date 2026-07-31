---
name: unit-testing
description: Guidelines, patterns, and best practices for writing Kotlin unit tests for ViewModels, Repositories, and Utilities in the Flow Android project.
---

# Kotlin Unit Testing Guidelines & Best Practices for Flow

This skill outlines the standards and conventions for writing unit tests in the Flow codebase (`io.github.aedev.flow`).

## 1. Naming Conventions

### Test Class Naming
- Test class names MUST mirror the target class with a `Test` suffix.
  - Example: `LikedVideosViewModel` → `LikedVideosViewModelTest`
  - Example: `HistoryViewModel` → `HistoryViewModelTest`
  - Example: `SearchViewModel` → `SearchViewModelTest`

### Test Method Naming
- Use backtick-quoted descriptive phrases outlining the scenario and expected outcome:
  - Format: `` `[given condition or action] [expected result]` ``
  - Good: `` `initial state loads liked videos from repository` ``
  - Good: `` `removeLike calls repository and updates state` ``
  - Good: `` `search with query updates results flow` ``
  - Bad: `testRemoveLike()`, `test1()`

---

## 2. Mocking Framework & Cleanup Rules

- **Framework**: Use **MockK** (`io.mockk.*`).
- **Initialization**: Create mocks in `@Before` or as class properties via `mockk(relaxed = true)` or `mockk()`.
- **Teardown / Cleanup**: ALWAYS clean up mocks in an `@After` method using `unmockkAll()` or `clearAllMocks()` to ensure isolated test execution and prevent memory leaks/polluted mock states across tests.

```kotlin
@Before
fun setUp() {
    repository = mockk(relaxed = true)
}

@After
fun tearDown() {
    unmockkAll()
}
```

---

## 3. Coroutines & ViewModel Testing

ViewModel unit tests run on the JVM without an Android Main Looper. Since `viewModelScope` uses `Dispatchers.Main`, you MUST replace the Main dispatcher during tests:

- Use `Dispatchers.setMain(testDispatcher)` in `@Before` (or via a JUnit `TestRule`).
- Use `Dispatchers.resetMain()` in `@After`.
- Wrap suspend test logic inside `runTest` from `kotlinx.coroutines.test`.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }
}
```

---

## 4. Assertions & AAA Pattern

- **Assertion Library**: Use **Google Truth** (`com.google.common.truth.Truth.assertThat`).
- **AAA Pattern**: Structure tests clearly with **Arrange**, **Act**, and **Assert**:

```kotlin
@Test
fun `removeLike calls repository to remove item`() = runTest {
    // Arrange
    val videoId = "test_id_123"
    coEvery { repository.removeLikeState(videoId) } returns Unit

    // Act
    viewModel.removeLike(videoId)
    testScheduler.advanceUntilIdle()

    // Assert
    coVerify(exactly = 1) { repository.removeLikeState(videoId) }
}
```

---

## 5. Summary Checklist Before Shipping Tests
1. ✅ Test file resides under `app/src/test/java/...` matching package structure of source class.
2. ✅ Method names use backtick-quoted descriptive sentences.
3. ✅ MockK is used for dependencies with `unmockkAll()` in `@After`.
4. ✅ Main dispatcher rule/setup is present for ViewModel tests.
5. ✅ Google Truth (`assertThat`) is used for assertions.
6. ✅ Test passes cleanly via `./gradlew :app:testGithubDebugUnitTest`.
