# Voice Budget Tracker

An Android budget tracking application with voice input functionality built using Kotlin and Jetpack Compose.

## Features

- 🎤 **Voice Input**: Add transactions using natural voice commands
  - "Spent 50 dollars on food"
  - "Received 1000 dollars salary"
  - "Paid 30 for transport"
  
- 💰 **Income & Expense Tracking**: Track both income and expenses with categorization

- 📊 **Balance Overview**: See your total balance, income, and expenses at a glance

- 🏷️ **Categories**: Organize transactions into categories:
  - **Income**: Salary, Freelance, Investment, Other
  - **Expense**: Food, Transport, Shopping, Entertainment, Bills, Health, Education, Other

- 💾 **Local Storage**: All data stored locally using Room database

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room
- **Dependency Injection**: Hilt
- **Voice Recognition**: Android Speech Recognition API

## Project Structure

```
app/src/main/java/com/budgettracker/
├── BudgetTrackerApp.kt          # Application class
├── MainActivity.kt              # Main activity
├── data/
│   ├── local/                   # Room database, DAOs, converters
│   └── repository/              # Data repositories
├── di/                          # Hilt dependency injection modules
├── domain/
│   └── model/                   # Data models (Transaction, etc.)
├── ui/
│   ├── navigation/              # Navigation setup
│   ├── screens/
│   │   ├── home/               # Home screen with transaction list
│   │   └── addtransaction/     # Add transaction screen
│   └── theme/                   # Material theme configuration
└── utils/                       # Voice recognition utilities
```

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- An Android device or emulator running Android 8.0 (API 26) or higher

### Building the Project

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on an emulator or physical device

### Permissions

The app requires the following permissions:
- `RECORD_AUDIO`: For voice input functionality
- `INTERNET`: For speech recognition service

## Usage

1. **View Transactions**: The home screen displays your balance and recent transactions

2. **Add Transaction Manually**: 
   - Tap the **+** button
   - Select income or expense
   - Enter amount and description
   - Choose a category
   - Save

3. **Add Transaction by Voice**:
   - Tap the **microphone** button
   - Speak your transaction (e.g., "Spent 20 dollars on coffee")
   - The app will parse your voice input and pre-fill the form
   - Review and save

4. **Delete Transaction**: Tap the delete icon on any transaction to remove it

## Voice Commands Examples

- "Spent fifty dollars on groceries"
- "Paid twenty for uber"
- "Received two thousand dollars salary"
- "Got 500 from freelance work"
- "Bought coffee for 5 dollars"

## License

This project is open source and available under the MIT License.
