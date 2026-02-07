# AI Model for Voice Budget Tracker

The AI model is **downloaded on-demand** from Settings, not shipped with the APK.

## How It Works

1. Go to **Settings > AI Text Correction**
2. Tap **Download** to download the Gemma 2B model (~1.3 GB)
3. The model will be saved to internal storage
4. Voice input will now use AI for smarter corrections

## Model Details

| Model | Gemma 2B IT |
|-------|-------------|
| Size | ~1.3 GB |
| RAM Required | 4GB+ |
| Source | Google MediaPipe |
| Format | .task (MediaPipe LLM) |

## Features with AI Model

- **Better spelling correction**: Fixes voice transcription errors
- **Smart descriptions**: Generates concise expense descriptions
- **Works offline**: No internet needed after download
- **On-device**: All processing is local for privacy

## Without AI Model

The app still works! It uses rule-based corrections:
- Common misspellings (fod → food, luch → lunch)
- Number words (fifty → 50, two hundred → 200)
- Indian numbers (lakh → 100000, crore → 10000000)

## Storage Location

The model is stored at:
```
/data/data/com.budgettracker/files/models/qwen.task
```

## Deleting the Model

Go to **Settings > AI Text Correction > Delete Model** to free up storage.
