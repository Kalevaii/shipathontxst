# Feature ViewModels

Place feature-specific Kotlin ViewModels in `com.rommie.app.viewmodel` as screens are built.
No unused base ViewModel or feature implementation is needed for the foundation.

- Each feature owner owns their ViewModel and UI state; avoid one shared app-wide ViewModel.
- Inject `RoomieRepository` through the constructor. Use `MockRoomieRepository` for development.
- Expose UI state and event methods; screens receive state and callbacks rather than querying storage.
- Handle loading, missing households, and failures when integrating `loadHousehold`.
- Reuse `model` definitions and `domain` calculations. Keep Firebase SDK calls in `data`.
- Coordinate new repository operations with the backend owner before adding write workflows.
