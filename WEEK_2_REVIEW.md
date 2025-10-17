## Week 2 Review – Input Pipeline

### A. Touch to Path
- Compose UI captures gestures via `pointerInput { detectDragGestures(...) }`.
- `onDragStart/Move/End` call `SketchbookViewModel.handleMotionEvent(...)`.
- ViewModel builds both an Android `Path` (final list) and a Compose `Path` (active stroke) with quadratic smoothing.

### B. Path to Bitmap
- On "Capture", ViewModel emits a `captureRequests` event; UI renders the canvas offscreen using `ComposeView` and `drawToBitmap`.
- The captured bitmap is cropped to the drawing bounds computed from all paths and then sent back to the ViewModel.

### C. Preprocessing (Crop → Center/Scale → Color)
- Crop: Use `getDrawingBounds()` and `Bitmap.createBitmap` to trim whitespace.
- Center/Scale: Compute single scale `S = targetSize / max(w,h)`; draw scaled image centered onto a square `targetSize × targetSize` bitmap.
- Color: For Day 7, keep ARGB; grayscale/white-on-black can be enabled as needed.

### D. Bitmap to FloatBuffer
- Extract pixels; convert RGB → grayscale (if channels=1) using `0.299R + 0.587G + 0.114B`.
- Normalize to `[0, 1]` by dividing by `255f` and write into a reusable FloatBuffer (`rewind()` before/after use).

### E. Inference & Output
- Run `interpreter.run(inputBuffer, outputArray)` synchronously.
- Post-process with `processOutput()` to get top-1 label and confidence, expose via `classificationResult`.

## Model Swap Architecture (Week 3 Prep)
- `ModelConfig(modelFileName, inputWidth, inputHeight, inputChannels)` provided via Hilt.
- `SketchClassifier` depends on `ModelConfig` rather than hardcoded values; swapping models only updates Hilt provision.

## Test Flow Checklist
1) Draw → Press Capture → spinner shows.
2) UI displays (dummy) result and preview thumbnail.
3) Press Clear → drawing, result, and loading reset.
4) Memory profiler shows fewer spikes due to reusable bitmap and output array.


