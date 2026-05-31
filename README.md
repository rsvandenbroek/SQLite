# OMDb Zoeken

Een eenvoudige Android-app in Java waarmee je films en series kunt zoeken op trefwoord via de OMDb API.

## Project

- Java-only Android code.
- Gradle buildscript met Groovy DSL.
- Repositories voor plugins én app-dependencies staan in `settings.gradle`, zodat Android tooling zoals `aapt2` kan worden opgelost.
- Het app-thema gebruikt platform-styles en een lokale Material3-compatibiliteitsalias, zodat er geen externe Material Components dependency nodig is.
- Geen externe Android dependencies; de app gebruikt `HttpURLConnection` en `org.json`.
- De meegegeven OMDb API key staat in `BuildConfig.OMDB_API_KEY`.

## Bouwen

Open dit project in Android Studio of bouw via de terminal:

```bash
gradle :app:assembleDebug
```
