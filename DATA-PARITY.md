# Windows-Android data parity

Windows-repon C#-toteutus on nykyisen laskenta- ja SQLite-sopimuksen lähde. Golden-fixturet ajetaan sekä C#- että Kotlin-testeissä.

Numeerisia eroja ei hyväksytä seuraavissa: domain/origin/home URL, periodin inklusiiviset päivämäärät, KPI:t, trendit, prosentit, listajärjestys ja deterministic Insights. Mobiilin esitystapa saa poiketa, mutta data ei.

Ensimmäiset portatut invariantit:

- `Period.All` = 365 päivää.
- Nollasta alkava aktiivisuus = `TrendState.NewActivity`.
- `PerformanceUrl` delegoi `SiteIdentity`lle.
- GA4 `engagedSessions` on count ja `engagementRate` ratio.
