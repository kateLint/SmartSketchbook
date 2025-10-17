## Smart Sketchbook: Drawing → Classification Data Flow

```
+-----------------------+          +---------------------------+          +---------------------------+          +----------------------+
|     Composable UI     |          |        ViewModel          |          |     SketchClassifier      |          |     DrawingPath      |
| - Canvas & gestures   |  Start/  | - handleMotionEvent()     |  Bitmap  | - classify(bitmap):       |          | - points: List<Offset|
| - pointerInput(viewM) |  Move/   |   updates activePath      | -------> |   ByteBuffer packing ->   |          |                      |
| - draws paths +       |  End     | - activePath: StateFlow   |          |   interpreter.run(...)    |          |  Data consumed by UI |
|   activePath          | -------> | - onCaptureDrawing():     |          | -> FloatArray preds       |          |  and ViewModel       |
| - triggers classify   |          |   emit captureRequests    |          |                           |          |                      |
+-----------------------+          +---------------------------+          +---------------------------+          +----------------------+
         ^                                   |         ^                          |
         |                                   |         |                          |
         |       captureComposableToBitmap   |         |   onBitmapCaptured       |
         +-----------------------------------+---------+--------------------------+
```

### Hilt Scope Review
- ViewModel: `@HiltViewModel` `SketchbookViewModel` — lifecycle-scoped to the composable/navigation owner.
- Model: `@Singleton` `SketchClassifier` with `@ApplicationContext` — single interpreter instance, model loaded once.
- Assets: `.tflite` placed under `app/src/main/assets/`; Gradle configured with `noCompress ".tflite"` for mmap.

### Notes
- UI hoists events and observes `StateFlow`s; ViewModel coordinates capture and preprocessing; Classifier handles TFLite.
- `DrawingPath` is a lean data class for the in-progress stroke.


