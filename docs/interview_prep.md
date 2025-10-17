## Compose State (Core of UI)

- **What is State Hoisting in Compose, and why used in DrawingCanvas?**
  State hoisting moves state up to a single source of truth. We hoisted gesture updates to the ViewModel so UI reads from `StateFlow` and multiple composables can stay in sync.

- **mutableStateOf vs StateFlow; why StateFlow for ViewModel?**
  `mutableStateOf` is Compose-scoped and lifecycle-aware inside composition; `StateFlow` is Kotlin Flow-based, survives configuration changes, and is ideal for exposing observable state from ViewModels to UI.

## TFLite Architecture

- **Primary function of the ByteBuffer with TFLite?**
  It holds the model input tensor in the exact binary layout and type the interpreter expects (shape, order, dtype) for zero-copy/native execution.

- **Synchronous inference nature and performance note (Day 6)?**
  We used `interpreter.run(...)` synchronously, which blocks the calling thread. Running on the main thread risks jank; shift to background dispatchers/threads for production.
