## Week 3 Review – Model Integration and Acceleration

### A. Real Model Swap (Day 16)
- We introduced `ModelConfig(modelFileName, inputWidth, inputHeight, inputChannels)` and validated the interpreter’s input tensor shape at runtime. The classifier throws early if the model shape (including batch=1) doesn’t match the config.
- Output shape handling supports both `[1, N]` and `[N]` by allocating/reusing the correct output array shape before `interpreter.run()`.

### B. Data Type Logic (Day 17)
- The input tensor `DataType` is read from `interpreter.getInputTensor(0).dataType()` and logged.
- The pipeline switches automatically: FLOAT32 → normalized `[0..1]` written via a reused FloatBuffer; UINT8/INT8 → raw `0..255` bytes written via a reused ByteBuffer (with matching polarity) before inference.

### C. Acceleration (Day 18)
- We attempt GPU Delegate creation (with compatibility check) and add it to `Interpreter.Options`. On failure or unsupported devices we fall back to CPU cleanly.
- GPU is a good first step for float models due to dense parallel math; we log inference time to benchmark improvements.

### D. Asynchronous Inference (Day 19)
- `onBitmapCaptured()` runs preprocessing and `interpreter.run()` on `Dispatchers.Default` to keep the main thread responsive while the spinner shows.
- UI state updates (bitmap preview and result) occur after background work completes, maintaining a fluid user experience.

### TFLite Model Maker (Concept)
- Value: Model Maker streamlines training or re-training task-specific TFLite models (classification, object detection, etc.) with minimal ML expertise, producing deployable `.tflite` assets.
- For custom symbols (star/heart/triangle), gather labeled images per class, use Model Maker’s image classification API to train, export the `.tflite` model, update `ModelConfig`, and reuse the existing preprocessing pipeline (size/channels) to deploy.

### Instrumentation Test Strategy
- Use an instrumentation test (runs on device) to access assets and native TFLite. Create a synthetic `28×28` bitmap, call preprocessing, then directly invoke `classifier.classify()`.
- Assertions: ensure the pipeline returns a `ClassificationResult` with a confidence in `[0.0, 1.0]`. Use `@Before`/`@After` to initialize and close the interpreter/delegate.


