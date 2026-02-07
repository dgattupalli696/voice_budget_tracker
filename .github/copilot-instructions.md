# Voice Budget Tracker - Android App

## Project Overview
A budget tracking Android application with voice input functionality built using Kotlin and Jetpack Compose.

## Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM
- **Database**: Room
- **Voice Input**: Android Speech Recognition API
- **Dependency Injection**: Hilt

## Project Structure
```
app/
├── src/main/
│   ├── java/com/budgettracker/
│   │   ├── data/          # Database, repositories
│   │   ├── di/            # Dependency injection modules
│   │   ├── domain/        # Models and use cases
│   │   ├── ui/            # Composables and ViewModels
│   │   └── utils/         # Voice recognition utilities
│   └── res/               # Resources
```

## Key Features
- Voice input for adding expenses/income
- Category-based expense tracking
- Budget summaries and charts
- Material Design 3 UI
